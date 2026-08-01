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
import com.personal.tracker.service.wxpusher.ocr.WxPusherImageOcrService;
import com.personal.tracker.service.wxpusher.ocr.WxPusherOcrOpinionSyncService;
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

  @Test
  void backfillsImportedVipImageTextWithoutDuplicatingOpinions() {
    var fx = fixture();
    String legacyText = "💎｜顺哥vip小群\n[图片]";
    String fetchedText = "💎｜顺哥vip小群\nWXPUSHER_IMAGE_URL=https://img.example/signal.png";
    String ocrText = "💎｜顺哥vip小群\n[图片转文字 1]\nNVDA 看多\n[/图片转文字]";
    WxPusherMessage legacy = new WxPusherMessage(
        "msg-history", "key-history", "kol-shun", "顺哥", "", "顺哥vip小群",
        "https://wxpusher.zjiecode.com/api/message/1", "https://source",
        "2026-07-01T06:00:00Z", "{}", legacyText, "{}", "IMPORTED", "",
        "session-history", "", "");
    WxPusherMessage updated = new WxPusherMessage(
        "msg-history", "key-history", "kol-shun", "顺哥", "", "顺哥vip小群",
        legacy.detailUrl(), legacy.sourceUrl(), legacy.messageTime(), "{}", ocrText, "{}",
        "IMPORTED", "", "session-history", "", "");
    when(fx.messages.listLegacyImageMessages(1000)).thenReturn(List.of(legacy));
    when(fx.imageOcr.requiresSourceRefresh("顺哥", legacyText)).thenReturn(true);
    when(fx.settings.get()).thenReturn(settings());
    when(fx.articles.fetchText(legacy.detailUrl(), settings())).thenReturn(fetchedText);
    when(fx.imageOcr.convert("顺哥", fetchedText)).thenReturn(ocrText);
    when(fx.imageOcr.containsOcrText(ocrText)).thenReturn(true);
    when(fx.sessions.findById("session-history"))
        .thenReturn(java.util.Optional.of(session("session-history", "kol-shun", legacyText)));
    when(fx.messages.findById("msg-history")).thenReturn(java.util.Optional.of(updated));

    var result = fx.service.backfillOcrHistory(1000);

    assertEquals(1, result.eligible());
    assertEquals(1, result.converted());
    assertEquals(1, result.imported());
    assertEquals(0, result.failed());
    verify(fx.sessions).updateRawText("session-history", ocrText);
    verify(fx.messages).updateDetailText("msg-history", ocrText);
    verify(fx.writer).updateSessionSourceQuote("session-history", ocrText);
    verify(fx.writer, never()).write(
        anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyList());
  }

  @Test
  void backfillsFailedOcrAsMessageOpinion() {
    var fx = fixture();
    String ocrText = "💎｜顺哥vip小群\n[图片转文字 1]\nQQQ 看空 620\n[/图片转文字]";
    WxPusherMessage message = new WxPusherMessage(
        "msg-ocr", "key-ocr", "kol-shun", "顺哥", "", "顺哥vip小群",
        "", "", "2026-07-31T15:40:00Z", "{}", ocrText, "", "FAILED",
        "模型没有提取到观点", "session-ocr", "", "");
    when(fx.messages.listOcrMessages(1000)).thenReturn(List.of(message));
    when(fx.imageOcr.containsOcrText(ocrText)).thenReturn(true);
    when(fx.sessions.findById("session-ocr"))
        .thenReturn(java.util.Optional.of(session("session-ocr", "kol-shun", ocrText)));

    var result = fx.service.backfillOcrOpinions(1000);

    assertEquals(1, result.messages());
    assertEquals(1, result.messageOpinions());
    assertEquals(0, result.missingSymbols());
    verify(fx.writer).writeMessageFallback(
        eq("session-ocr"), eq("msg-ocr"), eq("kol-shun"), contains("顺哥"),
        eq("2026-07-31"), eq(ocrText), eq("2026-07-31T15:40:00Z"), anyList());
  }

  private Fixture fixture() {
    var sessions = mock(SessionRepository.class);
    var settings = mock(WxPusherSettingsRepository.class);
    var bloggers = mock(WxPusherBloggerRepository.class);
    var messages = mock(WxPusherMessageRepository.class);
    var shared = mock(WxPusherSharedMessageRepository.class);
    var articles = mock(WxPusherArticleExtractor.class);
    var imageOcr = mock(WxPusherImageOcrService.class);
    var ai = mock(OpenAiJsonExtractor.class);
    var parser = mock(JsonOpinionParser.class);
    var writer = mock(OpinionImportWriter.class);
    var instruments = mock(InstrumentRepository.class);
    when(imageOcr.convert(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
    when(writer.writeMessageFallback(
        anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(new WriteResult("session-ocr", 1));
    var service = new WxPusherIngestionService(
        sessions, settings, bloggers, messages, shared, articles, imageOcr,
        new WxPusherOcrOpinionSyncService(messages, imageOcr, writer),
        ai, parser, writer, instruments);
    return new Fixture(
        service, sessions, settings, bloggers, messages, shared, articles, imageOcr, ai, parser, writer, instruments);
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
      WxPusherImageOcrService imageOcr,
      OpenAiJsonExtractor ai,
      JsonOpinionParser parser,
      OpinionImportWriter writer,
      InstrumentRepository instruments) {
  }
}
