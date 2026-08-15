package com.personal.tracker.service.trading.spot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.config.BinanceSpotProperties;
import com.personal.tracker.repository.alerts.PriceAlertRepository;
import com.personal.tracker.repository.alerts.PriceAlertRepository.PriceAlertView;
import com.personal.tracker.repository.trading.SignalTradeRepository;
import com.personal.tracker.repository.trading.SignalTradeRepository.TradePlan;
import com.personal.tracker.service.trading.binance.BinanceSpotClient;
import com.personal.tracker.service.trading.binance.BinanceSpotClient.SymbolRules;
import com.personal.tracker.service.trading.spot.SignalTradePlanService.CreatePlanCommand;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignalTradePlanServiceTest {
  @Mock private PriceAlertRepository alerts;
  @Mock private SignalTradeRepository trades;
  @Mock private BinanceSpotClient binance;
  @Mock private BinanceSpotProperties properties;
  private SignalTradePlanService service;

  @BeforeEach
  void setUp() {
    service = new SignalTradePlanService(
        alerts, trades, binance, properties, new TradeRoutingPolicy());
  }

  @Test
  void createsStockPlanThroughBinanceStocksWithoutUsingSpotOrderPath() {
    when(trades.findByAlertId("alert-stock")).thenReturn(Optional.empty());
    when(alerts.findById("alert-stock")).thenReturn(Optional.of(stockAlert()));
    when(binance.equityRules("GOOGL")).thenReturn(new SymbolRules(
        "GOOGL", "TRADING", "GOOGL", "USDC", number("0.01"),
        number("0.0001"), number("0.0001"), number("1000"), number("1")));
    when(properties.environment()).thenReturn("mainnet");
    when(properties.paper()).thenReturn(true);
    AtomicReference<TradePlan> created = new AtomicReference<>();
    when(trades.create(any(), any())).thenAnswer(invocation -> {
      TradePlan plan = invocation.getArgument(0);
      created.set(plan);
      return plan;
    });
    when(trades.findPlan(anyString())).thenAnswer(invocation -> Optional.of(created.get()));
    when(trades.orders(anyString())).thenReturn(List.of());

    var result = service.create(
        "alert-stock", new CreatePlanCommand(number("1000"), 1));

    assertEquals("STOCK", result.assetClass());
    assertEquals("BINANCE_STOCKS", result.provider());
    assertEquals("USDC", result.quoteAsset());
    verify(binance).equityRules("GOOGL");
    verify(binance, never()).symbolRules(anyString());
  }

  private PriceAlertView stockAlert() {
    return new PriceAlertView(
        "alert-stock", "instrument-stock", "GOOGL", "谷歌", "US", "POINT", "DOWN",
        number("325"), number("325"), number("325"), "ACTIVE", number("331"), "now",
        null, "WAITING", null, null, null, null, "now", "now");
  }

  private BigDecimal number(String value) {
    return new BigDecimal(value);
  }
}
