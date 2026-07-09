package com.personal.tracker.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.LiveSession;
import com.personal.tracker.domain.Opinion;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.OpinionRepository;
import com.personal.tracker.repository.SessionRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.WxPusherBlogger;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.WxPusherMessage;
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
    var bloggers = mock(WxPusherBloggerRepository.class);
    var messages = mock(WxPusherMessageRepository.class);
    var positions = mock(KolPositionService.class);
    var resonance = mock(ResonanceService.class);
    var service = new OpinionService(instruments, opinions, sessions, bloggers, messages, positions, resonance);
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

  @Test
  void includesMatchingKolMessagesAsOpinionHints() {
    var instruments = mock(InstrumentRepository.class);
    var opinions = mock(OpinionRepository.class);
    var sessions = mock(SessionRepository.class);
    var bloggers = mock(WxPusherBloggerRepository.class);
    var messages = mock(WxPusherMessageRepository.class);
    var positions = mock(KolPositionService.class);
    var resonance = mock(ResonanceService.class);
    var service = new OpinionService(instruments, opinions, sessions, bloggers, messages, positions, resonance);
    var instrument = new Instrument(
        "inst-1", "NVDA", "NVIDIA", "US", "", "", "", "",
        "", "", "", "", "");
    var message = new WxPusherMessage(
        "msg-1", "key-1", "kol-1", "顺哥", "标题",
        "NVDA 突破关键位置", "", "", "2026-07-06T10:00:00Z",
        "", "NVDA 突破关键位置，可以继续观察。", "", "SKIPPED", "",
        "session-1", "now", "now");
    when(opinions.find("kol-1", "NVDA", null, 100)).thenReturn(List.of());
    when(instruments.findBySymbol("NVDA")).thenReturn(Optional.of(instrument));
    when(bloggers.enabled()).thenReturn(List.of(blogger("kol-1", "顺哥", List.of("顺哥"))));
    when(messages.findByKolSince(anyString(), anyString(), anyInt())).thenReturn(List.of(message));

    var result = service.find("kol-1", "NVDA", null, 100);

    assertEquals(1, result.size());
    assertEquals("MESSAGE", result.get(0).opinion().status());
    assertEquals("NVDA", result.get(0).opinion().symbol());
    verify(opinions).find("kol-1", "NVDA", null, 100);
    verify(messages).findByKolSince(eq("kol-1"), anyString(), anyInt());
  }

  @Test
  void hidesMessageHintsFromUnconfiguredNestedSource() {
    var instruments = mock(InstrumentRepository.class);
    var opinions = mock(OpinionRepository.class);
    var sessions = mock(SessionRepository.class);
    var bloggers = mock(WxPusherBloggerRepository.class);
    var messages = mock(WxPusherMessageRepository.class);
    var positions = mock(KolPositionService.class);
    var resonance = mock(ResonanceService.class);
    var service = new OpinionService(instruments, opinions, sessions, bloggers, messages, positions, resonance);
    var instrument = new Instrument(
        "inst-1", "GOLD", "Gold", "US", "", "", "", "",
        "", "", "", "", "");
    var message = new WxPusherMessage(
        "msg-gold", "key-gold", "kol-1", "顺哥", "标题",
        "GOLD SELL NOW", "", "", "2026-07-08T14:00:00Z",
        "", "✨黄金帝国-PREMIUM-CIRCLE-⭕\nGOLD SELL NOW @ 4081", "", "SKIPPED", "",
        "session-1", "now", "now");
    when(opinions.find("kol-1", "GOLD", null, 100)).thenReturn(List.of());
    when(instruments.findBySymbol("GOLD")).thenReturn(Optional.of(instrument));
    when(bloggers.enabled()).thenReturn(List.of(blogger("kol-1", "顺哥", List.of("CIA-信息推送", "顺哥"))));
    when(messages.findByKolSince(anyString(), anyString(), anyInt())).thenReturn(List.of(message));

    var result = service.find("kol-1", "GOLD", null, 100);

    assertEquals(0, result.size());
  }

  private WxPusherBlogger blogger(String kolId, String name, List<String> aliases) {
    return new WxPusherBlogger("blogger-" + kolId, kolId, name, aliases, true, "LAST_30", null, "", "");
  }
}
