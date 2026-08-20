package com.personal.tracker.service.celebrity;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.celebrity.CelebrityFiling;
import com.personal.tracker.domain.celebrity.CelebrityHolding;
import com.personal.tracker.domain.celebrity.CelebrityHoldingChange;
import com.personal.tracker.domain.celebrity.CelebrityInvestor;
import com.personal.tracker.domain.celebrity.discovery.CelebrityDiscoveryModels.Consensus;
import com.personal.tracker.domain.celebrity.discovery.CelebrityDiscoveryModels.ConsensusHolder;
import com.personal.tracker.domain.celebrity.discovery.CelebrityDiscoveryModels.FeedItem;
import com.personal.tracker.domain.celebrity.discovery.CelebrityDiscoveryModels.InstrumentOwnership;
import com.personal.tracker.domain.celebrity.discovery.CelebrityDiscoveryModels.WatchlistOverlap;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.celebrity.CelebrityPortfolioRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class CelebrityDiscoveryService {
  private static final int MAX_ITEMS = 100;
  private final CelebrityPortfolioRepository repository;
  private final InstrumentRepository instruments;

  public CelebrityDiscoveryService(
      CelebrityPortfolioRepository repository,
      InstrumentRepository instruments) {
    this.repository = repository;
    this.instruments = instruments;
  }

  public List<CelebrityHoldingChange> changes(String slug) {
    CelebrityInvestor investor = investor(slug);
    List<CelebrityFiling> filings = repository.recentFilings(investor.id(), 2);
    if (filings.size() < 2) {
      return List.of();
    }
    CelebrityFiling current = filings.get(0);
    CelebrityFiling previous = filings.get(1);
    Map<String, CelebrityHolding> currentByKey = byKey(repository.holdingsForFiling(current.id()));
    Map<String, CelebrityHolding> previousByKey = byKey(repository.holdingsForFiling(previous.id()));
    Map<String, CelebrityHoldingChange> result = new LinkedHashMap<>();
    currentByKey.forEach((key, holding) -> {
      CelebrityHolding before = previousByKey.get(key);
      if (before == null) {
        result.put(key, change(holding, null, "NEW", current));
        return;
      }
      int direction = holding.shares().compareTo(before.shares());
      if (direction > 0) {
        result.put(key, change(holding, before, "ADDED", current));
      } else if (direction < 0) {
        result.put(key, change(holding, before, "REDUCED", current));
      }
    });
    previousByKey.forEach((key, holding) -> {
      if (!currentByKey.containsKey(key)) {
        result.put(key, change(null, holding, "EXITED", current));
      }
    });
    return result.values().stream().sorted(changeOrder()).toList();
  }

  public List<FeedItem> feed(int requestedLimit) {
    List<FeedItem> items = new ArrayList<>();
    for (CelebrityInvestor investor : repository.findEnabledInvestors()) {
      for (CelebrityHoldingChange change : changes(investor.slug())) {
        items.add(new FeedItem(
            investor.slug() + ":" + change.holdingKey() + ":" + change.action() + ":" + change.reportDate(),
            investor.slug(), investor.displayName(), investor.sourceType(), change.symbol(), null,
            change.issuerName(), change.action(), change.sharesDelta(), change.sharesChangePercent(),
            change.reportedWeight(), change.reportedValue(), change.reportDate(), change.filedAt(), change.sourceUrl()));
      }
    }
    return items.stream().sorted(Comparator.comparing(FeedItem::reportDate, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(FeedItem::reportedValue, Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(limit(requestedLimit)).toList();
  }

  public List<Consensus> consensus(int requestedLimit) {
    return ownershipGroups().values().stream().map(this::toConsensus)
        .filter(item -> item.investorCount() >= 2)
        .sorted(Comparator.comparing(Consensus::investorCount).reversed()
            .thenComparing(Consensus::combinedReportedValue, Comparator.reverseOrder()))
        .limit(limit(requestedLimit)).toList();
  }

  public List<InstrumentOwnership> ownership(String requestedSymbol) {
    String symbol = symbol(requestedSymbol);
    if (symbol.isBlank()) {
      return List.of();
    }
    return ownershipGroups().values().stream()
        .flatMap(List::stream)
        .filter(item -> symbol.equals(symbol(item.holding().symbol())))
        .map(this::toOwnership)
        .sorted(Comparator.comparing(InstrumentOwnership::reportedValue, Comparator.nullsLast(Comparator.reverseOrder())))
        .toList();
  }

  public List<WatchlistOverlap> watchlistOverlap(String kolId, int requestedLimit) {
    List<Instrument> automaticItems = instruments.findOpinionHistoryByKol(kolId, null);
    List<Instrument> watchlist = instruments.applyWatchlist(kolId, automaticItems);
    Map<String, List<OwnershipRecord>> groups = ownershipGroups();
    List<WatchlistOverlap> result = new ArrayList<>();
    for (Instrument instrument : watchlist) {
      List<OwnershipRecord> owners = groups.get("S:" + symbol(instrument.symbol()));
      if (owners != null && !owners.isEmpty()) {
        result.add(new WatchlistOverlap(instrument.symbol(), instrument.name(), toConsensus(owners)));
      }
    }
    return result.stream().sorted(Comparator.comparing(
        item -> item.consensus().combinedReportedValue(), Comparator.reverseOrder()))
        .limit(limit(requestedLimit)).toList();
  }

  private Map<String, List<OwnershipRecord>> ownershipGroups() {
    Map<String, List<OwnershipRecord>> groups = new LinkedHashMap<>();
    for (CelebrityInvestor investor : repository.findEnabledInvestors()) {
      repository.latestFiling(investor.id()).ifPresent(filing -> repository.holdingsForFiling(filing.id()).forEach(holding -> {
        String key = holdingKey(holding);
        if (!key.isBlank()) {
          groups.computeIfAbsent(key, ignored -> new ArrayList<>())
              .add(new OwnershipRecord(investor, filing, holding));
        }
      }));
    }
    return groups;
  }

  private Consensus toConsensus(List<OwnershipRecord> records) {
    OwnershipRecord lead = records.get(0);
    BigDecimal value = records.stream().map(item -> zero(item.holding().reportedValue()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    List<ConsensusHolder> holders = records.stream()
        .sorted(Comparator.comparing((OwnershipRecord item) -> item.holding().reportedValue(),
            Comparator.nullsLast(Comparator.reverseOrder())))
        .map(item -> new ConsensusHolder(item.investor().slug(), item.investor().displayName(),
            item.investor().sourceType(), item.holding().reportedWeight(), item.holding().reportedValue(),
            item.filing().reportDate()))
        .toList();
    return new Consensus(holdingKey(lead.holding()), lead.holding().symbol(), lead.holding().cusip(),
        lead.holding().issuerName(), holders.size(), value, holders);
  }

  private InstrumentOwnership toOwnership(OwnershipRecord record) {
    CelebrityHolding holding = record.holding();
    return new InstrumentOwnership(record.investor().slug(), record.investor().displayName(),
        record.investor().sourceType(), holding.symbol(), holding.issuerName(), holding.shares(),
        holding.reportedWeight(), holding.reportedValue(), record.filing().reportDate(), record.filing().filedAt(),
        record.filing().sourceUrl());
  }

  private CelebrityInvestor investor(String slug) {
    return repository.findInvestor(slug).orElseThrow(() -> new IllegalArgumentException("未找到名人投资人：" + slug));
  }

  private static Map<String, CelebrityHolding> byKey(List<CelebrityHolding> holdings) {
    Map<String, CelebrityHolding> result = new HashMap<>();
    holdings.forEach(item -> result.put(item.holdingKey(), item));
    return result;
  }

  private static CelebrityHoldingChange change(
      CelebrityHolding current,
      CelebrityHolding previous,
      String action,
      CelebrityFiling filing) {
    CelebrityHolding source = current == null ? previous : current;
    BigDecimal currentShares = current == null ? BigDecimal.ZERO : current.shares();
    BigDecimal previousShares = previous == null ? BigDecimal.ZERO : previous.shares();
    BigDecimal delta = currentShares.subtract(previousShares);
    BigDecimal percent = previousShares.signum() == 0 ? null
        : delta.divide(previousShares, 8, RoundingMode.HALF_UP).movePointRight(2).setScale(2, RoundingMode.HALF_UP);
    return new CelebrityHoldingChange(source.holdingKey(), source.symbol(), source.issuerName(), action,
        currentShares, previousShares, delta, percent,
        current == null ? BigDecimal.ZERO : current.reportedValue(),
        current == null ? BigDecimal.ZERO : current.reportedWeight(), filing.reportDate(), filing.filedAt(),
        filing.sourceUrl());
  }

  private static Comparator<CelebrityHoldingChange> changeOrder() {
    return Comparator.comparing(CelebrityHoldingChange::action)
        .thenComparing(item -> zero(item.reportedValue()), Comparator.reverseOrder());
  }

  private static String holdingKey(CelebrityHolding holding) {
    String symbol = symbol(holding.symbol());
    if (!symbol.isBlank()) {
      return "S:" + symbol;
    }
    String cusip = symbol(holding.cusip());
    return cusip.isBlank() ? "" : "C:" + cusip;
  }

  private static String symbol(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private static BigDecimal zero(BigDecimal value) {
    return Objects.requireNonNullElse(value, BigDecimal.ZERO);
  }

  private static int limit(int requestedLimit) {
    return Math.max(1, Math.min(requestedLimit, MAX_ITEMS));
  }

  private record OwnershipRecord(CelebrityInvestor investor, CelebrityFiling filing, CelebrityHolding holding) {
  }
}
