package com.personal.tracker.service.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.MarketBar;
import com.personal.tracker.repository.JdbcSupport;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class YahooMarketBarProvider implements MarketBarProvider {
  private static final Logger log = LoggerFactory.getLogger(YahooMarketBarProvider.class);
  private static final Map<String, String> CRYPTO = Map.of("BTC", "BTC-USD", "ETH", "ETH-USD", "SOL", "SOL-USD");
  private final ObjectMapper mapper;
  private final RestClient client;

  public YahooMarketBarProvider(ObjectMapper mapper) {
    this.mapper = mapper;
    client = RestClient.builder()
        .baseUrl("https://query1.finance.yahoo.com")
        .defaultHeader("User-Agent", "Mozilla/5.0")
        .requestFactory(requestFactory())
        .build();
  }

  @Override
  public String name() {
    return "yahoo";
  }

  @Override
  public List<MarketBar> fetch(Instrument instrument, String timeframe) {
    return fetch(instrument, timeframe, null, null, 1000);
  }

  @Override
  public List<MarketBar> fetch(Instrument instrument, String timeframe, Long startTime, Long endTime, int limit) {
    String frame = interval(timeframe);
    String symbol = yahooSymbol(instrument);
    if (frame == null || symbol.isBlank()) return List.of();
    try {
      JsonNode result = request(uri(symbol, frame, startTime, endTime, limit))
          .path("chart").path("result").path(0);
      return parse(instrument, timeframe, result, Math.max(1, limit));
    } catch (RuntimeException error) {
      log.debug("Yahoo K line fetch failed for {}", instrument.symbol(), error);
      return List.of();
    }
  }

  private static SimpleClientHttpRequestFactory requestFactory() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(8));
    factory.setReadTimeout(Duration.ofSeconds(15));
    return factory;
  }

  private static String uri(String symbol, String interval, Long startTime, Long endTime, int limit) {
    UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/v8/finance/chart/{symbol}")
        .queryParam("interval", interval).queryParam("includePrePost", false);
    if (startTime != null || endTime != null) {
      builder.queryParam("period1", startTime == null ? 0 : startTime / 1000);
      builder.queryParam("period2", endTime == null ? Instant.now().getEpochSecond() : endTime / 1000);
    } else {
      builder.queryParam("range", "1h".equals(interval) ? "730d" : "5y");
    }
    return builder.buildAndExpand(symbol).toUriString();
  }

  private JsonNode request(String uri) {
    try {
      JsonNode body = client.get().uri(uri).retrieve().body(JsonNode.class);
      return body == null ? mapper.nullNode() : body;
    } catch (RuntimeException error) {
      log.debug("Yahoo Java client failed, trying PowerShell fallback: {}", error.getMessage());
      return requestWithPowerShell("https://query1.finance.yahoo.com" + uri);
    }
  }

  private JsonNode requestWithPowerShell(String url) {
    Path output = null;
    try {
      output = Files.createTempFile("yahoo-bars-", ".json");
      Process process = new ProcessBuilder(
          "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command(url))
          .redirectErrorStream(true)
          .redirectOutput(output.toFile())
          .start();
      CompletableFuture<Integer> exitCode = CompletableFuture.supplyAsync(() -> waitFor(process));
      try {
        if (exitCode.get(20, TimeUnit.SECONDS) != 0) return mapper.nullNode();
        return mapper.readTree(Files.readAllBytes(output));
      } finally {
        if (process.isAlive()) process.destroyForcibly();
      }
    } catch (TimeoutException error) {
      log.debug("Yahoo PowerShell fallback timed out: {}", url);
      return mapper.nullNode();
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      return mapper.nullNode();
    } catch (Exception error) {
      log.debug("Yahoo PowerShell fallback failed: {}", error.getMessage());
      return mapper.nullNode();
    } finally {
      if (output != null) {
        try {
          Files.deleteIfExists(output);
        } catch (IOException error) {
          log.debug("Yahoo temp file cleanup failed: {}", error.getMessage());
        }
      }
    }
  }

  private static String command(String url) {
    return """
        $ProgressPreference = 'SilentlyContinue';
        [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new();
        $headers = @{ 'User-Agent' = 'Mozilla/5.0' };
        (Invoke-WebRequest -Uri '%s' -Headers $headers -TimeoutSec 15 -UseBasicParsing).Content;
        """.formatted(url.replace("'", "''"));
  }

  private static int waitFor(Process process) {
    try {
      return process.waitFor();
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      return -1;
    }
  }

  private static String interval(String timeframe) {
    if ("1D".equalsIgnoreCase(timeframe)) return "1d";
    if ("1H".equalsIgnoreCase(timeframe)) return "1h";
    return null;
  }

  private static String yahooSymbol(Instrument instrument) {
    String symbol = MarketBarSupport.cleanSymbol(instrument.symbol()).toUpperCase(Locale.ROOT);
    if (symbol.isBlank() || symbol.matches("\\d+")) return "";
    if (CRYPTO.containsKey(symbol)) return CRYPTO.get(symbol);
    return "BRK".equals(symbol) ? "BRK-B" : symbol;
  }

  private static List<MarketBar> parse(Instrument instrument, String timeframe, JsonNode result, int limit) {
    JsonNode times = result.path("timestamp");
    JsonNode quote = result.path("indicators").path("quote").path(0);
    if (!times.isArray() || quote.isMissingNode()) return List.of();
    var open = quote.path("open");
    var high = quote.path("high");
    var low = quote.path("low");
    var close = quote.path("close");
    var volume = quote.path("volume");
    java.util.ArrayList<MarketBar> bars = new java.util.ArrayList<>();
    for (int i = Math.max(0, times.size() - limit); i < times.size(); i++) {
      if (missing(open, high, low, close, i)) continue;
      bars.add(new MarketBar(JdbcSupport.id(), instrument.id(), timeframe, time(timeframe, times.get(i).asLong()),
          decimal(open.get(i)), decimal(high.get(i)), decimal(low.get(i)), decimal(close.get(i)), volume(volume, i)));
    }
    return bars;
  }

  private static boolean missing(JsonNode open, JsonNode high, JsonNode low, JsonNode close, int index) {
    return open.path(index).isNull() || high.path(index).isNull() || low.path(index).isNull()
        || close.path(index).isNull();
  }

  private static BigDecimal decimal(JsonNode value) { return BigDecimal.valueOf(value.asDouble()); }

  private static BigDecimal volume(JsonNode volume, int index) {
    return volume.path(index).isNumber() ? BigDecimal.valueOf(volume.path(index).asLong()) : BigDecimal.ZERO;
  }

  private static String time(String timeframe, long epochSeconds) {
    Instant instant = Instant.ofEpochSecond(epochSeconds);
    return "1D".equalsIgnoreCase(timeframe) ? instant.atZone(ZoneOffset.UTC).toLocalDate().toString()
        : instant.toString();
  }
}
