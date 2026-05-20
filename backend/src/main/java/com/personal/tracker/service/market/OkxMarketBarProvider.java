package com.personal.tracker.service.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.MarketBar;
import com.personal.tracker.repository.JdbcSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OkxMarketBarProvider implements MarketBarProvider {
  private static final Logger log = LoggerFactory.getLogger(OkxMarketBarProvider.class);
  private static final Map<String, String> INTERVALS = Map.of(
      "1D", "1Dutc",
      "1H", "1H",
      "4H", "4H");
  private static final int MAX_LIMIT = 300;

  private final RestClient client = RestClient.builder()
      .baseUrl("https://www.okx.com")
      .requestFactory(MarketBarSupport.requestFactory())
      .build();

  @Override
  public String name() {
    return "okx";
  }

  @Override
  public List<MarketBar> fetch(Instrument instrument, String timeframe) {
    return fetch(instrument, timeframe, null, null, MAX_LIMIT);
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
    for (String symbol : candidates(instrument.symbol())) {
      List<MarketBar> bars = request(instrument, timeframe, interval, symbol, startTime, endTime, limit);
      if (!bars.isEmpty()) {
        return bars;
      }
    }
    return List.of();
  }

  private List<String> candidates(String symbol) {
    String clean = MarketBarSupport.cleanSymbol(symbol);
    if (clean.isBlank() || clean.matches("\\d+")) {
      return List.of();
    }
    String base = clean.endsWith("USDT") ? clean.substring(0, clean.length() - 4) : clean;
    return List.of(base + "-USDT-SWAP", base + "-USDT");
  }

  private List<MarketBar> request(
      Instrument instrument,
      String timeframe,
      String interval,
      String symbol,
      Long startTime,
      Long endTime,
      int limit) {
    try {
      UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/v5/market/candles")
          .queryParam("instId", symbol)
          .queryParam("bar", interval)
          .queryParam("limit", MarketBarSupport.limit(limit, MAX_LIMIT));
      if (startTime != null) {
        builder.queryParam("before", startTime);
      }
      if (endTime != null) {
        builder.queryParam("after", endTime);
      }
      JsonNode root = client.get().uri(builder.toUriString()).retrieve().body(JsonNode.class);
      if (root == null || !"0".equals(root.path("code").asText())) {
        return List.of();
      }
      return parse(instrument, timeframe, root.path("data"));
    } catch (RuntimeException error) {
      log.debug("OKX K line fetch failed for {}", symbol, error);
      return List.of();
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
          MarketBarSupport.time(timeframe, row.get(0).asLong()),
          MarketBarSupport.decimal(row.get(1)),
          MarketBarSupport.decimal(row.get(2)),
          MarketBarSupport.decimal(row.get(3)),
          MarketBarSupport.decimal(row.get(4)),
          MarketBarSupport.decimal(row.get(5))));
    }
    return items;
  }
}
