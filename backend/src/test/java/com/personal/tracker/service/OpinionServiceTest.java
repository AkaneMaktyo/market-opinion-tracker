package com.personal.tracker.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.LiveSession;
import com.personal.tracker.domain.Opinion;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.OpinionRepository;
import com.personal.tracker.repository.SessionRepository;
import com.personal.tracker.service.positions.KolPositionService;
import com.personal.tracker.service.resonance.ResonanceService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OpinionServiceTest {
  @Test
  void appliesPositionActionAfterSavingOpinion() {
    var instruments = mock(InstrumentRepository.class);
    var opinions = mock(OpinionRepository.class);
    var sessions = mock(SessionRepository.class);
    var positions = mock(KolPositionService.class);
    var resonance = mock(ResonanceService.class);
    var service = new OpinionService(instruments, opinions, sessions, positions, resonance);
    var instrument = new Instrument(
        "inst-1", "NVDA", "NVIDIA", "US", "", "", "", "",
        "", "", "", "", "");
    var saved = new Opinion(
        "opinion-1", "session-1", "inst-1", "NVDA", "BULLISH", "短线",
        "买入观察仓", "", "", null, "", null, "看多", "", "",
        "", "{}", "2026-06-06T09:30:00", "ACTIVE", "now");
    when(instruments.saveIfAbsent("NVDA", "NVIDIA", "US", null)).thenReturn(instrument);
    when(opinions.create(any(Opinion.class))).thenReturn(saved);
    when(sessions.findById("session-1")).thenReturn(Optional.of(new LiveSession(
        "session-1", "kol-1", "2026-06-06", "直播", "TEST", "", "now")));
    when(opinions.findLevels("opinion-1")).thenReturn(List.of());
    when(opinions.findReview("opinion-1")).thenReturn(Optional.empty());

    service.create(new OpinionService.CreateOpinionCommand(
        "session-1", "NVDA", "NVIDIA", "US", null, "BULLISH", "OPEN",
        "短线", "买入观察仓", "", "", null, "", null, "看多", "",
        "", "", "{}", "2026-06-06T09:30:00", List.of()));

    verify(positions).apply("kol-1", instrument, "opinion-1", "OPEN");
  }
}
