package com.personal.tracker.web;

import com.personal.tracker.domain.PriceLevel;
import com.personal.tracker.service.OpinionService;
import com.personal.tracker.service.OpinionService.CreateOpinionCommand;
import com.personal.tracker.service.OpinionService.OpinionView;
import com.personal.tracker.service.OpinionService.ReviewCommand;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/opinions")
public class OpinionController {
  private final OpinionService opinions;

  public OpinionController(OpinionService opinions) {
    this.opinions = opinions;
  }

  @GetMapping
  List<OpinionView> list(
      @RequestParam(required = false) String kolId,
      @RequestParam(required = false) String symbol,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "100") int limit) {
    return opinions.find(kolId, symbol, status, limit);
  }

  @PostMapping
  OpinionView create(@RequestBody CreateOpinionRequest request) {
    return opinions.create(new CreateOpinionCommand(
        request.sessionId(),
        request.symbol(),
        request.instrumentName(),
        request.market(),
        request.sector(),
        request.direction(),
        request.positionAction(),
        request.horizon(),
        request.thesis(),
        request.triggerCondition(),
        request.invalidation(),
        request.confidence(),
        request.sourceQuote(),
        request.referencePrice(),
        request.rawDirection(),
        request.risksText(),
        request.catalystsText(),
        request.priceNotesText(),
        request.rawItemJson(),
        request.opinionTime(),
        request.priceLevels()));
  }

  @PatchMapping("/{id}/review")
  OpinionView review(@PathVariable String id, @RequestBody ReviewRequest request) {
    return opinions.review(id, new ReviewCommand(
        request.outcome(),
        request.notes(),
        request.resultPrice(),
        request.reviewDate()));
  }

  public record CreateOpinionRequest(
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
  }

  public record ReviewRequest(
      String outcome,
      String notes,
      BigDecimal resultPrice,
      String reviewDate) {
  }
}
