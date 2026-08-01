package com.personal.tracker.service.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.LiveSession;
import com.personal.tracker.domain.Opinion;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.OpinionRepository;
import com.personal.tracker.repository.SessionRepository;
import com.personal.tracker.service.ImportService.ImportCandidate;
import com.personal.tracker.service.OpinionService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpinionImportWriterTest {
  @Test
  void storesCleanOcrTextAsDeterministicMessageOpinion() {
    var sessions = mock(SessionRepository.class);
    var instruments = mock(InstrumentRepository.class);
    var opinions = mock(OpinionRepository.class);
    var writer = new OpinionImportWriter(
        sessions, mock(OpinionService.class), instruments, opinions);
    var session = new LiveSession(
        "session-1", "kol-shun", "2026-07-31", "顺哥", "WXPUSHER_AUTO", "", "now");
    var instrument = new Instrument(
        "inst-qqq", "QQQ", "QQQ", "US", "", "", "", "", "", "", "", "", "");
    var candidate = new ImportCandidate(
        true, "QQQ", "QQQ", "US", "BEARISH", "关键词兜底", "IGNORE", "消息",
        "QQQ 看空 620", "", "", "", "", "", "{}", List.of());
    String rawText = "💎｜顺哥vip小群\n[图片转文字 1]\nQQQ 看空 620\n[/图片转文字]";
    when(sessions.findById("session-1")).thenReturn(Optional.of(session));
    when(instruments.saveIfAbsent("QQQ", "QQQ", "US", null)).thenReturn(instrument);
    when(opinions.upsertMessage(anyString(), any(Opinion.class)))
        .thenAnswer(call -> call.getArgument(1));

    writer.writeMessageFallback(
        "session-1", "message-1", "kol-shun", "顺哥", "2026-07-31",
        rawText, "2026-07-31T15:40:00Z", List.of(candidate));
    writer.writeMessageFallback(
        "session-1", "message-1", "kol-shun", "顺哥", "2026-07-31",
        rawText, "2026-07-31T15:40:00Z", List.of(candidate));

    var idCaptor = ArgumentCaptor.forClass(String.class);
    var opinionCaptor = ArgumentCaptor.forClass(Opinion.class);
    verify(opinions, times(2)).upsertMessage(idCaptor.capture(), opinionCaptor.capture());
    assertEquals(idCaptor.getAllValues().get(0), idCaptor.getAllValues().get(1));
    assertEquals("QQQ 看空 620", opinionCaptor.getValue().sourceQuote());
    assertEquals("MESSAGE", opinionCaptor.getValue().status());
  }
}
