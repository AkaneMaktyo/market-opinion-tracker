package com.personal.tracker.service.alerts;

import com.fasterxml.jackson.databind.JsonNode;
import com.personal.tracker.service.market.bitget.BitgetPublicApiClient;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class BitgetTickerClient {
  private static final String SUCCESS = "00000";
  private final BitgetPublicApiClient client;

  public BitgetTickerClient(BitgetPublicApiClient client) {
    this.client = client;
  }

  public Optional<Quote> fetch(String category, String symbol) {
    String uri = "SPOT".equalsIgnoreCase(category)
        ? UriComponentsBuilder.fromPath("/api/v2/spot/market/tickers")
            .queryParam("symbol", symbol)
            .toUriString()
        : UriComponentsBuilder.fromPath("/api/v2/mix/market/ticker")
            .queryParam("productType", category)
            .queryParam("symbol", symbol)
            .toUriString();
    JsonNode root = client.get(uri);
    if (root == null || !SUCCESS.equals(root.path("code").asText())) {
      return Optional.empty();
    }
    JsonNode row = root.path("data").path(0);
    String value = row.path("lastPr").asText("");
    if (value.isBlank()) {
      return Optional.empty();
    }
    try {
      long timestamp = row.path("ts").asLong(root.path("requestTime").asLong());
      return Optional.of(new Quote(new BigDecimal(value), timestamp));
    } catch (NumberFormatException error) {
      return Optional.empty();
    }
  }

  public record Quote(BigDecimal price, long timestamp) {
  }
}
