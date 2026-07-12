package com.personal.tracker.service.wxpusher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.domain.LiveSession;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.SessionRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.WxPusherBlogger;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.PendingMessage;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.SaveResult;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.WxPusherMessage;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository.WxPusherSettings;
import com.personal.tracker.repository.wxpusher.WxPusherSharedMessageRepository;
import com.personal.tracker.service.ImportService.ImportCandidate;
import com.personal.tracker.service.ImportService.ImportPreview;
import com.personal.tracker.service.imports.OpinionImportWriter;
import com.personal.tracker.service.imports.OpinionImportWriter.WriteResult;
import com.personal.tracker.service.json.JsonOpinionParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WxPusherIngestionServiceTest {
  @Test
  void skipsMessagesOutsideWhitelist() {
    var fx = fixture();
    when(fx.bloggers.enabled()).thenReturn(List.of(blogger("Alpha")));

    fx.service.ingest(incoming("Beta"));

    verify(fx.messages, never()).createPending(any(PendingMessage.class));
    verify(fx.ai, never()).extract(anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void importsOnlyOnceWhenPollingAndRealtimeDeliverSameMessage() throws Exception {
    var fx = fixture();
    when(fx.settings.get()).thenReturn(settings());
    when(fx.bloggers.enabled()).thenReturn(List.of(blogger("Alpha")));
    when(fx.ai.extractionEnabled()).thenReturn(true);
    when(fx.messages.createPending(any(PendingMessage.class)))
        .thenReturn(new SaveResult(message("msg-1"), true), new SaveResult(importedMessage(), false));
    when(fx.sessions.create(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(session("session-1", "kol-1", "detail"));
    when(fx.articles.fetchText(anyString(), any())).thenReturn("detail");
    when(fx.ai.extract(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn("{\"ok\":true}");
    when(fx.parser.parse("{\"ok\":true}")).thenReturn(preview("BTC"));
    when(fx.writer.write(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(new WriteResult("session-1", 1));

    fx.service.ingest(incoming("Alpha"));
    fx.service.ingest(incoming("Alpha"));

    verify(fx.writer, times(1)).write(
        eq("session-1"), eq("kol-1"), contains("WxPusher / Alpha / "),
        eq("2026-05-31"), eq("WXPUSHER_AUTO"), eq("detail"), anyList());
    verify(fx.messages).markImported("msg-1", "detail", "{\"ok\":true}", "session-1");
  }

  @Test
  void createsSessionBeforeMarkingFailed() throws Exception {
    var fx = fixture();
    when(fx.settings.get()).thenReturn(settings());
    when(fx.bloggers.enabled()).thenReturn(List.of(blogger("Alpha")));
    when(fx.ai.extractionEnabled()).thenReturn(true);
    when(fx.messages.createPending(any(PendingMessage.class)))
        .thenReturn(new SaveResult(message("msg-2"), true));
    when(fx.sessions.create(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(session("session-failed", "kol-1", "title\nsummary\nhttps://source"));
    when(fx.articles.fetchText(anyString(), any())).thenThrow(new IllegalStateException("detail failed"));
    when(fx.ai.extract(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn("{\"empty\":true}");
    when(fx.parser.parse("{\"empty\":true}"))
        .thenReturn(new ImportPreview(List.of(), List.of(), List.of(), List.of()));

    fx.service.ingest(incoming("Alpha"));

    verify(fx.messages).attachSession("msg-2", "session-failed");
    verify(fx.messages).markFailed(
        eq("msg-2"), eq("title\nsummary\nhttps://source"), eq("{\"empty\":true}"), anyString());
    verify(fx.writer, never()).write(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyList());
  }

  @Test
  void hydratesMissingSessionsWithoutRetryingImport() {
    var fx = fixture();
    when(fx.messages.listMissingSessions(10)).thenReturn(List.of(message("msg-3")));
    when(fx.sessions.create(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(session("session-3", "kol-1", "title\nsummary\nhttps://source"));

    fx.service.ensureMessageSessions(10);

    verify(fx.messages).attachSession("msg-3", "session-3");
    verify(fx.ai, never()).extract(anyString(), anyString(), anyString(), anyString(), anyString());
    verify(fx.writer, never()).write(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyList());
  }

  @Test
  void marksMessageSkippedWhenDisabledLlmHasNoKeywordFallback() throws Exception {
    var fx = fixture();
    when(fx.settings.get()).thenReturn(settings());
    when(fx.bloggers.enabled()).thenReturn(List.of(blogger("Alpha")));
    when(fx.ai.extractionEnabled()).thenReturn(false);
    when(fx.messages.createPending(any(PendingMessage.class)))
        .thenReturn(new SaveResult(message("msg-skip"), true));
    when(fx.sessions.create(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(session("session-skip", "kol-1", "plain text"));
    when(fx.articles.fetchText(anyString(), any())).thenReturn("plain text");

    fx.service.ingest(incoming("Alpha"));

    verify(fx.messages).markSkipped(eq("msg-skip"), eq("plain text"), contains("LLM"));
    verify(fx.ai, never()).extract(anyString(), anyString(), anyString(), anyString(), anyString());
    verify(fx.writer, never()).write(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyList());
  }

  @Test
  void importsKeywordOpinionsWhenWxpusherLlmIsDisabled() throws Exception {
    var fx = fixture();
    when(fx.settings.get()).thenReturn(settings());
    when(fx.bloggers.enabled()).thenReturn(List.of(blogger("Alpha")));
    when(fx.ai.extractionEnabled()).thenReturn(false);
    when(fx.messages.createPending(any(PendingMessage.class)))
        .thenReturn(new SaveResult(message("msg-symbols"), true));
    when(fx.sessions.create(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(session("session-symbols", "kol-1", ""));
    when(fx.articles.fetchText(anyString(), any())).thenReturn("NVDA BUY NOW and AMZN BUY NOW");
    when(fx.writer.write(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(new WriteResult("session-symbols", 2));

    fx.service.ingest(incoming("Alpha"));

    verify(fx.instruments).saveIfAbsent("NVDA", "NVDA", "US", null);
    verify(fx.instruments).saveIfAbsent("AMZN", "AMZN", "US", null);
    ArgumentCaptor<List<ImportCandidate>> candidates = ArgumentCaptor.forClass(List.class);
    verify(fx.writer).write(
        eq("session-symbols"), eq("kol-1"), contains("WxPusher / Alpha / "),
        eq("2026-05-31"), eq("WXPUSHER_KEYWORD_FALLBACK"),
        eq("NVDA BUY NOW and AMZN BUY NOW"), candidates.capture());
    assertEquals(List.of("NVDA", "AMZN"), candidates.getValue().stream().map(ImportCandidate::symbol).toList());
    verify(fx.messages).markImported(
        eq("msg-symbols"), eq("NVDA BUY NOW and AMZN BUY NOW"), contains("keyword"), eq("session-symbols"));
  }

  private Fixture fixture() {
    var sessions = mock(SessionRepository.class);
    var settings = mock(WxPusherSettingsRepository.class);
    var bloggers = mock(WxPusherBloggerRepository.class);
    var messages = mock(WxPusherMessageRepository.class);
    var shared = mock(WxPusherSharedMessageRepository.class);
    var articles = mock(WxPusherArticleExtractor.class);
    var ai = mock(OpenAiJsonExtractor.class);
    var parser = mock(JsonOpinionParser.class);
    var writer = mock(OpinionImportWriter.class);
    var instruments = mock(InstrumentRepository.class);
    var service = new WxPusherIngestionService(
        sessions, settings, bloggers, messages, shared, articles, ai, parser, writer, instruments);
    return new Fixture(service, sessions, settings, bloggers, messages, shared, articles, ai, parser, writer, instruments);
  }

  private ImportPreview preview(String symbol) {
    return new ImportPreview(List.of(), List.of(), List.of(new ImportCandidate(
        true, symbol, symbol, "CRYPTO", "BULLISH", "bullish", "OPEN", "short",
        "thesis", "", "", "", "", "quote", "{\"symbol\":\"%s\"}".formatted(symbol), List.of())), List.of());
  }

  private LiveSession session(String id, String kolId, String rawText) {
    return new LiveSession(id, kolId, "2026-05-31", "title", "WXPUSHER_AUTO", rawText, "now");
  }

  private WxPusherSettings settings() {
    return new WxPusherSettings(
        "default", "device-token", "", "", "Chrome-Windows", "1.1.1", 60, true, false, "", "", "", "", "");
  }

  private WxPusherBlogger blogger(String name) {
    return new WxPusherBlogger("blogger-" + name, "kol-1", name, List.of("Alpha VIP"), true, "LAST_30", null, "", "");
  }

  private WxPusherClient.IncomingMessage incoming(String bloggerName) {
    return new WxPusherClient.IncomingMessage(
        "polling", "wxpusher:src:https://source", bloggerName, "title", "summary",
        "https://wxpusher.zjiecode.com/api/message/1", "https://source",
        "2026-05-31T06:00:00Z", "123", "{\"id\":1}", 1L);
  }

  private WxPusherMessage message(String id) {
    return new WxPusherMessage(
        id, "wxpusher:src:https://source", "kol-1", "Alpha", "title", "summary",
        "https://wxpusher.zjiecode.com/api/message/1", "https://source",
        "2026-05-31T06:00:00Z", "{\"id\":1}", "", "", "PENDING", "", "", "", "");
  }

  private WxPusherMessage importedMessage() {
    return new WxPusherMessage(
        "msg-1", "wxpusher:src:https://source", "kol-1", "Alpha", "title", "summary",
        "https://wxpusher.zjiecode.com/api/message/1", "https://source",
        "2026-05-31T06:00:00Z", "{\"id\":1}", "detail", "{\"ok\":true}",
        "IMPORTED", "", "session-1", "", "");
  }

  private record Fixture(
      WxPusherIngestionService service,
      SessionRepository sessions,
      WxPusherSettingsRepository settings,
      WxPusherBloggerRepository bloggers,
      WxPusherMessageRepository messages,
      WxPusherSharedMessageRepository shared,
      WxPusherArticleExtractor articles,
      OpenAiJsonExtractor ai,
      JsonOpinionParser parser,
      OpinionImportWriter writer,
      InstrumentRepository instruments) {
  }
}
