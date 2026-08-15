package com.personal.tracker.service.trading.spot;

import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TradeRoutingPolicy {
  private static final Set<String> CRYPTO_MARKETS = Set.of(
      "CRYPTO", "CRYPTOCURRENCY", "COIN", "DIGITAL_ASSET", "BINANCE", "BITGET", "OKX", "BYBIT");

  public Route route(String market, String symbol) {
    String normalizedMarket = clean(market);
    String normalizedSymbol = clean(symbol).replaceAll("[^A-Z0-9]", "");
    boolean crypto = CRYPTO_MARKETS.contains(normalizedMarket)
        || normalizedSymbol.endsWith("USDT")
        || normalizedSymbol.endsWith("USDC");
    return crypto
        ? new Route("CRYPTO", "BINANCE", true, "币安现货")
        : new Route("STOCK", "BINANCE_STOCKS", true, "币安股票");
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  public record Route(String assetClass, String provider, boolean supported, String displayName) {
  }
}
