package com.personal.tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.config.MarketDataProperties;
import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.MarketBar;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.MarketBarRepository;
import com.personal.tracker.repository.MarketBarRepository.BarCoverage;
import com.personal.tracker.service.market.MarketBarProvider;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketDataServiceTest {
  @Test
  void deepBackfillStartsBeforeExistingOldestBar() {
    var instruments = mock(InstrumentRepository.class);
    var bars = mock(MarketBarRepository.class);
    var provider = mock(MarketBarProvider.class);
    when(provider.name()).thenReturn("bitget");
    var service = new MarketDataService(
        instruments, bars, List.of(provider), new MarketDataProperties());
    var instrument = new Instrument(
        "inst-1", "NOK", "Nokia", "US", null, null, null, null,
        null, null, null, null, null);
    long cursor = LocalDate.parse("2026-07-01")
        .atStartOfDay()
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli() - 1;
    when(bars.coverage("inst-1", "1D")).thenReturn(new BarCoverage(10, "2026-07-01", "2026-07-05"));
    when(provider.fetch(eq(instrument), eq("1D"), isNull(), eq(cursor), eq(1000)))
        .thenReturn(List.of());

    MarketDataService.BackfillResult result = service.deepBackfillBars(instrument, "1D");

    assertEquals(0, result.fetched());
    verify(provider).fetch(eq(instrument), eq("1D"), isNull(), eq(cursor), eq(1000));
    verify(bars, never()).saveAll(org.mockito.ArgumentMatchers.<List<MarketBar>>any());
  }
}
