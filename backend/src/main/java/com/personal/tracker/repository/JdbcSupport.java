package com.personal.tracker.repository;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public final class JdbcSupport {
  private JdbcSupport() {
  }

  public static String id() {
    return UUID.randomUUID().toString();
  }

  public static String now() {
    return Instant.now().toString();
  }

  public static String symbol(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  public static String market(String value, String symbol) {
    if (value != null && !value.isBlank()) {
      return value.trim().toUpperCase(Locale.ROOT);
    }
    String normalized = symbol(symbol);
    if (normalized.matches("\\d{4,6}")) {
      return "HK";
    }
    if (isCryptoSymbol(normalized)) {
      return "CRYPTO";
    }
    return "US";
  }

  private static boolean isCryptoSymbol(String symbol) {
    if (symbol.endsWith("USDT") || symbol.endsWith("USD")) {
      return true;
    }
    return switch (symbol) {
      case "BTC", "ETH", "SOL", "XRP", "BNB", "DOGE", "ADA", "TRX", "LTC", "BCH", "AVAX" -> true;
      default -> false;
    };
  }
}
