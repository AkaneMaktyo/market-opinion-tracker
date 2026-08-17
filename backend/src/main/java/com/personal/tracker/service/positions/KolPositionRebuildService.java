package com.personal.tracker.service.positions;

import com.personal.tracker.domain.KolPositionTrade;
import com.personal.tracker.domain.Opinion;
import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.repository.KolRepository;
import com.personal.tracker.repository.MarketBarRepository;
import com.personal.tracker.repository.OpinionRepository;
import com.personal.tracker.repository.positions.KolPositionRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.WxPusherBlogger;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import static java.util.Locale.ROOT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class KolPositionRebuildService {
  private static final Logger log = LoggerFactory.getLogger(KolPositionRebuildService.class);
  private final KolRepository kols;
  private final OpinionRepository opinions;
  private final KolPositionRepository positions;
  private final MarketBarRepository bars;
  private final PositionActionResolver resolver;
  private final WxPusherBloggerRepository bloggers;

  public KolPositionRebuildService(
      KolRepository kols,
      OpinionRepository opinions,
      KolPositionRepository positions,
      MarketBarRepository bars,
      PositionActionResolver resolver,
      WxPusherBloggerRepository bloggers) {
    this.kols = kols;
    this.opinions = opinions;
    this.positions = positions;
    this.bars = bars;
    this.resolver = resolver;
    this.bloggers = bloggers;
  }

  public RebuildResult rebuild(String kolId, String sourceIncludeOverride) {
    String normalized = kols.normalize(kolId);
    String sourceInclude = resolveSourceInclude(normalized, sourceIncludeOverride);
    List<Opinion> history = opinions.findAllByKol(normalized, sourceInclude);
    positions.deleteTrades(normalized);
    Map<String, OpenTrade> open = new HashMap<>();
    int settled = 0;
    for (Opinion opinion : history) {
      String action = resolver.resolve(
          null,
          opinion.rawDirection(),
          opinion.thesis(),
          opinion.triggerCondition(),
          opinion.risksText(),
          opinion.priceNotesText(),
          opinion.sourceQuote());
      if (PositionActionResolver.OPEN.equals(action)
          && !open.containsKey(opinion.instrumentId())
          && hasMarketData(opinion.instrumentId())
          && mentionsSymbol(opinion)) {
        open.put(opinion.instrumentId(), new OpenTrade(opinion, JdbcSupport.now()));
      } else if (PositionActionResolver.CLOSE.equals(action)
          && open.containsKey(opinion.instrumentId())) {
        OpenTrade trade = open.remove(opinion.instrumentId());
        BigDecimal exitPrice = exitPrice(trade.opinion(), opinion);
        upsert(normalized, trade, opinion, exitPrice, action);
        settled++;
      }
    }
    int removed = positions.deleteAutoActiveNotIn(
        normalized, new ArrayList<>(open.keySet()));
    int running = 0;
    for (OpenTrade trade : open.values()) {
      Opinion entry = trade.opinion();
      upsert(normalized, trade, null, null, "OPEN");
      positions.reopenForRebuild(
          normalized,
          entry.instrumentId(),
          directionOf(entry),
          entryPrice(entry),
          entry.id(),
          entry.opinionTime(),
          "REBUILD_OPEN");
      running++;
    }
    log.info("重建虚拟单子完成 kol={} source={} opinions={} settled={} running={} removed={}",
        normalized, sourceInclude, history.size(), settled, running, removed);
    return new RebuildResult(
        normalized, sourceInclude, history.size(),
        settled + running, settled, running, removed);
  }

  private boolean hasMarketData(String instrumentId) {
    return bars.count(instrumentId, "1D") > 0;
  }

  private boolean mentionsSymbol(Opinion opinion) {
    String symbol = opinion.symbol() == null ? "" : opinion.symbol().trim();
    if (symbol.length() < 2) {
      return false;
    }
    String text = safe(opinion.sourceQuote());
    Pattern pattern = Pattern.compile(
        "(?<![A-Z0-9])" + Pattern.quote(symbol.toUpperCase(ROOT)) + "(?![A-Z0-9])",
        Pattern.CASE_INSENSITIVE);
    return pattern.matcher(text).find();
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }

  private String resolveSourceInclude(String kolId, String override) {
    if (override != null && !override.isBlank()) {
      return override.trim();
    }
    Optional<WxPusherBlogger> blogger = bloggers.findByKolId(kolId);
    return blogger.map(WxPusherBlogger::bloggerName).orElse(null);
  }

  private void upsert(
      String kolId,
      OpenTrade trade,
      Opinion exitOpinion,
      BigDecimal exitPrice,
      String exitReason) {
    String now = JdbcSupport.now();
    Opinion entry = trade.opinion();
    BigDecimal entryPrice = entryPrice(entry);
    positions.upsertTrade(new KolPositionTrade(
        JdbcSupport.id(),
        kolId,
        entry.instrumentId(),
        entry.symbol(),
        directionOf(entry),
        entryPrice,
        entry.opinionTime(),
        entry.id(),
        exitPrice,
        exitOpinion == null ? null : exitOpinion.opinionTime(),
        exitOpinion == null ? null : exitOpinion.id(),
        exitOpinion == null ? null : exitReason,
        KolPositionTrade.pnlPct(directionOf(entry), entryPrice, exitPrice),
        trade.openedAt()), now);
  }

  private BigDecimal entryPrice(Opinion entryOpinion) {
    if (entryOpinion.referencePrice() != null) {
      return entryOpinion.referencePrice();
    }
    return bars.closeAtOrBefore(entryOpinion.instrumentId(), "1D", trimDate(entryOpinion.opinionTime()))
        .orElse(null);
  }

  private BigDecimal exitPrice(Opinion entryOpinion, Opinion exitOpinion) {
    if (exitOpinion.referencePrice() != null) {
      return exitOpinion.referencePrice();
    }
    String date = exitOpinion.opinionTime();
    return bars.closeAtOrAfter(exitOpinion.instrumentId(), "1D", trimDate(date))
        .or(() -> bars.closeAtOrBefore(exitOpinion.instrumentId(), "1D", trimDate(date)))
        .orElse(entryOpinion.referencePrice());
  }

  private static String directionOf(Opinion opinion) {
    return "SHORT".equals(opinion.direction()) ? "SHORT" : "LONG";
  }

  private static String trimDate(String value) {
    return value == null ? null : value.substring(0, Math.min(10, value.length()));
  }

  private record OpenTrade(Opinion opinion, String openedAt) {
  }

  public record RebuildResult(
      String kolId,
      String sourceInclude,
      int scannedOpinions,
      int totalTrades,
      int settledTrades,
      int runningTrades,
      int removedPositions) {
  }
}
