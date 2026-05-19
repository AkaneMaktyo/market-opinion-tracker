package com.personal.tracker.service.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.MarketBar;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.JdbcSupport;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class BitgetMarketBarProvider implements MarketBarProvider {
  private static final Logger log = LoggerFactory.getLogger(BitgetMarketBarProvider.class);
  private static final String SUCCESS = "00000";
  private static final Map<String, String> INTERVALS = Map.of(
      "1D", "1D",
      "1H", "1H",
      "4H", "4H");
  private static final int DEFAULT_LIMIT = 1000;
  private static final String MAPPED = "MAPPED";
  private static final String UNAVAILABLE = "UNAVAILABLE";

  private final ObjectMapper mapper;
  private final InstrumentRepository instruments;
  private final RestClient client;
  private final Map<String, CachedMapping> mappings = new ConcurrentHashMap<>();

  public BitgetMarketBarProvider(ObjectMapper mapper, InstrumentRepository instruments) {
    this.mapper = mapper;
    this.instruments = instruments;
    this.client = RestClient.builder()
        .baseUrl("https://api.bitget.com")
        .requestFactory(requestFactory())
        .build();
  }

  private SimpleClientHttpRequestFactory requestFactory() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(8));
    factory.setReadTimeout(Duration.ofSeconds(15));
    return factory;
  }

  @Override
  public List<MarketBar> fetch(Instrument instrument, String timeframe) {
    return fetch(instrument, timeframe, null, null, DEFAULT_LIMIT);
  }

  @Override
  public List<MarketBar> fetch(
      Instrument instrument,
      String timeframe,
      Long startTime,
      Long endTime,
      int limit) {
    String interval = INTERVALS.get(timeframe.toUpperCase());
    if (interval == null) {
      return List.of();
    }
    CachedMapping cached = cachedMapping(instrument);
    if (cached.unavailable()) {
      return List.of();
    }
    if (cached.query() != null) {
      FetchOutcome outcome = request(
          instrument,
          timeframe,
          interval,
          cached.query(),
          startTime,
          endTime,
          Math.min(Math.max(limit, 1), DEFAULT_LIMIT));
      if (outcome.unavailable()) {
        rememberUnavailable(instrument);
      }
      return outcome.bars();
    }
    List<Query> candidates = queries(instrument.symbol());
    int unavailableCount = 0;
    for (Query query : candidates) {
      FetchOutcome outcome = request(
          instrument,
          timeframe,
          interval,
          query,
          startTime,
          endTime,
          Math.min(Math.max(limit, 1), DEFAULT_LIMIT));
      if (!outcome.bars().isEmpty()) {
        rememberMapped(instrument, query);
        return outcome.bars();
      }
      if (outcome.unavailable()) {
        unavailableCount++;
      }
    }
    if (candidates.isEmpty() || unavailableCount == candidates.size()) {
      rememberUnavailable(instrument);
    }
    return List.of();
  }

  private CachedMapping cachedMapping(Instrument instrument) {
    return mappings.computeIfAbsent(instrument.id(), ignored -> {
      if (UNAVAILABLE.equalsIgnoreCase(instrument.bitgetStatus())) {
        return CachedMapping.unavailableMapping();
      }
      if (MAPPED.equalsIgnoreCase(instrument.bitgetStatus())
          && present(instrument.bitgetCategory())
          && present(instrument.bitgetSymbol())) {
        return new CachedMapping(new Query(instrument.bitgetCategory(), instrument.bitgetSymbol()), false);
      }
      return CachedMapping.unknown();
    });
  }

  private void rememberMapped(Instrument instrument, Query query) {
    mappings.put(instrument.id(), new CachedMapping(query, false));
    instruments.saveBitgetMapping(instrument.id(), query.category(), query.symbol());
  }

  private void rememberUnavailable(Instrument instrument) {
    mappings.put(instrument.id(), CachedMapping.unavailableMapping());
    instruments.markBitgetUnavailable(instrument.id());
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  private List<Query> queries(String symbol) {
    String clean = symbol.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    if (clean.isBlank()) {
      return List.of();
    }
    if (clean.matches("\\d+")) {
      return List.of();
    }
    return List.of(
        new Query("USDT-FUTURES", clean + "USDT"),
        new Query("SPOT", clean + "ONUSDT"),
        new Query("SPOT", clean + "USDT"));
  }

  private FetchOutcome request(
      Instrument instrument,
      String timeframe,
      String interval,
      Query query,
      Long startTime,
      Long endTime,
      int limit) {
    try {
      UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/v3/market/candles")
          .queryParam("category", query.category())
          .queryParam("symbol", query.symbol())
          .queryParam("interval", interval)
          .queryParam("limit", limit);
      if (startTime != null) {
        builder.queryParam("startTime", startTime);
      }
      if (endTime != null) {
        builder.queryParam("endTime", endTime);
      }
      JsonNode root = request(builder.toUriString());
      if (root == null) {
        return FetchOutcome.failed();
      }
      if (permanentlyUnavailable(root)) {
        return FetchOutcome.unavailableOutcome();
      }
      if (!SUCCESS.equals(root.path("code").asText())) {
        return FetchOutcome.failed();
      }
      return new FetchOutcome(parse(instrument, timeframe, root.path("data")), false);
    } catch (RuntimeException error) {
      log.debug("Bitget K line fetch failed for {}", query.symbol(), error);
      return FetchOutcome.failed();
    }
  }

  private JsonNode request(String uri) {
    try {
      return client.get().uri(uri).retrieve().body(JsonNode.class);
    } catch (RestClientResponseException error) {
      JsonNode body = readJson(error.getResponseBodyAsByteArray());
      if (body != null) {
        return body;
      }
      log.debug("Bitget Java client status error, trying PowerShell fallback: {}", error.getMessage());
      return requestWithPowerShell("https://api.bitget.com" + uri);
    } catch (RuntimeException error) {
      log.debug("Bitget Java client failed, trying PowerShell fallback: {}", error.getMessage());
      return requestWithPowerShell("https://api.bitget.com" + uri);
    }
  }

  private JsonNode requestWithPowerShell(String url) {
    Path output = null;
    try {
      output = Files.createTempFile("bitget-bars-", ".json");
      String command = """
          $ProgressPreference = 'SilentlyContinue';
          [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new();
          try {
            $response = Invoke-WebRequest -Uri '%s' -TimeoutSec 15 -UseBasicParsing;
            $response.Content;
          } catch {
            if ($_.ErrorDetails -ne $null -and -not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) {
              $_.ErrorDetails.Message;
              exit 0;
            }
            if ($_.Exception.Response -ne $null) {
              $stream = $_.Exception.Response.GetResponseStream();
              if ($stream -ne $null) {
                $reader = [System.IO.StreamReader]::new($stream);
                $reader.ReadToEnd();
                exit 0;
              }
            }
            throw;
          }
          """.formatted(url.replace("'", "''"));
      Process process = new ProcessBuilder(
          "powershell",
          "-NoProfile",
          "-ExecutionPolicy",
          "Bypass",
          "-Command",
          command)
          .redirectErrorStream(true)
          .redirectOutput(output.toFile())
          .start();
      Process running = process;
      CompletableFuture<Integer> exitCode = CompletableFuture.supplyAsync(() -> waitFor(running));
      try {
        if (exitCode.get(20, TimeUnit.SECONDS) != 0) {
          return null;
        }
        return readJson(Files.readAllBytes(output));
      } finally {
        if (process.isAlive()) {
          process.destroyForcibly();
        }
      }
    } catch (TimeoutException error) {
      log.debug("Bitget PowerShell fallback timed out: {}", url);
      return null;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      log.debug("Bitget PowerShell fallback failed: {}", error.getMessage());
      return null;
    } catch (Exception error) {
      log.debug("Bitget PowerShell fallback failed: {}", error.getMessage());
      return null;
    } finally {
      if (output != null) {
        try {
          Files.deleteIfExists(output);
        } catch (IOException error) {
          log.debug("Bitget temp file cleanup failed: {}", error.getMessage());
        }
      }
    }
  }

  private JsonNode readJson(byte[] data) {
    if (data == null || data.length == 0) {
      return null;
    }
    try {
      return mapper.readTree(data);
    } catch (IOException error) {
      log.debug("Bitget JSON parse failed: {}", error.getMessage());
      return null;
    }
  }

  private int waitFor(Process process) {
    try {
      return process.waitFor();
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      return -1;
    }
  }

  private List<MarketBar> parse(Instrument instrument, String timeframe, JsonNode data) {
    if (!data.isArray()) {
      return List.of();
    }
    List<MarketBar> items = new ArrayList<>();
    for (JsonNode row : data) {
      if (!row.isArray() || row.size() < 6) {
        continue;
      }
      items.add(new MarketBar(
          JdbcSupport.id(),
          instrument.id(),
          timeframe,
          time(timeframe, row.get(0).asLong()),
          decimal(row.get(1)),
          decimal(row.get(2)),
          decimal(row.get(3)),
          decimal(row.get(4)),
          decimal(row.get(5))));
    }
    return items;
  }

  private static BigDecimal decimal(JsonNode value) {
    return new BigDecimal(value.asText());
  }

  private static String time(String timeframe, long epochMillis) {
    Instant instant = Instant.ofEpochMilli(epochMillis);
    if ("1D".equalsIgnoreCase(timeframe)) {
      return instant.atZone(ZoneOffset.UTC).toLocalDate().toString();
    }
    return instant.toString();
  }

  private static boolean permanentlyUnavailable(JsonNode root) {
    String code = root.path("code").asText("");
    String message = root.path("msg").asText("").toLowerCase();
    return "25100".equals(code)
        || "40034".equals(code)
        || message.contains("does not exist");
  }

  private record CachedMapping(Query query, boolean unavailable) {
    static CachedMapping unknown() {
      return new CachedMapping(null, false);
    }

    static CachedMapping unavailableMapping() {
      return new CachedMapping(null, true);
    }
  }

  private record FetchOutcome(List<MarketBar> bars, boolean unavailable) {
    static FetchOutcome failed() {
      return new FetchOutcome(List.of(), false);
    }

    static FetchOutcome unavailableOutcome() {
      return new FetchOutcome(List.of(), true);
    }
  }

  private record Query(String category, String symbol) {
  }
}
