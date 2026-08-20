package com.personal.tracker.domain.celebrity.discovery;

import java.math.BigDecimal;
import java.util.List;

public final class CelebrityDiscoveryModels {
  private CelebrityDiscoveryModels() {
  }

  public record FeedItem(
      String id,
      String investorSlug,
      String investorName,
      String sourceType,
      String symbol,
      String cusip,
      String issuerName,
      String action,
      BigDecimal sharesDelta,
      BigDecimal sharesChangePercent,
      BigDecimal reportedWeight,
      BigDecimal reportedValue,
      String reportDate,
      String filedAt,
      String sourceUrl) {
  }

  public record Consensus(
      String key,
      String symbol,
      String cusip,
      String issuerName,
      int investorCount,
      BigDecimal combinedReportedValue,
      List<ConsensusHolder> holders) {
  }

  public record ConsensusHolder(
      String investorSlug,
      String investorName,
      String sourceType,
      BigDecimal reportedWeight,
      BigDecimal reportedValue,
      String reportDate) {
  }

  public record InstrumentOwnership(
      String investorSlug,
      String investorName,
      String sourceType,
      String symbol,
      String issuerName,
      BigDecimal shares,
      BigDecimal reportedWeight,
      BigDecimal reportedValue,
      String reportDate,
      String filedAt,
      String sourceUrl) {
  }

  public record WatchlistOverlap(
      String symbol,
      String name,
      Consensus consensus) {
  }
}
