package com.personal.tracker.service.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.MarketBar;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.service.market.bitget.BitgetPublicApiClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
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
  private final InstrumentRepository instruments;
  private final BitgetPublicApiClient client;
  private final Map<String, CachedMapping> mappings = new ConcurrentHashMap<>();

  public BitgetMarketBarProvider(
      InstrumentRepository instruments,
      BitgetPublicApiClient client) {
    this.instruments = instruments;
    this.client = client;
  }

  @Override
  public String name() {
    return "bitget";
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
    int boundedLimit = Math.min(Math.max(limit, 1), DEFAULT_LIMIT);
    CachedMapping cached = cachedMapping(instrument);
    if (cached.unavailable()) {
      return List.of();
    }
    Query cachedQuery = cached.query();
    int attemptedCount = 0;
    int unavailableCount = 0;
    if (cachedQuery != null) {
      attemptedCount++;
      FetchOutcome outcome = request(
          instrument,
          timeframe,
          interval,
          cachedQuery,
          startTime,
          endTime,
          boundedLimit);
      if (!outcome.bars().isEmpty()) {
        return outcome.bars();
      }
      if (outcome.unavailable()) {
        unavailableCount++;
      }
    }
    List<Query> candidates = queriesForSymbol(instrument.symbol());
    for (Query query : candidates) {
      if (query.equals(cachedQuery)) {
        continue;
      }
      attemptedCount++;
      FetchOutcome outcome = request(
          instrument,
          timeframe,
          interval,
          query,
          startTime,
          endTime,
          boundedLimit);
      if (!outcome.bars().isEmpty()) {
        rememberMapped(instrument, query);
        return outcome.bars();
      }
      if (outcome.unavailable()) {
        unavailableCount++;
      }
    }
    if (attemptedCount == 0 || unavailableCount == attemptedCount) {
      rememberUnavailable(instrument);
    }
    return List.of();
  }

  private CachedMapping cachedMapping(Instrument instrument) {
    return mappings.computeIfAbsent(instrument.id(), ignored -> {
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

  static List<Query> queriesForSymbol(String symbol) {
    String clean = MarketBarSupport.cleanSymbol(symbol);
    if (clean.isBlank() || clean.matches("\\d+")) {
      return List.of();
    }
    String root = clean.endsWith("USDT") ? clean.substring(0, clean.length() - 4) : clean;
    return List.of(
        new Query("USDT-FUTURES", root + "USDT"),
        new Query("USDT-FUTURES", root + "STOCKUSDT"),
        new Query("SPOT", "R" + root + "USDT"),
        new Query("SPOT", root + "ONUSDT"),
        new Query("SPOT", root + "USDT"));
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
      JsonNode root = client.get(builder.toUriString());
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

  record Query(String category, String symbol) {
  }
}
