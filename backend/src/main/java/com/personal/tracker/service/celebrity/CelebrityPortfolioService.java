package com.personal.tracker.service.celebrity;

import com.personal.tracker.config.celebrity.CelebrityDataProperties;
import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.MarketBar;
import com.personal.tracker.domain.celebrity.CelebrityFiling;
import com.personal.tracker.domain.celebrity.CelebrityHolding;
import com.personal.tracker.domain.celebrity.CelebrityHoldingChange;
import com.personal.tracker.domain.celebrity.CelebrityHoldingView;
import com.personal.tracker.domain.celebrity.CelebrityInvestor;
import com.personal.tracker.domain.celebrity.CelebrityInvestorOverview;
import com.personal.tracker.domain.celebrity.CelebritySyncStatus;
import com.personal.tracker.domain.celebrity.alerts.CelebrityAlertSettings;
import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.repository.MarketBarRepository;
import com.personal.tracker.repository.celebrity.CelebrityPortfolioRepository;
import com.personal.tracker.service.MarketDataService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CelebrityPortfolioService {
  private static final Logger log = LoggerFactory.getLogger(CelebrityPortfolioService.class);
  private static final int MAX_HOLDINGS = 200;
  private final CelebrityPortfolioRepository repository;
  private final CelebrityDataProperties properties;
  private final Sec13fClient sec;
  private final ArkHoldingsClient ark;
  private final CelebritySymbolResolver symbols;
  private final CelebrityCostEstimator costs;
  private final CelebrityDiscoveryService discovery;
  private final CelebrityDisclosureAlertService disclosureAlerts;
  private final MarketDataService marketData;
  private final MarketBarRepository marketBars;
  private final AtomicBoolean syncRunning = new AtomicBoolean();

  public CelebrityPortfolioService(
      CelebrityPortfolioRepository repository,
      CelebrityDataProperties properties,
      Sec13fClient sec,
      ArkHoldingsClient ark,
      CelebritySymbolResolver symbols,
      CelebrityCostEstimator costs,
      CelebrityDiscoveryService discovery,
      CelebrityDisclosureAlertService disclosureAlerts,
      MarketDataService marketData,
      MarketBarRepository marketBars) {
    this.repository = repository;
    this.properties = properties;
    this.sec = sec;
    this.ark = ark;
    this.symbols = symbols;
    this.costs = costs;
    this.discovery = discovery;
    this.disclosureAlerts = disclosureAlerts;
    this.marketData = marketData;
    this.marketBars = marketBars;
  }

  public List<CelebrityInvestorOverview> investors() {
    return repository.findAllInvestors().stream().map(this::overview).toList();
  }

  public CelebrityPortfolio holdings(String slug, int requestedLimit) {
    CelebrityInvestor investor = investor(slug);
    CelebrityFiling filing = repository.latestFiling(investor.id()).orElse(null);
    if (filing == null) {
      return new CelebrityPortfolio(overview(investor), List.of(), "尚未同步到公开披露数据");
    }
    int limit = Math.max(1, Math.min(requestedLimit, MAX_HOLDINGS));
    List<CelebrityHolding> raw = repository.holdingsForFiling(filing.id());
    Map<String, List<CelebrityHolding>> histories = repository.holdingHistories(investor.id(), properties.historyLimit());
    Map<String, Quote> quotes = loadQuotes(raw.subList(0, Math.min(raw.size(), limit)));
    List<CelebrityHoldingView> items = new ArrayList<>();
    for (CelebrityHolding holding : raw.subList(0, Math.min(raw.size(), limit))) {
      Quote quote = quotes.get(symbolKey(holding.symbol()));
      BigDecimal currentPrice = quote == null ? null : quote.price();
      BigDecimal currentValue = currentPrice == null ? null : currentPrice.multiply(holding.shares())
          .setScale(2, RoundingMode.HALF_UP);
      CelebrityCostEstimator.CostEstimate estimate = costs.estimate(
          histories.getOrDefault(holding.holdingKey(), List.of()), investor.sourceType(), currentPrice);
      items.add(new CelebrityHoldingView(
          holding.holdingKey(), holding.symbol(), holding.symbolConfidence(), holding.cusip(), holding.issuerName(), holding.titleClass(),
          holding.putCall(), holding.shares(), holding.reportedValue(), holding.reportedWeight(),
          holding.reportedUnitValue(), currentPrice, currentValue, null, estimate.averageCost(),
          estimate.costLow(), estimate.costHigh(), estimate.totalCost(), estimate.pnl(), estimate.pnlPercent(),
          estimate.method(), estimate.confidence(), estimate.note(), filing.reportDate(), filing.filedAt(),
          filing.sourceUrl(), quote == null ? null : quote.updatedAt()));
    }
    String message = items.stream().anyMatch(item -> item.currentPrice() == null)
        ? "部分标的正在补齐代码或行情；空值不会按 0 计价。"
        : "行情已覆盖当前列表；成本和盈亏均为公开披露重建估算。";
    return new CelebrityPortfolio(overview(investor), items, message);
  }

  public List<CelebrityHoldingChange> changes(String slug) {
    return discovery.changes(slug);
  }

  public CelebritySyncStatus syncStatus() {
    return repository.syncStatus(properties.enabled());
  }

  public CelebrityAlertSettings alertSettings() {
    return repository.alertSettings();
  }

  public CelebrityAlertSettings saveAlertSettings(
      boolean enabled,
      List<String> investorSlugs,
      BigDecimal minimumReportedWeight) {
    return repository.saveAlertSettings(enabled, investorSlugs, minimumReportedWeight);
  }

  public CelebritySyncStatus syncAsync(String trigger) {
    if (!properties.enabled()) {
      return syncStatus();
    }
    if (!syncRunning.compareAndSet(false, true)) {
      return syncStatus();
    }
    repository.markSyncStarted();
    Thread worker = new Thread(() -> sync(trigger), "celebrity-disclosure-sync");
    worker.setDaemon(true);
    worker.start();
    return syncStatus();
  }

  private void sync(String trigger) {
    SyncCounters counters = new SyncCounters();
    List<String> errors = new ArrayList<>();
    List<CelebrityInvestor> completed = new ArrayList<>();
    Set<String> establishedBeforeSync = new java.util.HashSet<>();
    try {
      for (CelebrityInvestor investor : repository.findEnabledInvestors()) {
        if (repository.latestFiling(investor.id()).isPresent()) {
          establishedBeforeSync.add(investor.id());
        }
        try {
          if ("SEC_13F".equals(investor.sourceType())) {
            syncSec(investor, counters);
          } else if ("ARK_DAILY".equals(investor.sourceType()) && properties.arkEnabled()) {
            syncArk(investor, counters);
          }
          counters.investors++;
          completed.add(investor);
        } catch (RuntimeException error) {
          String message = investor.displayName() + "：" + friendlyMessage(error);
          errors.add(message);
          log.warn("名人持仓同步失败，trigger={} investor={}", trigger, investor.slug(), error);
        }
      }
      if (counters.filings > 0) {
        symbols.resolvePending(properties.symbolResolutionLimit());
      }
      try {
        disclosureAlerts.observe(completed, establishedBeforeSync);
      } catch (RuntimeException error) {
        errors.add("提醒处理：" + friendlyMessage(error));
        log.warn("名人披露提醒处理失败，trigger={}", trigger, error);
      }
      String outcome = errors.isEmpty() ? "SUCCESS" : counters.filings > 0 ? "PARTIAL" : "FAILED";
      repository.markSyncFinished(outcome, String.join("；", errors), counters.investors,
          counters.filings, counters.holdings);
    } catch (RuntimeException error) {
      repository.markSyncFinished("FAILED", friendlyMessage(error), counters.investors,
          counters.filings, counters.holdings);
      log.warn("名人持仓同步异常结束，trigger={}", trigger, error);
    } finally {
      syncRunning.set(false);
    }
  }

  private void syncSec(CelebrityInvestor investor, SyncCounters counters) {
    for (Sec13fClient.SecFiling source : sec.recentFilings(investor)) {
      CelebrityFiling filing = repository.saveFiling(new CelebrityFiling(
          JdbcSupport.id(), investor.id(), "SEC_13F", source.accessionNumber(), source.formType(),
          source.reportDate(), source.filedAt(), sec.filingUrl(source), source.amendment(), JdbcSupport.now()));
      List<CelebrityHolding> holdings = sec.holdings(source).stream()
          .map(item -> holding(investor.id(), filing.id(), item.holdingKey(), item.symbol(), "UNKNOWN", item.cusip(),
              item.issuerName(), item.titleClass(), item.putCall(), item.shares(), item.reportedValue(), null,
              item.reportedUnitValue()))
          .toList();
      repository.replaceHoldings(filing, withWeights(holdings));
      counters.filings++;
      counters.holdings += holdings.size();
    }
  }

  private void syncArk(CelebrityInvestor investor, SyncCounters counters) {
    ArkHoldingsClient.ArkSnapshot source = ark.currentArkk();
    CelebrityFiling filing = repository.saveFiling(new CelebrityFiling(
        JdbcSupport.id(), investor.id(), "ARK_DAILY", source.externalId(), "ARK_DAILY_HOLDINGS",
        source.reportDate(), source.reportDate() + "T00:00:00Z", source.sourceUrl(), false, JdbcSupport.now()));
    List<CelebrityHolding> holdings = source.holdings().stream()
        .map(item -> holding(investor.id(), filing.id(), item.holdingKey(), item.symbol(), "HIGH", item.cusip(),
            item.issuerName(), item.titleClass(), item.putCall(), item.shares(), item.reportedValue(),
            item.reportedWeight(), item.reportedUnitValue()))
        .toList();
    repository.replaceHoldings(filing, withWeights(holdings));
    counters.filings++;
    counters.holdings += holdings.size();
  }

  private CelebrityInvestorOverview overview(CelebrityInvestor investor) {
    Optional<CelebrityFiling> latest = repository.latestFiling(investor.id());
    if (latest.isEmpty()) {
      return new CelebrityInvestorOverview(investor.slug(), investor.displayName(), investor.managerName(),
          investor.sourceType(), investor.sourceUrl(), null, null, null, 0, 0, BigDecimal.ZERO);
    }
    List<CelebrityHolding> holdings = repository.holdingsForFiling(latest.get().id());
    BigDecimal value = holdings.stream().map(CelebrityHolding::reportedValue).filter(item -> item != null)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new CelebrityInvestorOverview(investor.slug(), investor.displayName(), investor.managerName(),
        investor.sourceType(), latest.get().sourceUrl(), latest.get().reportDate(), latest.get().filedAt(),
        latest.get().fetchedAt(), disclosureDelay(latest.get().reportDate()), holdings.size(), value);
  }

  private Map<String, Quote> loadQuotes(List<CelebrityHolding> holdings) {
    Map<String, Quote> result = new HashMap<>();
    for (CelebrityHolding holding : holdings) {
      String symbol = symbolKey(holding.symbol());
      if (symbol.isBlank() || result.containsKey(symbol)) {
        continue;
      }
      Instrument instrument = marketData.instrument(symbol);
      Optional<MarketBar> bar = marketBars.latestDailyBar(instrument.id());
      if (bar.isEmpty() || !positive(bar.get().close())) {
        marketData.queueSummaryRefresh(instrument, bar.isEmpty(), true);
        result.put(symbol, new Quote(null, null));
      } else {
        result.put(symbol, new Quote(bar.get().close(), bar.get().barTime()));
      }
    }
    return result;
  }

  private static List<CelebrityHolding> withWeights(List<CelebrityHolding> holdings) {
    BigDecimal total = holdings.stream().map(CelebrityHolding::reportedValue).filter(item -> item != null)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (total.signum() <= 0) {
      return holdings;
    }
    return holdings.stream().map(item -> item.reportedWeight() == null
        ? new CelebrityHolding(item.id(), item.filingId(), item.investorId(), item.holdingKey(), item.symbol(), item.symbolConfidence(),
            item.cusip(), item.issuerName(), item.titleClass(), item.putCall(), item.shares(), item.reportedValue(),
            item.reportedValue().divide(total, 8, RoundingMode.HALF_UP), item.reportedUnitValue())
        : item).toList();
  }

  private static CelebrityHolding holding(
      String investorId,
      String filingId,
      String holdingKey,
      String symbol,
      String symbolConfidence,
      String cusip,
      String issuerName,
      String titleClass,
      String putCall,
      BigDecimal shares,
      BigDecimal reportedValue,
      BigDecimal reportedWeight,
      BigDecimal reportedUnitValue) {
    return new CelebrityHolding(JdbcSupport.id(), filingId, investorId, holdingKey, symbol, symbolConfidence, cusip, issuerName,
        titleClass, putCall, shares, reportedValue, reportedWeight, reportedUnitValue);
  }

  private CelebrityInvestor investor(String slug) {
    return repository.findInvestor(slug).orElseThrow(() -> new IllegalArgumentException("未找到名人投资人：" + slug));
  }

  private static int disclosureDelay(String reportDate) {
    try {
      return Math.max(0, (int) ChronoUnit.DAYS.between(LocalDate.parse(reportDate), LocalDate.now()));
    } catch (RuntimeException error) {
      return 0;
    }
  }

  private static boolean positive(BigDecimal value) {
    return value != null && value.signum() > 0;
  }

  private static String symbolKey(String value) {
    return value == null ? "" : value.trim().toUpperCase();
  }

  private static String friendlyMessage(RuntimeException error) {
    String message = error.getMessage();
    return message == null || message.isBlank() ? "未知错误" : message.replaceAll("[\\r\\n]+", " ");
  }

  public record CelebrityPortfolio(
      CelebrityInvestorOverview investor,
      List<CelebrityHoldingView> holdings,
      String message) {
  }

  private record Quote(BigDecimal price, String updatedAt) {
  }

  private static final class SyncCounters {
    private int investors;
    private int filings;
    private int holdings;
  }
}
