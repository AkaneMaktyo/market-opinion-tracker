package com.personal.tracker.service.trading.spot;

import com.personal.tracker.config.BinanceSpotProperties;
import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.repository.trading.PositionCostOverrideRepository;
import com.personal.tracker.repository.trading.PositionCostOverrideRepository.CostAnchor;
import com.personal.tracker.repository.trading.PositionCostOverrideRepository.PositionCostOverride;
import com.personal.tracker.repository.trading.SignalTradeRepository;
import com.personal.tracker.repository.trading.SignalTradeRepository.PositionCost;
import com.personal.tracker.service.trading.binance.BinanceSpotClient;
import com.personal.tracker.service.trading.binance.BinanceSpotClient.AccountBalance;
import com.personal.tracker.service.trading.binance.BinanceSpotClient.FundingBalance;
import com.personal.tracker.service.trading.spot.PositionCostBasisCalculator.ReconciledCost;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SpotPositionService {
  private static final BigDecimal MAX_AVERAGE_COST = new BigDecimal("1000000000");
  private final BinanceSpotClient binance;
  private final BinanceSpotProperties properties;
  private final SignalTradeRepository trades;
  private final PositionCostOverrideRepository overrides;
  private final AtomicBoolean refreshing = new AtomicBoolean();
  private final Map<String, BigDecimal> lastKnownPrices = new ConcurrentHashMap<>();
  private volatile PositionPortfolio cachedPortfolio;

  public SpotPositionService(
      BinanceSpotClient binance,
      BinanceSpotProperties properties,
      SignalTradeRepository trades,
      PositionCostOverrideRepository overrides) {
    this.binance = binance;
    this.properties = properties;
    this.trades = trades;
    this.overrides = overrides;
  }

  public PositionPortfolio positions(boolean forceRefresh) {
    if (!properties.liveReady()) return unavailablePortfolio();
    PositionPortfolio current = cachedPortfolio;
    if (!forceRefresh && current != null) return current;
    if (!refreshing.compareAndSet(false, true)) {
      return current != null ? current : buildPortfolio();
    }
    try {
      PositionPortfolio refreshed = buildPortfolio();
      cachedPortfolio = refreshed;
      return refreshed;
    } finally {
      refreshing.set(false);
    }
  }

  @Scheduled(
      fixedDelayString = "${trading.binance.positions-refresh-ms:30000}",
      initialDelayString = "${trading.binance.positions-initial-delay-ms:1000}")
  public void refreshCache() {
    if (!properties.liveReady() || !refreshing.compareAndSet(false, true)) return;
    try {
      cachedPortfolio = buildPortfolio();
    } catch (RuntimeException ignored) {
      // Keep serving the last successful snapshot during a short exchange/network failure.
    } finally {
      refreshing.set(false);
    }
  }

  public PositionPortfolio setAverageCost(
      String provider, String symbol, BigDecimal averageCost) {
    if (averageCost == null || averageCost.signum() <= 0
        || averageCost.compareTo(MAX_AVERAGE_COST) > 0) {
      throw new IllegalArgumentException("平均成本必须大于 0 且不超过 1,000,000,000");
    }
    PositionPortfolio current = positions(false);
    SpotPosition position = requireEditablePosition(current, provider, symbol);
    BigDecimal normalizedCost = averageCost.setScale(8, RoundingMode.HALF_UP);
    PositionCost trade = tradeCosts().get(PositionCostOverrideRepository.key(provider, symbol));
    overrides.upsert(
        provider, symbol, normalizedCost, createAnchor(position, normalizedCost, trade));
    PositionPortfolio updated = applyCosts(current.positions(), current.marketValue());
    cachedPortfolio = updated;
    return updated;
  }

  public PositionPortfolio clearAverageCost(String provider, String symbol) {
    PositionPortfolio current = positions(false);
    requireEditablePosition(current, provider, symbol);
    overrides.delete(provider, symbol);
    PositionPortfolio updated = applyCosts(current.positions(), current.marketValue());
    cachedPortfolio = updated;
    return updated;
  }

  private PositionPortfolio buildPortfolio() {
    Map<String, BigDecimal> prices = binance.prices();
    List<SpotPosition> positions = new ArrayList<>();
    BigDecimal marketValue = BigDecimal.ZERO;
    for (AccountBalance balance : binance.balances()) {
      BigDecimal quantity = balance.total();
      if (isCash(balance.asset())) {
        positions.add(cashPosition("BINANCE_SPOT", balance.asset(), quantity,
            balance.free(), balance.locked()));
        marketValue = marketValue.add(quantity);
        continue;
      }
      String symbol = balance.asset() + "USDT";
      BigDecimal price = currentOrLastKnownPrice("CRYPTO", symbol, prices.get(symbol));
      if (price == null) continue;
      BigDecimal value = quantity.multiply(price);
      positions.add(rawPosition(
          "CRYPTO", "BINANCE", balance.asset(), symbol, quantity,
          balance.free(), balance.locked(), price, value));
      marketValue = marketValue.add(value);
    }

    List<FundingBalance> fundingBalances = binance.fundingBalances();
    Map<String, BigDecimal> equityPrices = new ConcurrentHashMap<>();
    fundingBalances.parallelStream()
        .map(FundingBalance::asset)
        .filter(asset -> asset.startsWith("EQ_") && asset.length() > 3)
        .map(asset -> asset.substring(3))
        .distinct()
        .forEach(ticker -> {
          BigDecimal price = equityPrice(ticker);
          if (price != null) equityPrices.put(ticker, price);
        });
    for (FundingBalance balance : fundingBalances) {
      BigDecimal quantity = balance.total();
      BigDecimal locked = quantity.subtract(balance.free());
      if (balance.asset().startsWith("EQ_") && balance.asset().length() > 3) {
        String ticker = balance.asset().substring(3);
        BigDecimal price = equityPrices.get(ticker);
        if (price == null) continue;
        BigDecimal value = quantity.multiply(price);
        positions.add(rawPosition(
            "STOCK", "BINANCE_STOCKS", ticker, ticker, quantity,
            balance.free(), locked, price, value));
        marketValue = marketValue.add(value);
      } else if (isCash(balance.asset())) {
        positions.add(cashPosition(
            "BINANCE_FUNDING", balance.asset(), quantity, balance.free(), locked));
        marketValue = marketValue.add(quantity);
      } else {
        String symbol = balance.asset() + "USDT";
        BigDecimal price = currentOrLastKnownPrice("CRYPTO", symbol, prices.get(symbol));
        if (price == null) continue;
        BigDecimal value = quantity.multiply(price);
        positions.add(rawPosition(
            "CRYPTO", "BINANCE_FUNDING", balance.asset(), symbol, quantity,
            balance.free(), locked, price, value));
        marketValue = marketValue.add(value);
      }
    }
    positions.sort((left, right) -> right.marketValue().compareTo(left.marketValue()));
    return applyCosts(List.copyOf(positions), marketValue);
  }

  private PositionPortfolio applyCosts(List<SpotPosition> source, BigDecimal marketValue) {
    Map<String, PositionCostOverride> manualCosts = overrides.findAll().stream()
        .collect(Collectors.toMap(
            item -> PositionCostOverrideRepository.key(item.provider(), item.symbol()),
            Function.identity(), (left, right) -> right));
    Map<String, PositionCost> tradeCosts = tradeCosts();
    List<SpotPosition> positions = new ArrayList<>();
    BigDecimal knownCost = BigDecimal.ZERO;
    BigDecimal knownPnl = BigDecimal.ZERO;
    for (SpotPosition position : source) {
      SpotPosition valued = applyCost(position, manualCosts, tradeCosts);
      positions.add(valued);
      if (valued.costKnown()) {
        knownCost = knownCost.add(valued.cost());
        knownPnl = knownPnl.add(valued.pnl());
      }
    }
    BigDecimal knownPnlPercent = knownCost.signum() > 0
        ? knownPnl.divide(knownCost, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
        : BigDecimal.ZERO;
    return new PositionPortfolio(
        true, false, "币安现货、资金账户与股票持仓", "USD", marketValue,
        knownCost, knownPnl, knownPnlPercent, JdbcSupport.now(), List.copyOf(positions));
  }

  private SpotPosition applyCost(
      SpotPosition position,
      Map<String, PositionCostOverride> manualCosts,
      Map<String, PositionCost> tradeCosts) {
    if ("CASH".equals(position.assetClass())) return position;
    String key = PositionCostOverrideRepository.key(position.provider(), position.symbol());
    PositionCostOverride manual = manualCosts.get(key);
    ReconciledCost reconciled;
    String source;
    if (manual != null) {
      CostAnchor anchor = manual.anchor();
      PositionCost trade = tradeCosts.get(key);
      if (anchor == null) {
        anchor = createAnchor(position, manual.averageCost(), trade);
        overrides.anchorIfAbsent(position.provider(), position.symbol(), anchor);
      }
      reconciled = PositionCostBasisCalculator.fromAnchor(position.quantity(), anchor, trade);
      if (reconciled.reviewRequired()) {
        return reviewPosition(position, reconciled.averageCost());
      }
      advanceAnchor(position, anchor, reconciled, trade);
      source = "MANUAL";
    } else {
      reconciled = PositionCostBasisCalculator.fromTrades(
          position.quantity(), tradeCosts.get(key));
      source = "TRADES";
    }
    if (!reconciled.known()) return rawPosition(
        position.assetClass(), position.provider(), position.asset(), position.symbol(),
        position.quantity(), position.freeQuantity(), position.lockedQuantity(),
        position.currentPrice(), position.marketValue());
    BigDecimal cost = reconciled.cost();
    BigDecimal pnl = position.marketValue().subtract(cost);
    BigDecimal pnlPercent = cost.signum() > 0
        ? pnl.divide(cost, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
        : BigDecimal.ZERO;
    return new SpotPosition(
        position.assetClass(), position.provider(), position.asset(), position.symbol(),
        position.quantity(), position.freeQuantity(), position.lockedQuantity(),
        position.currentPrice(), position.marketValue(), true, source,
        cost, reconciled.averageCost(), pnl, pnlPercent);
  }

  private Map<String, PositionCost> tradeCosts() {
    return trades.positionCosts().stream().collect(Collectors.toMap(
        item -> PositionCostOverrideRepository.key(item.provider(), item.exchangeSymbol()),
        Function.identity(), (left, right) -> right));
  }

  private static CostAnchor createAnchor(
      SpotPosition position, BigDecimal averageCost, PositionCost trade) {
    BigDecimal tradeQuantity = trade == null ? BigDecimal.ZERO : trade.executedQuantity();
    BigDecimal tradeQuote = trade == null ? BigDecimal.ZERO : trade.cumulativeQuote();
    return new CostAnchor(
        position.quantity().setScale(12, RoundingMode.HALF_UP),
        position.quantity().multiply(averageCost).setScale(8, RoundingMode.HALF_UP),
        tradeQuantity.setScale(12, RoundingMode.HALF_UP),
        tradeQuote.setScale(8, RoundingMode.HALF_UP));
  }

  private void advanceAnchor(
      SpotPosition position, CostAnchor current, ReconciledCost cost, PositionCost trade) {
    if (!PositionCostBasisCalculator.canAdvanceAnchor(position.quantity(), current, trade)) return;
    CostAnchor advanced = createAnchor(position, cost.averageCost(), trade);
    advanced = new CostAnchor(
        advanced.basisQuantity(), cost.cost().setScale(8, RoundingMode.HALF_UP),
        advanced.tradeQuantity(), advanced.tradeQuote());
    if (!advanced.equals(current)) {
      overrides.updateAnchor(position.provider(), position.symbol(), advanced);
    }
  }

  private BigDecimal equityPrice(String ticker) {
    try {
      return currentOrLastKnownPrice(
          "STOCK", ticker, binance.equityQuote(ticker).midpoint());
    } catch (RuntimeException ignored) {
      return lastKnownPrices.get(priceKey("STOCK", ticker));
    }
  }

  private BigDecimal currentOrLastKnownPrice(
      String assetClass, String symbol, BigDecimal currentPrice) {
    String key = priceKey(assetClass, symbol);
    if (currentPrice != null && currentPrice.signum() > 0) {
      lastKnownPrices.put(key, currentPrice);
      return currentPrice;
    }
    return lastKnownPrices.get(key);
  }

  private static String priceKey(String assetClass, String symbol) {
    return assetClass + ':' + symbol;
  }

  private PositionPortfolio unavailablePortfolio() {
    String message = properties.paper()
        ? "当前为模拟交易，尚无真实账户持仓"
        : "币安现货账户未配置或未启用";
    return new PositionPortfolio(
        false, properties.paper(), message, "USD", BigDecimal.ZERO, BigDecimal.ZERO,
        BigDecimal.ZERO, BigDecimal.ZERO, JdbcSupport.now(), List.of());
  }

  private static SpotPosition requireEditablePosition(
      PositionPortfolio portfolio, String provider, String symbol) {
    return portfolio.positions().stream().filter(position ->
            !"CASH".equals(position.assetClass())
                && PositionCostOverrideRepository.key(position.provider(), position.symbol())
                    .equals(PositionCostOverrideRepository.key(provider, symbol)))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("找不到可设置成本的当前持仓"));
  }

  private static SpotPosition rawPosition(
      String assetClass, String provider, String asset, String symbol,
      BigDecimal quantity, BigDecimal free, BigDecimal locked,
      BigDecimal price, BigDecimal value) {
    return new SpotPosition(
        assetClass, provider, asset, symbol, quantity, free, locked,
        price, value, false, "UNKNOWN", null, null, null, null);
  }

  private static SpotPosition cashPosition(
      String provider, String asset, BigDecimal quantity, BigDecimal free, BigDecimal locked) {
    return new SpotPosition(
        "CASH", provider, asset, asset, quantity, free, locked,
        BigDecimal.ONE, quantity, false, "UNKNOWN", null, null, null, null);
  }

  private static SpotPosition reviewPosition(
      SpotPosition position, BigDecimal lastKnownAverageCost) {
    return new SpotPosition(
        position.assetClass(), position.provider(), position.asset(), position.symbol(),
        position.quantity(), position.freeQuantity(), position.lockedQuantity(),
        position.currentPrice(), position.marketValue(), false, "MANUAL_REVIEW_REQUIRED",
        null, lastKnownAverageCost, null, null);
  }

  private static boolean isCash(String asset) {
    return "USDT".equals(asset) || "USDC".equals(asset) || "FDUSD".equals(asset)
        || "BUSD".equals(asset) || "USD".equals(asset);
  }

  public record SpotPosition(
      String assetClass, String provider, String asset, String symbol,
      BigDecimal quantity, BigDecimal freeQuantity, BigDecimal lockedQuantity,
      BigDecimal currentPrice, BigDecimal marketValue, boolean costKnown, String costSource,
      BigDecimal cost, BigDecimal averageCost, BigDecimal pnl, BigDecimal pnlPercent) {
  }

  public record PositionPortfolio(
      boolean accountReady, boolean paper, String message, String valuationCurrency,
      BigDecimal marketValue, BigDecimal knownCost, BigDecimal knownPnl,
      BigDecimal knownPnlPercent, String updatedAt, List<SpotPosition> positions) {
  }
}
