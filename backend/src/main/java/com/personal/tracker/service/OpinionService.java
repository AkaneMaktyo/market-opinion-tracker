package com.personal.tracker.service;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.Opinion;
import com.personal.tracker.domain.PriceLevel;
import com.personal.tracker.domain.Review;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.repository.KolRepository;
import com.personal.tracker.repository.OpinionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OpinionService {
  private final InstrumentRepository instruments;
  private final OpinionRepository opinions;

  public OpinionService(InstrumentRepository instruments, OpinionRepository opinions) {
    this.instruments = instruments;
    this.opinions = opinions;
  }

  public OpinionView create(CreateOpinionCommand command) {
    Instrument instrument = instruments.saveIfAbsent(
        command.symbol(), command.instrumentName(), "US", command.sector());
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

  private static String value(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  public record CreateOpinionCommand(
      String sessionId,
      String symbol,
      String instrumentName,
      String sector,
      String direction,
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
