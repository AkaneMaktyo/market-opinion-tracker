package com.personal.tracker.service.positions;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.KolRepository;
import com.personal.tracker.repository.positions.KolPositionRepository;
import org.junit.jupiter.api.Test;

class KolPositionServiceTest {
  @Test
  void closeActionOnlyMarksPositionClosed() {
    var kols = mock(KolRepository.class);
    var instruments = mock(InstrumentRepository.class);
    var positions = mock(KolPositionRepository.class);
    var resolver = new PositionActionResolver();
    var service = new KolPositionService(kols, instruments, positions, resolver);
    var instrument = new Instrument(
        "inst-1", "NVDA", "NVIDIA", "US", "", "", "", "",
        "", "", "", "", "");
    when(kols.normalize("kol-1")).thenReturn("kol-1");

    service.apply("kol-1", instrument, "opinion-1", "CLOSE");

    verify(positions).close("kol-1", "inst-1", "opinion-1", "CLOSE");
    verify(positions, never()).open("kol-1", "inst-1", "opinion-1", "CLOSE");
    verify(instruments, never()).merge("inst-1", "inst-1");
  }
}
