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
public class BinanceMarketBarProvider implements MarketBarProvider {
  private static final Logger log = LoggerFactory.getLogger(BinanceMarketBarProvider.class);
  private static final Map<String, String> INTERVALS = Map.of(
      "1D", "1d",
      "1H", "1h",
      "4H", "4h");
  private static final int MAX_LIMIT = 1000;

  private final RestClient client = RestClient.builder()
      .baseUrl("https://api.binance.com")
      .requestFactory(MarketBarSupport.requestFactory())
      .build();

  @Override
  public String name() {
    return "binance";
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
    for (String symbol : MarketBarSupport.usdtSymbols(instrument.symbol())) {
      List<MarketBar> bars = request(instrument, timeframe, interval, symbol, startTime, endTime, limit);
      if (!bars.isEmpty()) {
        return bars;
      }
    }
    return List.of();
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
      UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/v3/klines")
          .queryParam("symbol", symbol)
          .queryParam("interval", interval)
          .queryParam("limit", MarketBarSupport.limit(limit, MAX_LIMIT));
      if (startTime != null) {
        builder.queryParam("startTime", startTime);
      }
      if (endTime != null) {
        builder.queryParam("endTime", endTime);
      }
      return parse(instrument, timeframe, client.get().uri(builder.toUriString())
          .retrieve().body(JsonNode.class));
    } catch (RuntimeException error) {
      log.debug("Binance K line fetch failed for {}", symbol, error);
      return List.of();
    }
  }

  private List<MarketBar> parse(Instrument instrument, String timeframe, JsonNode data) {
    if (data == null || !data.isArray()) {
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
