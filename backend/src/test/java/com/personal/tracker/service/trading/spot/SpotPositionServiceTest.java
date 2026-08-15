package com.personal.tracker.service.trading.spot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.config.BinanceSpotProperties;
import com.personal.tracker.repository.trading.PositionCostOverrideRepository;
import com.personal.tracker.repository.trading.PositionCostOverrideRepository.PositionCostOverride;
import com.personal.tracker.repository.trading.SignalTradeRepository;
import com.personal.tracker.service.trading.binance.BinanceSpotClient;
import com.personal.tracker.service.trading.binance.BinanceSpotClient.AccountBalance;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpotPositionServiceTest {
  @Mock private BinanceSpotClient binance;
  @Mock private BinanceSpotProperties properties;
  @Mock private SignalTradeRepository trades;
  @Mock private PositionCostOverrideRepository overrides;
  private SpotPositionService service;

  @BeforeEach
  void setUp() {
    service = new SpotPositionService(binance, properties, trades, overrides);
    when(properties.liveReady()).thenReturn(true);
    when(binance.prices()).thenReturn(Map.of("BTCUSDT", number("100")));
    when(binance.balances()).thenReturn(List.of(
        new AccountBalance("BTC", number("2"), BigDecimal.ZERO)));
    when(binance.fundingBalances()).thenReturn(List.of());
    when(trades.positionCosts()).thenReturn(List.of());
  }

  @Test
  void servesRepeatedReadsFromCache() {
    when(overrides.findAll()).thenReturn(List.of());

    var first = service.positions(false);
    var second = service.positions(false);

    assertEquals(first.updatedAt(), second.updatedAt());
    assertEquals(number("200"), second.marketValue());
    verify(binance, times(1)).prices();
    verify(binance, times(1)).balances();
    verify(binance, times(1)).fundingBalances();
  }

  @Test
  void appliesAndClearsManualAverageCostWithoutRefetchingExchange() {
    AtomicReference<BigDecimal> manual = new AtomicReference<>();
    when(overrides.findAll()).thenAnswer(invocation -> manual.get() == null
        ? List.of()
        : List.of(new PositionCostOverride(
            "BINANCE", "BTCUSDT", manual.get(), "now")));
    doAnswer(invocation -> {
      manual.set(invocation.getArgument(2));
      return null;
    }).when(overrides).upsert(anyString(), anyString(), any());
    doAnswer(invocation -> {
      manual.set(null);
      return null;
    }).when(overrides).delete(anyString(), anyString());

    assertFalse(service.positions(false).positions().get(0).costKnown());
    var updated = service.setAverageCost("BINANCE", "BTCUSDT", number("60"));

    assertTrue(updated.positions().get(0).costKnown());
    assertEquals("MANUAL", updated.positions().get(0).costSource());
    assertEquals(0, number("120").compareTo(updated.knownCost()));
    assertEquals(0, number("80").compareTo(updated.knownPnl()));

    var cleared = service.clearAverageCost("BINANCE", "BTCUSDT");
    assertFalse(cleared.positions().get(0).costKnown());
    verify(binance, times(1)).balances();
  }

  private static BigDecimal number(String value) {
    return new BigDecimal(value);
  }
}
