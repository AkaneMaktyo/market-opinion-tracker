package com.personal.tracker.service.positions;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.KolPosition;
import com.personal.tracker.domain.KolPositionTrade;
import com.personal.tracker.domain.MarketBar;
import com.personal.tracker.domain.Opinion;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.KolRepository;
import com.personal.tracker.repository.MarketBarRepository;
import com.personal.tracker.repository.positions.KolPositionRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class KolPositionService {
  private final KolRepository kols;
  private final InstrumentRepository instruments;
  private final KolPositionRepository positions;
  private final PositionActionResolver actionResolver;
  private final MarketBarRepository bars;

  public KolPositionService(
      KolRepository kols,
      InstrumentRepository instruments,
      KolPositionRepository positions,
      PositionActionResolver actionResolver,
      MarketBarRepository bars) {
    this.kols = kols;
    this.instruments = instruments;
    this.positions = positions;
    this.actionResolver = actionResolver;
    this.bars = bars;
  }

  public List<PositionView> list(String kolId, boolean includeClosed) {
    List<KolPosition> stored = positions.list(kols.normalize(kolId), includeClosed);
    Map<String, BigDecimal> lastPrices = new HashMap<>();
    for (KolPosition position : stored) {
      if ("ACTIVE".equals(position.status())
          && !lastPrices.containsKey(position.instrumentId())) {
        BigDecimal close = latestClose(position.instrumentId());
        if (close != null) {
          lastPrices.put(position.instrumentId(), close);
        }
      }
    }
    return stored.stream()
        .map(position -> new PositionView(
            position,
            lastPrices.get(position.instrumentId()),
            floatingPnlPct(position, lastPrices.get(position.instrumentId()))))
        .toList();
  }

  public KolPosition openManual(OpenPositionCommand command) {
    String kolId = kols.normalize(command.kolId());
    if (command.symbol() == null || command.symbol().isBlank()) {
      throw new IllegalArgumentException("请先填写持仓代码");
    }
    Instrument instrument = instruments.saveIfAbsent(
        command.symbol(),
        command.name(),
        command.market(),
        command.sector());
    return positions.open(
        kolId, instrument.id(), "", "MANUAL_OPEN",
        normalizeDirection(command.direction()), command.entryPrice());
  }

  public KolPosition closeManual(String id) {
    KolPosition current = positions.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("持仓不存在"));
    BigDecimal exitPrice = latestClose(current.instrumentId());
    return positions.closeById(id, exitPrice, "手动平仓").orElseThrow();
  }

  public void apply(String kolId, Instrument instrument, Opinion opinion, String actionText) {
    String resolved = actionResolver.normalize(actionText);
    String kolIdNormalized = kols.normalize(kolId);
    if (PositionActionResolver.OPEN.equals(resolved)) {
      positions.open(
          kolIdNormalized,
          instrument.id(),
          opinion.id(),
          resolved,
          normalizeDirection(opinion.direction()),
          opinion.referencePrice());
    }
    if (PositionActionResolver.CLOSE.equals(resolved)) {
      BigDecimal exitPrice = opinion.referencePrice() != null
          ? opinion.referencePrice()
          : latestClose(instrument.id());
      positions.close(
          kolIdNormalized, instrument.id(), opinion.id(), resolved,
          exitPrice, blankToNull(actionText));
    }
  }

  public PositionStatsView stats(String kolId) {
    String normalized = kols.normalize(kolId);
    KolPositionRepository.TradeStats stats = positions.tradeStats(normalized);
    return PositionStatsView.of(
        normalized,
        stats.total(),
        stats.settled(),
        stats.wins(),
        stats.losses(),
        stats.avgPnlPct(),
        stats.bestPnlPct(),
        stats.worstPnlPct(),
        stats.totalPnlPct(),
        positions.countActive(normalized));
  }

  public List<KolPositionTrade> trades(String kolId, int limit) {
    return positions.trades(kols.normalize(kolId), limit);
  }

  private BigDecimal floatingPnlPct(KolPosition position, BigDecimal lastPrice) {
    if (!"ACTIVE".equals(position.status())) {
      return KolPositionTrade.pnlPct(
          position.direction(), position.entryPrice(), position.exitPrice());
    }
    return KolPositionTrade.pnlPct(position.direction(), position.entryPrice(), lastPrice);
  }

  private BigDecimal latestClose(String instrumentId) {
    if (instrumentId == null || instrumentId.isBlank()) {
      return null;
    }
    List<MarketBar> recent = bars.findRecentBars(instrumentId, "1D", 1, null);
    return recent.isEmpty() ? null : recent.get(recent.size() - 1).close();
  }

  private static String normalizeDirection(String direction) {
    return "SHORT".equals(direction) ? "SHORT" : "LONG";
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public record OpenPositionCommand(
      String kolId,
      String symbol,
      String name,
      String market,
      String sector,
      String direction,
      BigDecimal entryPrice) {
  }
}
