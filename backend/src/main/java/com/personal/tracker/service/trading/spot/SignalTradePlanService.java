package com.personal.tracker.service.trading.spot;

import com.personal.tracker.config.BinanceSpotProperties;
import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.repository.alerts.PriceAlertRepository;
import com.personal.tracker.repository.alerts.PriceAlertRepository.PriceAlertView;
import com.personal.tracker.repository.trading.SignalTradeRepository;
import com.personal.tracker.repository.trading.SignalTradeRepository.NewOrder;
import com.personal.tracker.repository.trading.SignalTradeRepository.TradeOrder;
import com.personal.tracker.repository.trading.SignalTradeRepository.TradePlan;
import com.personal.tracker.service.trading.binance.BinanceOrderNormalizer;
import com.personal.tracker.service.trading.binance.BinanceOrderNormalizer.NormalizedOrder;
import com.personal.tracker.service.trading.binance.BinanceSpotClient;
import com.personal.tracker.service.trading.binance.BinanceSpotClient.BinanceClientException;
import com.personal.tracker.service.trading.binance.BinanceSpotClient.SymbolRules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class SignalTradePlanService {
  private static final BigDecimal MAX_TOTAL_COST = new BigDecimal("1000000");
  private final PriceAlertRepository alerts;
  private final SignalTradeRepository trades;
  private final BinanceSpotClient binance;
  private final BinanceSpotProperties properties;
  private final TradeRoutingPolicy routing;

  public SignalTradePlanService(
      PriceAlertRepository alerts,
      SignalTradeRepository trades,
      BinanceSpotClient binance,
      BinanceSpotProperties properties,
      TradeRoutingPolicy routing) {
    this.alerts = alerts;
    this.trades = trades;
    this.binance = binance;
    this.properties = properties;
    this.routing = routing;
  }

  public TradingStatus status() {
    return new TradingStatus(
        properties.enabled(), properties.configured(), properties.paper(), properties.liveReady(),
        properties.environment(), properties.enabled() && properties.configured(),
        properties.paper() ? "模拟交易" : properties.liveReady() ? "实盘交易" : "币安现货未就绪",
        properties.paper() ? "币安股票模拟计划" : properties.liveReady()
            ? "币安股票实盘，仅使用股票订单接口" : "币安股票账户未就绪");
  }

  public List<TradePlanView> plans() {
    return trades.plans().stream().map(this::view).toList();
  }

  public TradePlanView create(String alertId, CreatePlanCommand command) {
    TradePlan existing = trades.findByAlertId(alertId).orElse(null);
    if (existing != null) return view(existing);
    PriceAlertView alert = alerts.findById(alertId)
        .orElseThrow(() -> new IllegalArgumentException("价格信号不存在"));
    TradeRoutingPolicy.Route route = routing.route(alert.market(), alert.symbol());
    if (!route.supported()) {
      throw new IllegalStateException("当前交易标的尚未接入可用的买入通道");
    }
    BigDecimal totalCost = requireCost(command == null ? null : command.totalCost());
    int batches = requireBatches(alert, command == null ? 0 : command.batchCount());
    if ("POINT".equals(alert.alertType()) && "UP".equals(alert.triggerDirection())) {
      throw new IllegalArgumentException("向上突破信号不能直接挂限价买单，暂不支持自动下单");
    }

    boolean stock = "STOCK".equals(route.assetClass());
    String exchangeSymbol = stock ? stockSymbol(alert.symbol()) : binanceSymbol(alert.symbol());
    SymbolRules rules = stock
        ? binance.equityRules(exchangeSymbol)
        : binance.symbolRules(exchangeSymbol);
    validateRules(rules, stock);
    List<BigDecimal> levels = priceLevels(alert, batches);
    BigDecimal batchCost = totalCost.divide(BigDecimal.valueOf(batches), 8, RoundingMode.DOWN);
    String planId = JdbcSupport.id();
    String now = JdbcSupport.now();
    List<NewOrder> orders = new ArrayList<>();
    for (int index = 0; index < levels.size(); index++) {
      NormalizedOrder normalized = BinanceOrderNormalizer.normalize(levels.get(index), batchCost, rules);
      orders.add(new NewOrder(
          JdbcSupport.id(), index + 1, normalized.price(), normalized.notional(), normalized.quantity(),
          clientOrderId(planId, index + 1)));
    }
    TradePlan plan = new TradePlan(
        planId, alert.id(), alert.instrumentId(), route.assetClass(), route.provider(),
        rules.symbol(), rules.baseAsset(), rules.quoteAsset(), "BUY", totalCost, batches,
        properties.environment(), properties.paper(), "PLANNED", null, now, now);
    trades.create(plan, orders);
    if (!properties.paper()) submit(plan);
    return view(trades.findPlan(planId).orElseThrow());
  }

  private void submit(TradePlan plan) {
    properties.ensureTradingReady();
    for (TradeOrder order : trades.orders(plan.id())) {
      trades.markSubmitting(order.id());
      try {
        trades.updateOrder(order.id(), "BINANCE_STOCKS".equals(plan.provider())
            ? binance.placeEquityLimitOrder(
                order.exchangeSymbol(), order.side(), order.quantity(),
                order.price(), order.clientOrderId())
            : binance.placeLimitOrder(
                order.exchangeSymbol(), order.side(), order.quantity(),
                order.price(), order.clientOrderId()));
      } catch (BinanceClientException submitError) {
        recoverSubmittedOrder(plan.provider(), order, submitError);
      } catch (RuntimeException submitError) {
        trades.markOrderError(order.id(), "UNKNOWN", message(submitError));
      }
    }
    trades.refreshPlanStatus(plan.id());
  }

  private void recoverSubmittedOrder(
      String provider, TradeOrder order, BinanceClientException submitError) {
    try {
      trades.updateOrder(order.id(), "BINANCE_STOCKS".equals(provider)
          ? binance.equityOrder(order.clientOrderId())
          : binance.order(order.exchangeSymbol(), order.clientOrderId()));
    } catch (RuntimeException queryError) {
      String status = submitError.code() == 0 ? "UNKNOWN" : "ERROR";
      trades.markOrderError(order.id(), status, submitError.getMessage());
    }
  }

  private TradePlanView view(TradePlan plan) {
    return new TradePlanView(
        plan.id(), plan.alertId(), plan.instrumentId(), plan.assetClass(), plan.provider(),
        plan.exchangeSymbol(), plan.baseAsset(), plan.quoteAsset(), plan.side(), plan.totalCost(),
        plan.batchCount(), plan.environment(), plan.paper(), plan.status(), plan.errorMessage(),
        plan.createdAt(), plan.updatedAt(), trades.orders(plan.id()));
  }

  private static BigDecimal requireCost(BigDecimal value) {
    if (value == null || value.signum() <= 0) {
      throw new IllegalArgumentException("投入成本必须大于 0");
    }
    if (value.compareTo(MAX_TOTAL_COST) > 0) {
      throw new IllegalArgumentException("单个交易计划投入成本不能超过 1,000,000 稳定币");
    }
    return value.setScale(8, RoundingMode.DOWN);
  }

  private static int requireBatches(PriceAlertView alert, int value) {
    int batches = value <= 0 ? ("RANGE".equals(alert.alertType()) ? 3 : 1) : value;
    if (batches < 1 || batches > 5) {
      throw new IllegalArgumentException("分批次数必须在 1 到 5 之间");
    }
    if ("POINT".equals(alert.alertType()) && batches != 1) {
      throw new IllegalArgumentException("点位信号只能设置一笔买入");
    }
    return batches;
  }

  private static List<BigDecimal> priceLevels(PriceAlertView alert, int batches) {
    if ("POINT".equals(alert.alertType())) {
      BigDecimal target = alert.targetPrice() == null ? alert.lowerPrice() : alert.targetPrice();
      return List.of(target);
    }
    BigDecimal upper = alert.upperPrice().max(alert.lowerPrice());
    BigDecimal lower = alert.upperPrice().min(alert.lowerPrice());
    if (batches == 1) return List.of(upper);
    BigDecimal step = upper.subtract(lower)
        .divide(BigDecimal.valueOf(batches - 1L), 16, RoundingMode.HALF_UP);
    List<BigDecimal> levels = new ArrayList<>();
    for (int index = 0; index < batches; index++) {
      levels.add(index == batches - 1 ? lower : upper.subtract(step.multiply(BigDecimal.valueOf(index))));
    }
    return List.copyOf(levels);
  }

  private static String binanceSymbol(String symbol) {
    String clean = symbol == null ? "" : symbol.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    if (clean.isBlank()) throw new IllegalArgumentException("交易标的代码不能为空");
    return clean.endsWith("USDT") ? clean : clean + "USDT";
  }

  private static String stockSymbol(String symbol) {
    String clean = symbol == null ? "" : symbol.replaceAll("[^A-Za-z0-9.-]", "")
        .toUpperCase(Locale.ROOT);
    if (clean.isBlank()) throw new IllegalArgumentException("股票代码不能为空");
    return clean;
  }

  private static void validateRules(SymbolRules rules, boolean stock) {
    if (!"TRADING".equals(rules.status())) {
      throw new IllegalArgumentException(stock
          ? "当前股票暂不支持币安买入" : "当前交易对不在币安现货交易状态");
    }
    String expectedQuote = stock ? "USDC" : "USDT";
    if (!expectedQuote.equals(rules.quoteAsset())) {
      throw new IllegalArgumentException("当前标的不支持币安 " + expectedQuote + " 买入");
    }
  }

  private static String clientOrderId(String planId, int batch) {
    String clean = planId.replaceAll("[^A-Za-z0-9]", "");
    String body = clean.substring(0, Math.min(28, clean.length()));
    return "mot" + body + String.format(Locale.ROOT, "%02d", batch);
  }

  private static String message(Throwable error) {
    return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
  }

  public record CreatePlanCommand(BigDecimal totalCost, int batchCount) {
  }

  public record TradingStatus(
      boolean binanceEnabled, boolean binanceConfigured, boolean paper, boolean liveReady,
      String environment, boolean stockBrokerConfigured, String cryptoMessage, String stockMessage) {
  }

  public record TradePlanView(
      String id, String alertId, String instrumentId, String assetClass, String provider,
      String exchangeSymbol, String baseAsset, String quoteAsset, String side,
      BigDecimal totalCost, int batchCount, String environment, boolean paper,
      String status, String errorMessage, String createdAt, String updatedAt,
      List<TradeOrder> orders) {
  }
}
