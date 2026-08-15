package com.personal.tracker.service.trading.spot;

import com.personal.tracker.config.BinanceSpotProperties;
import com.personal.tracker.repository.trading.SignalTradeRepository;
import com.personal.tracker.repository.trading.SignalTradeRepository.OpenTradeOrder;
import com.personal.tracker.service.trading.binance.BinanceSpotClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SignalOrderReconciler {
  private final SignalTradeRepository trades;
  private final BinanceSpotClient binance;
  private final BinanceSpotProperties properties;

  public SignalOrderReconciler(
      SignalTradeRepository trades,
      BinanceSpotClient binance,
      BinanceSpotProperties properties) {
    this.trades = trades;
    this.binance = binance;
    this.properties = properties;
  }

  @Scheduled(
      fixedDelayString = "${trading.binance.reconcile-ms:15000}",
      initialDelayString = "${trading.binance.reconcile-ms:15000}")
  public void reconcile() {
    if (!properties.liveReady()) return;
    for (OpenTradeOrder openOrder : trades.openOrders()) {
      var order = openOrder.order();
      try {
        trades.updateOrder(order.id(), "BINANCE_STOCKS".equals(openOrder.provider())
            ? binance.equityOrder(order.clientOrderId())
            : binance.order(order.exchangeSymbol(), order.clientOrderId()));
        trades.refreshPlanStatus(order.planId());
      } catch (RuntimeException ignored) {
        // A short exchange/network failure must not rewrite a known order into an error state.
      }
    }
  }
}
