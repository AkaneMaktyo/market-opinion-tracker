package com.personal.tracker.service.market;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

final class MarketBarSupport {
  private MarketBarSupport() {
  }

  static SimpleClientHttpRequestFactory requestFactory() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(8));
    factory.setReadTimeout(Duration.ofSeconds(15));
    return factory;
  }

  static int limit(int value, int max) {
    return Math.min(Math.max(value, 1), max);
  }

  static BigDecimal decimal(JsonNode value) {
    return new BigDecimal(value.asText());
  }

  static String time(String timeframe, long epochMillis) {
    Instant instant = Instant.ofEpochMilli(epochMillis);
    if ("1D".equalsIgnoreCase(timeframe)) {
      return instant.atZone(ZoneOffset.UTC).toLocalDate().toString();
    }
    return instant.toString();
  }

  static String cleanSymbol(String symbol) {
    return symbol == null ? "" : symbol.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
  }

  static List<String> usdtSymbols(String symbol) {
    String clean = cleanSymbol(symbol);
    if (clean.isBlank() || clean.matches("\\d+")) {
      return List.of();
    }
    return clean.endsWith("USDT") ? List.of(clean) : List.of(clean + "USDT");
  }
}
