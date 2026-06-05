package com.personal.tracker.service;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.LiveSession;
import com.personal.tracker.domain.Opinion;
import com.personal.tracker.domain.PriceLevel;
import com.personal.tracker.domain.Review;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.repository.KolRepository;
import com.personal.tracker.repository.OpinionRepository;
import com.personal.tracker.repository.SessionRepository;
import com.personal.tracker.service.positions.KolPositionService;
import com.personal.tracker.service.resonance.ResonanceService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpinionService {
  private static final Logger log = LoggerFactory.getLogger(OpinionService.class);
  private final InstrumentRepository instruments;
  private final OpinionRepository opinions;
  private final SessionRepository sessions;
  private final KolPositionService positions;
  private final ResonanceService resonance;

  public OpinionService(
      InstrumentRepository instruments,
      OpinionRepository opinions,
      SessionRepository sessions,
      KolPositionService positions,
      ResonanceService resonance) {
    this.instruments = instruments;
    this.opinions = opinions;
    this.sessions = sessions;
    this.positions = positions;
    this.resonance = resonance;
  }

  @Transactional
  public OpinionView create(CreateOpinionCommand command) {
    Instrument instrument = instruments.saveIfAbsent(
        command.symbol(), command.instrumentName(), command.market(), command.sector());
    Opinion saved = opinions.create(new Opinion(
        null,
        command.sessionId(),
        instrument.id(),
        instrument.symbol(),
        value(command.direction(), "WATCH"),
        value(command.horizon(), "短线"),
        value(command.thesis(), "待补充"),
        command.triggerCondition(),
        command.invalidation(),
        command.confidence(),
        command.sourceQuote(),
        command.referencePrice(),
        command.rawDirection(),
        command.risksText(),
        command.catalystsText(),
        command.priceNotesText(),
        command.rawItemJson(),
        value(command.opinionTime(), LocalDateTime.now().toString()),
        "ACTIVE",
        null));
    opinions.replaceLevels(saved.id(), command.priceLevels());
    positions.apply(sessionKol(command.sessionId()), instrument, saved.id(), command.positionAction());
    refreshResonance(saved.symbol());
    return view(saved);
  }

  public List<OpinionView> find(String kolId, String symbol, String status, int limit) {
    String safeKol = kolId == null || kolId.isBlank() ? KolRepository.DEFAULT_ID : kolId;
    return opinions.find(safeKol, symbol, status, limit).stream().map(this::view).toList();
  }

  public OpinionView review(String opinionId, ReviewCommand command) {
    opinions.saveReview(new Review(
        null,
        opinionId,
        value(command.outcome(), "PENDING"),
        command.notes(),
        command.resultPrice(),
        value(command.reviewDate(), LocalDate.now().toString()),
        null));
    Opinion opinion = opinions.findById(opinionId)
        .orElseThrow(() -> new IllegalArgumentException("观点不存在"));
    return view(opinion);
  }

  private OpinionView view(Opinion opinion) {
    return new OpinionView(
        opinion,
        opinions.findLevels(opinion.id()),
        opinions.findReview(opinion.id()).orElse(null));
  }

  private void refreshResonance(String symbol) {
    try {
      resonance.refreshForSymbol(symbol);
    } catch (RuntimeException error) {
      log.warn("刷新共振结果失败: {}", symbol, error);
    }
  }

  private String sessionKol(String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      return KolRepository.DEFAULT_ID;
    }
    return sessions.findById(sessionId).map(LiveSession::kolId).orElse(KolRepository.DEFAULT_ID);
  }

  private static String value(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  public record CreateOpinionCommand(
      String sessionId,
      String symbol,
      String instrumentName,
      String market,
      String sector,
      String direction,
      String positionAction,
      String horizon,
      String thesis,
      String triggerCondition,
      String invalidation,
      Integer confidence,
      String sourceQuote,
      BigDecimal referencePrice,
      String rawDirection,
      String risksText,
      String catalystsText,
      String priceNotesText,
      String rawItemJson,
      String opinionTime,
      List<PriceLevel> priceLevels) {
    public CreateOpinionCommand {
      symbol = JdbcSupport.symbol(symbol);
      market = JdbcSupport.market(market, symbol);
      priceLevels = priceLevels == null ? List.of() : priceLevels;
    }
  }

  public record ReviewCommand(
      String outcome,
      String notes,
      BigDecimal resultPrice,
      String reviewDate) {
  }

  public record OpinionView(
      Opinion opinion,
      List<PriceLevel> priceLevels,
      Review review) {
  }
}
