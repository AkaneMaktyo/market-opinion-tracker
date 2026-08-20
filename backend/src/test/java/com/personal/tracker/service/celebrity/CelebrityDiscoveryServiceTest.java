package com.personal.tracker.service.celebrity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.personal.tracker.domain.celebrity.CelebrityFiling;
import com.personal.tracker.domain.celebrity.CelebrityHolding;
import com.personal.tracker.domain.celebrity.CelebrityInvestor;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.celebrity.CelebrityPortfolioRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CelebrityDiscoveryServiceTest {
  private final CelebrityPortfolioRepository repository = Mockito.mock(CelebrityPortfolioRepository.class);
  private final CelebrityDiscoveryService service = new CelebrityDiscoveryService(
      repository, Mockito.mock(InstrumentRepository.class));

  @Test
  void combinesLatestHoldingsFromDifferentInvestorsBySymbol() {
    CelebrityInvestor first = investor("druckenmiller", "德鲁肯米勒");
    CelebrityInvestor second = investor("cathie-wood", "木头姐");
    when(repository.findEnabledInvestors()).thenReturn(List.of(first, second));
    when(repository.latestFiling(first.id())).thenReturn(Optional.of(filing(first.id(), "f-1")));
    when(repository.latestFiling(second.id())).thenReturn(Optional.of(filing(second.id(), "f-2")));
    when(repository.holdingsForFiling("f-1")).thenReturn(List.of(holding(first.id(), "f-1", "100.00", "0.10")));
    when(repository.holdingsForFiling("f-2")).thenReturn(List.of(holding(second.id(), "f-2", "200.00", "0.20")));

    var result = service.consensus(10);

    assertThat(result).singleElement().satisfies(item -> {
      assertThat(item.symbol()).isEqualTo("TSLA");
      assertThat(item.investorCount()).isEqualTo(2);
      assertThat(item.combinedReportedValue()).isEqualByComparingTo("300.00");
      assertThat(item.holders()).extracting(holder -> holder.investorName())
          .containsExactlyInAnyOrder("德鲁肯米勒", "木头姐");
    });
  }

  private static CelebrityInvestor investor(String slug, String name) {
    return new CelebrityInvestor(slug, slug, name, name + "基金", "SEC_13F", "0000000001",
        "https://example.com/" + slug, true, "2026-01-01T00:00:00Z", "2026-01-01T00:00:00Z");
  }

  private static CelebrityFiling filing(String investorId, String id) {
    return new CelebrityFiling(id, investorId, "SEC_13F", id, "13F-HR", "2026-06-30",
        "2026-08-01T00:00:00Z", "https://example.com/filing/" + id, false, "2026-08-01T00:00:00Z");
  }

  private static CelebrityHolding holding(String investorId, String filingId, String value, String weight) {
    return new CelebrityHolding("h-" + filingId, filingId, investorId, "TSLA|COMMON STOCK", "TSLA", "HIGH",
        "88160R101", "TESLA INC", "COMMON STOCK", null, new BigDecimal("10"), new BigDecimal(value),
        new BigDecimal(weight), new BigDecimal("10"));
  }
}
