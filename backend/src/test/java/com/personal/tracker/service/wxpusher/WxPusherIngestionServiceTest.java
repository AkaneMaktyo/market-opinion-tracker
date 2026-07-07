package com.personal.tracker.service.wxpusher;

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

class WxPusherIngestionServiceTest {
  @Test
  void skipsMessagesOutsideWhitelist() {
    var sessionRepository = mock(SessionRepository.class);
    var settingsRepository = mock(WxPusherSettingsRepository.class);
    var bloggerRepository = mock(WxPusherBloggerRepository.class);
    var messageRepository = mock(WxPusherMessageRepository.class);
    var sharedRepository = mock(WxPusherSharedMessageRepository.class);
    var articleExtractor = mock(WxPusherArticleExtractor.class);
    var aiExtractor = mock(OpenAiJsonExtractor.class);
    var parser = mock(JsonOpinionParser.class);
    var writer = mock(OpinionImportWriter.class);
    var service = service(
        sessionRepository,
        settingsRepository,
        bloggerRepository,
        messageRepository,
        sharedRepository,
        articleExtractor,
        aiExtractor,
        parser,
        writer);
    when(bloggerRepository.enabled()).thenReturn(List.of(blogger("Alpha")));

    service.ingest(incoming("Beta"));

    verify(messageRepository, never()).createPending(any(PendingMessage.class));
    verify(aiExtractor, never()).extract(anyString(), anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void importsOnlyOnceWhenPollingAndRealtimeDeliverSameMessage() throws Exception {
    var sessionRepository = mock(SessionRepository.class);
    var settingsRepository = mock(WxPusherSettingsRepository.class);
    var bloggerRepository = mock(WxPusherBloggerRepository.class);
    var messageRepository = mock(WxPusherMessageRepository.class);
    var sharedRepository = mock(WxPusherSharedMessageRepository.class);
    var articleExtractor = mock(WxPusherArticleExtractor.class);
    var aiExtractor = mock(OpenAiJsonExtractor.class);
    var parser = mock(JsonOpinionParser.class);
    var writer = mock(OpinionImportWriter.class);
    var service = service(
        sessionRepository,
        settingsRepository,
        bloggerRepository,
        messageRepository,
        sharedRepository,
        articleExtractor,
        aiExtractor,
        parser,
        writer);
    var preview = new ImportPreview(
        List.of(),
        List.of(),
        List.of(new ImportCandidate(
            true,
            "BTC",
            "比特币",
            "CRYPTO",
            "BULLISH",
            "看多",
            "OPEN",
            "短线",
            "继续看强",
            "",
            "",
            "",
            "70000",
            "原文",
            "{\"symbol\":\"BTC\"}",
            List.of())),
        List.of());
    when(settingsRepository.get()).thenReturn(settings());
    when(bloggerRepository.enabled()).thenReturn(List.of(blogger("Alpha")));
    when(aiExtractor.extractionEnabled()).thenReturn(true);
    when(messageRepository.createPending(any(PendingMessage.class)))
        .thenReturn(new SaveResult(message("msg-1"), true), new SaveResult(importedMessage(), false));
    when(sessionRepository.create(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(new LiveSession("session-1", "kol-1", "2026-05-31", "title", "WXPUSHER_AUTO", "正文", "now"));
    when(articleExtractor.fetchText(anyString(), any())).thenReturn("正文");
    when(aiExtractor.extract(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn("{\"ok\":true}");
    when(parser.parse("{\"ok\":true}")).thenReturn(preview);
    when(writer.write(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(new WriteResult("session-1", 1));

    service.ingest(incoming("Alpha"));
    service.ingest(incoming("Alpha"));

    verify(writer, times(1)).write(
        eq("session-1"),
        eq("kol-1"),
        contains("WxPusher / Alpha / "),
        eq("2026-05-31"),
        eq("WXPUSHER_AUTO"),
        eq("正文"),
        anyList());
    verify(messageRepository).attachSession("msg-1", "session-1");
    verify(messageRepository).markImported("msg-1", "正文", "{\"ok\":true}", "session-1");
    verify(aiExtractor, times(1))
        .extract(eq("Alpha"), eq("标题"), eq("摘要"), eq("正文"), eq("https://source"));
  }

  @Test
  void createsSessionBeforeMarkingFailed() throws Exception {
    var sessionRepository = mock(SessionRepository.class);
    var settingsRepository = mock(WxPusherSettingsRepository.class);
    var bloggerRepository = mock(WxPusherBloggerRepository.class);
    var messageRepository = mock(WxPusherMessageRepository.class);
    var sharedRepository = mock(WxPusherSharedMessageRepository.class);
    var articleExtractor = mock(WxPusherArticleExtractor.class);
    var aiExtractor = mock(OpenAiJsonExtractor.class);
    var parser = mock(JsonOpinionParser.class);
    var writer = mock(OpinionImportWriter.class);
    var service = service(
        sessionRepository,
        settingsRepository,
        bloggerRepository,
        messageRepository,
        sharedRepository,
        articleExtractor,
        aiExtractor,
        parser,
        writer);
    when(settingsRepository.get()).thenReturn(settings());
    when(bloggerRepository.enabled()).thenReturn(List.of(blogger("Alpha")));
    when(aiExtractor.extractionEnabled()).thenReturn(true);
    when(messageRepository.createPending(any(PendingMessage.class)))
        .thenReturn(new SaveResult(message("msg-2"), true));
    when(sessionRepository.create(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(new LiveSession("session-failed", "kol-1", "2026-05-31", "title", "WXPUSHER_AUTO", "标题\n摘要\nhttps://source", "now"));
    when(articleExtractor.fetchText(anyString(), any()))
        .thenThrow(new IllegalStateException("detail failed"));
    when(aiExtractor.extract(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn("{\"empty\":true}");
    when(parser.parse("{\"empty\":true}"))
        .thenReturn(new ImportPreview(List.of(), List.of(), List.of(), List.of()));

    service.ingest(incoming("Alpha"));

    String fallbackText = "标题\n摘要\nhttps://source";
    verify(sessionRepository).create(
        eq("kol-1"),
        eq("2026-05-31"),
        contains("WxPusher / Alpha / "),
        eq("WXPUSHER_AUTO"),
        eq(fallbackText));
    verify(messageRepository).attachSession("msg-2", "session-failed");
    verify(messageRepository).markFailed(
        eq("msg-2"),
        eq(fallbackText),
        eq("{\"empty\":true}"),
        contains("可入库观点"));
    verify(writer, never()).write(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyList());
  }

  @Test
  void hydratesMissingSessionsWithoutRetryingImport() {
    var sessionRepository = mock(SessionRepository.class);
    var settingsRepository = mock(WxPusherSettingsRepository.class);
    var bloggerRepository = mock(WxPusherBloggerRepository.class);
    var messageRepository = mock(WxPusherMessageRepository.class);
    var sharedRepository = mock(WxPusherSharedMessageRepository.class);
    var articleExtractor = mock(WxPusherArticleExtractor.class);
    var aiExtractor = mock(OpenAiJsonExtractor.class);
    var parser = mock(JsonOpinionParser.class);
    var writer = mock(OpinionImportWriter.class);
    var service = service(
        sessionRepository,
        settingsRepository,
        bloggerRepository,
        messageRepository,
        sharedRepository,
        articleExtractor,
        aiExtractor,
        parser,
        writer);
    when(messageRepository.listMissingSessions(10)).thenReturn(List.of(message("msg-3")));
    when(sessionRepository.create(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(new LiveSession("session-3", "kol-1", "2026-05-31", "title", "WXPUSHER_AUTO", "标题\n摘要\nhttps://source", "now"));

    service.ensureMessageSessions(10);

    verify(messageRepository).attachSession("msg-3", "session-3");
    verify(aiExtractor, never()).extract(anyString(), anyString(), anyString(), anyString(), anyString());
    verify(writer, never()).write(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyList());
  }

  @Test
  void marksMessageSkippedWhenWxpusherLlmIsDisabled() throws Exception {
    var sessionRepository = mock(SessionRepository.class);
    var settingsRepository = mock(WxPusherSettingsRepository.class);
    var bloggerRepository = mock(WxPusherBloggerRepository.class);
    var messageRepository = mock(WxPusherMessageRepository.class);
    var sharedRepository = mock(WxPusherSharedMessageRepository.class);
    var articleExtractor = mock(WxPusherArticleExtractor.class);
    var aiExtractor = mock(OpenAiJsonExtractor.class);
    var parser = mock(JsonOpinionParser.class);
    var writer = mock(OpinionImportWriter.class);
    var service = service(
        sessionRepository,
        settingsRepository,
        bloggerRepository,
        messageRepository,
        sharedRepository,
        articleExtractor,
        aiExtractor,
        parser,
        writer);
    when(settingsRepository.get()).thenReturn(settings());
    when(bloggerRepository.enabled()).thenReturn(List.of(blogger("Alpha")));
    when(aiExtractor.extractionEnabled()).thenReturn(false);
    when(messageRepository.createPending(any(PendingMessage.class)))
        .thenReturn(new SaveResult(message("msg-skip"), true));
    when(sessionRepository.create(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(new LiveSession("session-skip", "kol-1", "2026-05-31", "title", "WXPUSHER_AUTO", "姝ｆ枃", "now"));
    when(articleExtractor.fetchText(anyString(), any())).thenReturn("姝ｆ枃");

    service.ingest(incoming("Alpha"));

    verify(messageRepository).attachSession("msg-skip", "session-skip");
    verify(messageRepository).markSkipped("msg-skip", "姝ｆ枃", "WxPusher LLM 提取已禁用");
    verify(sharedRepository, never()).saveState(anyString(), anyString(), anyString(), anyString(), anyString());
    verify(aiExtractor, never()).extract(anyString(), anyString(), anyString(), anyString(), anyString());
    verify(parser, never()).parse(anyString());
    verify(writer, never()).write(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyList());
  }

  @Test
  void savesMentionedInstrumentsBeforeSkippingDisabledLlm() throws Exception {
    var sessionRepository = mock(SessionRepository.class);
    var settingsRepository = mock(WxPusherSettingsRepository.class);
    var bloggerRepository = mock(WxPusherBloggerRepository.class);
    var messageRepository = mock(WxPusherMessageRepository.class);
    var sharedRepository = mock(WxPusherSharedMessageRepository.class);
    var articleExtractor = mock(WxPusherArticleExtractor.class);
    var aiExtractor = mock(OpenAiJsonExtractor.class);
    var parser = mock(JsonOpinionParser.class);
    var writer = mock(OpinionImportWriter.class);
    var instruments = mock(InstrumentRepository.class);
    var service = service(
        sessionRepository, settingsRepository, bloggerRepository, messageRepository,
        sharedRepository, articleExtractor, aiExtractor, parser, writer, instruments);
    when(settingsRepository.get()).thenReturn(settings());
    when(bloggerRepository.enabled()).thenReturn(List.of(blogger("Alpha")));
    when(aiExtractor.extractionEnabled()).thenReturn(false);
    when(messageRepository.createPending(any(PendingMessage.class)))
        .thenReturn(new SaveResult(message("msg-symbols"), true));
    when(sessionRepository.create(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(new LiveSession("session-symbols", "kol-1", "2026-05-31", "title", "WXPUSHER_AUTO", "", "now"));
    when(articleExtractor.fetchText(anyString(), any())).thenReturn("NVDA 和 AMZN 受 AI 需求影响，VIP 群继续观察");

    service.ingest(incoming("Alpha"));

    verify(instruments).saveIfAbsent("NVDA", "NVDA", "US", null);
    verify(instruments).saveIfAbsent("AMZN", "AMZN", "US", null);
    verify(instruments, never()).saveIfAbsent(eq("AI"), anyString(), anyString(), any());
    verify(instruments, never()).saveIfAbsent(eq("VIP"), anyString(), anyString(), any());
  }

  @Test
  void reassignsMessageWhenDetailShowsNestedSource() throws Exception {
    var sessionRepository = mock(SessionRepository.class);
    var settingsRepository = mock(WxPusherSettingsRepository.class);
    var bloggerRepository = mock(WxPusherBloggerRepository.class);
    var messageRepository = mock(WxPusherMessageRepository.class);
    var sharedRepository = mock(WxPusherSharedMessageRepository.class);
    var articleExtractor = mock(WxPusherArticleExtractor.class);
    var aiExtractor = mock(OpenAiJsonExtractor.class);
    var parser = mock(JsonOpinionParser.class);
    var writer = mock(OpinionImportWriter.class);
    var service = service(
        sessionRepository, settingsRepository, bloggerRepository, messageRepository,
        sharedRepository, articleExtractor, aiExtractor, parser, writer);
    when(settingsRepository.get()).thenReturn(settings());
    when(bloggerRepository.enabled()).thenReturn(List.of(
        blogger("Alpha", "kol-1", List.of("CIA Feed")),
        blogger("Beta", "kol-beta", List.of())));
    when(aiExtractor.extractionEnabled()).thenReturn(false);
    when(messageRepository.createPending(any(PendingMessage.class)))
        .thenReturn(new SaveResult(message("msg-reassign"), true));
    when(sessionRepository.create(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(new LiveSession("session-beta", "kol-beta", "2026-05-31", "title", "WXPUSHER_AUTO", "", "now"));
    when(articleExtractor.fetchText(anyString(), any())).thenReturn("[feed] Beta: NVDA update");

    service.ingest(incoming("Alpha"));

    verify(messageRepository).reassign("msg-reassign", "kol-beta", "Beta");
    verify(sessionRepository).create(
        eq("kol-beta"),
        eq("2026-05-31"),
        contains("WxPusher / Beta / "),
        eq("WXPUSHER_AUTO"),
        eq("[feed] Beta: NVDA update"));
  }

  private WxPusherIngestionService service(
      SessionRepository sessionRepository,
      WxPusherSettingsRepository settingsRepository,
      WxPusherBloggerRepository bloggerRepository,
      WxPusherMessageRepository messageRepository,
      WxPusherSharedMessageRepository sharedRepository,
      WxPusherArticleExtractor articleExtractor,
      OpenAiJsonExtractor aiExtractor,
      JsonOpinionParser parser,
      OpinionImportWriter writer) {
    return service(
        sessionRepository,
        settingsRepository,
        bloggerRepository,
        messageRepository,
        sharedRepository,
        articleExtractor,
        aiExtractor,
        parser,
        writer,
        mock(InstrumentRepository.class));
  }

  private WxPusherIngestionService service(
      SessionRepository sessionRepository,
      WxPusherSettingsRepository settingsRepository,
      WxPusherBloggerRepository bloggerRepository,
      WxPusherMessageRepository messageRepository,
      WxPusherSharedMessageRepository sharedRepository,
      WxPusherArticleExtractor articleExtractor,
      OpenAiJsonExtractor aiExtractor,
      JsonOpinionParser parser,
      OpinionImportWriter writer,
      InstrumentRepository instruments) {
    return new WxPusherIngestionService(
        sessionRepository,
        settingsRepository,
        bloggerRepository,
        messageRepository,
        sharedRepository,
        articleExtractor,
        aiExtractor,
        parser,
        writer,
        instruments);
  }

  private WxPusherSettings settings() {
    return new WxPusherSettings(
        "default", "device-token", "", "", "Chrome-Windows", "1.1.1", 60, true, false, "", "", "", "", "");
  }

  private WxPusherBlogger blogger(String name) {
    return blogger(name, "kol-1", List.of("Alpha VIP"));
  }

  private WxPusherBlogger blogger(String name, String kolId, List<String> aliases) {
    return new WxPusherBlogger("blogger-" + name, kolId, name, aliases, true, "LAST_30", null, "", "");
  }

  private WxPusherClient.IncomingMessage incoming(String bloggerName) {
    return new WxPusherClient.IncomingMessage(
        "polling",
        "wxpusher:src:https://source",
        bloggerName,
        "标题",
        "摘要",
        "https://wxpusher.zjiecode.com/api/message/1",
        "https://source",
        "2026-05-31T06:00:00Z",
        "123",
        "{\"id\":1}",
        1L);
  }

  private WxPusherMessage message(String id) {
    return new WxPusherMessage(
        id,
        "wxpusher:src:https://source",
        "kol-1",
        "Alpha",
        "标题",
        "摘要",
        "https://wxpusher.zjiecode.com/api/message/1",
        "https://source",
        "2026-05-31T06:00:00Z",
        "{\"id\":1}",
        "",
        "",
        "PENDING",
        "",
        "",
        "",
        "");
  }

  private WxPusherMessage importedMessage() {
    return new WxPusherMessage(
        "msg-1",
        "wxpusher:src:https://source",
        "kol-1",
        "Alpha",
        "标题",
        "摘要",
        "https://wxpusher.zjiecode.com/api/message/1",
        "https://source",
        "2026-05-31T06:00:00Z",
        "{\"id\":1}",
        "正文",
        "{\"ok\":true}",
        "IMPORTED",
        "",
        "session-1",
        "",
        "");
  }
}
