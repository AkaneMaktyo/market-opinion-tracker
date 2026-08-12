package com.personal.tracker.service.alerts.recognition;

import java.math.BigDecimal;
import java.util.List;

public final class PriceAlertRecognitionModels {
  private PriceAlertRecognitionModels() {
  }

  public record Candidate(
      String candidateId,
      String instrumentName,
      String symbol,
      String market,
      String alertType,
      BigDecimal lowerPrice,
      BigDecimal upperPrice,
      BigDecimal targetPrice,
      String triggerDirection,
      String category,
      String note,
      String sourceQuote,
      String source,
      String creationStatus,
      String creationMessage) {
  }

  public record Result(
      String recognitionId,
      String messageId,
      String status,
      List<Candidate> candidates,
      List<String> warnings,
      String errorMessage,
      String updatedAt) {
  }

  public record Summary(String status, int candidateCount, String recognitionId) {
  }
}
