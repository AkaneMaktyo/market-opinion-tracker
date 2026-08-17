package com.personal.tracker.service.positions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.Opinion;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.KolRepository;
import com.personal.tracker.repository.MarketBarRepository;
import com.personal.tracker.repository.positions.KolPositionRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class KolPositionServiceTest {
  private final KolRepository kols = mock(KolRepository.class);
  private final InstrumentRepository instruments = mock(InstrumentRepository.class);
  private final KolPositionRepository positions = mock(KolPositionRepository.class);
  private final MarketBarRepository bars = mock(MarketBarRepository.class);
  private final KolPositionService service = new KolPositionService(
      kols, instruments, positions, new PositionActionResolver(), bars);

  private final Instrument instrument = new Instrument(
      "inst-1", "NVDA", "NVIDIA", "US", "", "", "", "", "", "", "", "", "");

  @Test
  void closeActionSettlesWithOpinionPrice() {
    when(kols.normalize("kol-1")).thenReturn("kol-1");
    Opinion opinion = opinion("opinion-1", "LONG", "830.50");

    service.apply("kol-1", instrument, opinion, "CLOSE");

    verify(positions).close(
        eq("kol-1"), eq("inst-1"), eq("opinion-1"), eq("CLOSE"),
        eq(new BigDecimal("830.50")), eq("CLOSE"));
    verify(positions, never()).open(any(), any(), any(), any(), any(), any());
  }

  @Test
  void closeFallsBackToLatestBarWhenOpinionPriceMissing() {
    when(kols.normalize("kol-1")).thenReturn("kol-1");
    when(bars.findRecentBars("inst-1", "1D", 1, null)).thenReturn(java.util.List.of(
        new com.personal.tracker.domain.MarketBar(
            "bar-1", "inst-1", "1D", "2026-08-15", null, null, null,
            new BigDecimal("120.25"), null)));
    Opinion opinion = opinion("opinion-2", "LONG", null);

    service.apply("kol-1", instrument, opinion, "止盈");

    verify(positions).close(
        eq("kol-1"), eq("inst-1"), eq("opinion-2"), eq("CLOSE"),
        eq(new BigDecimal("120.25")), eq("止盈"));
  }

  @Test
  void openActionWritesDirectionAndEntryPrice() {
    when(kols.normalize("kol-1")).thenReturn("kol-1");
    Opinion opinion = opinion("opinion-3", "SHORT", "61.80");

    service.apply("kol-1", instrument, opinion, "买入");

    verify(positions).open(
        "kol-1", "inst-1", "opinion-3", "OPEN", "SHORT", new BigDecimal("61.80"));
  }

  @Test
  void longDirectionIsDefaultForWatchOpinions() {
    when(kols.normalize("kol-1")).thenReturn("kol-1");
    Opinion opinion = opinion("opinion-4", "WATCH", "9.10");

    service.apply("kol-1", instrument, opinion, "开仓");

    verify(positions).open(
        "kol-1", "inst-1", "opinion-4", "OPEN", "LONG", new BigDecimal("9.10"));
  }

  @Test
  void closeWithoutAnyPricePassesNullExitPrice() {
    when(kols.normalize("kol-1")).thenReturn("kol-1");
    when(bars.findRecentBars(eq("inst-1"), eq("1D"), eq(1), isNull()))
        .thenReturn(java.util.List.of());
    Opinion opinion = opinion("opinion-5", "LONG", null);

    service.apply("kol-1", instrument, opinion, "清仓");

    verify(positions).close(
        eq("kol-1"), eq("inst-1"), eq("opinion-5"), eq("CLOSE"),
        isNull(), eq("清仓"));
  }

  private static Opinion opinion(String id, String direction, String referencePrice) {
    return new Opinion(
        id, "session-1", "inst-1", "NVDA", direction, "短线", "测试",
        "", "", null, "", referencePrice == null ? null : new BigDecimal(referencePrice),
        "", "", "", "", "", "2026-08-15T10:00:00", "ACTIVE", null);
  }
}
