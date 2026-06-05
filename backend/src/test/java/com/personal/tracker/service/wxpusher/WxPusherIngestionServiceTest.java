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

import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.WxPusherBlogger;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.PendingMessage;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.SaveResult;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.WxPusherMessage;
import com.personal.tracker.repository.wxpusher.WxPusherSharedMessageRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository.WxPusherSettings;
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
    var settingsRepository = mock(WxPusherSettingsRepository.class);
    var bloggerRepository = mock(WxPusherBloggerRepository.class);
    var messageRepository = mock(WxPusherMessageRepository.class);
    var sharedRepository = mock(WxPusherSharedMessageRepository.class);
    var articleExtractor = mock(WxPusherArticleExtractor.class);
    var aiExtractor = mock(OpenAiJsonExtractor.class);
    JsonOpinionParser parser = mock(JsonOpinionParser.class);
    var writer = mock(OpinionImportWriter.class);
    var service = service(
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
    var settingsRepository = mock(WxPusherSettingsRepository.class);
    var bloggerRepository = mock(WxPusherBloggerRepository.class);
    var messageRepository = mock(WxPusherMessageRepository.class);
    var sharedRepository = mock(WxPusherSharedMessageRepository.class);
    var articleExtractor = mock(WxPusherArticleExtractor.class);
    var aiExtractor = mock(OpenAiJsonExtractor.class);
    JsonOpinionParser parser = mock(JsonOpinionParser.class);
    var writer = mock(OpinionImportWriter.class);
    var service = service(
        settingsRepository,
        bloggerRepository,
        messageRepository,
        sharedRepository,
        articleExtractor,
        aiExtractor,
        parser,
        writer);
    WxPusherMessage saved = message("msg-1");
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
    WxPusherMessage imported = new WxPusherMessage(
        "msg-1",
        "wxpusher:src:https://source",
        "kol-1",
        "Alpha",
        "鏍囬",
        "鎽樿",
        "https://wxpusher.zjiecode.com/api/message/1",
        "https://source",
        "2026-05-31T06:00:00Z",
        "{\"id\":1}",
        "姝ｆ枃",
        "{\"ok\":true}",
        "IMPORTED",
        "",
        "session-1",
        "",
        "");
    when(messageRepository.createPending(any(PendingMessage.class)))
        .thenReturn(new SaveResult(saved, true), new SaveResult(imported, false));
    when(articleExtractor.fetchText(anyString(), any())).thenReturn("正文");
    when(aiExtractor.extract(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn("{\"ok\":true}");
    when(parser.parse("{\"ok\":true}")).thenReturn(preview);
    when(writer.write(anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(new WriteResult("session-1", 1));

    service.ingest(incoming("Alpha"));
    service.ingest(incoming("Alpha"));

    verify(writer, times(1)).write(
        eq("kol-1"),
        contains("WxPusher / Alpha / "),
        eq("2026-05-31"),
        eq("WXPUSHER_AUTO"),
        eq("{\"ok\":true}"),
        anyList());
    verify(messageRepository, times(1))
        .markImported("msg-1", "正文", "{\"ok\":true}", "session-1");
    verify(aiExtractor, times(1))
        .extract(eq("Alpha"), eq("标题"), eq("摘要"), eq("正文"), eq("https://source"));
  }

  @Test
  void fallsBackToSummaryWhenDetailFetchFailsAndMarksFailed() throws Exception {
    var settingsRepository = mock(WxPusherSettingsRepository.class);
    var bloggerRepository = mock(WxPusherBloggerRepository.class);
    var messageRepository = mock(WxPusherMessageRepository.class);
    var sharedRepository = mock(WxPusherSharedMessageRepository.class);
    var articleExtractor = mock(WxPusherArticleExtractor.class);
    var aiExtractor = mock(OpenAiJsonExtractor.class);
    JsonOpinionParser parser = mock(JsonOpinionParser.class);
    var writer = mock(OpinionImportWriter.class);
    var service = service(
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
    when(messageRepository.createPending(any(PendingMessage.class)))
        .thenReturn(new SaveResult(message("msg-2"), true));
    when(articleExtractor.fetchText(anyString(), any()))
        .thenThrow(new IllegalStateException("detail failed"));
    when(aiExtractor.extract(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn("{\"empty\":true}");
    when(parser.parse("{\"empty\":true}"))
        .thenReturn(new ImportPreview(List.of(), List.of(), List.of(), List.of()));

    service.ingest(incoming("Alpha"));

    String fallbackText = "标题\n摘要\nhttps://source";
    verify(aiExtractor).extract("Alpha", "标题", "摘要", fallbackText, "https://source");
    verify(messageRepository).markFailed(
        eq("msg-2"),
        eq(fallbackText),
        eq("{\"empty\":true}"),
        contains("可入库观点"));
    verify(writer, never()).write(anyString(), anyString(), anyString(), anyString(), anyString(), anyList());
  }

  private WxPusherIngestionService service(
      WxPusherSettingsRepository settingsRepository,
      WxPusherBloggerRepository bloggerRepository,
      WxPusherMessageRepository messageRepository,
      WxPusherSharedMessageRepository sharedRepository,
      WxPusherArticleExtractor articleExtractor,
      OpenAiJsonExtractor aiExtractor,
      JsonOpinionParser parser,
      OpinionImportWriter writer) {
    return new WxPusherIngestionService(
        settingsRepository,
        bloggerRepository,
        messageRepository,
        sharedRepository,
        articleExtractor,
        aiExtractor,
        parser,
        writer);
  }

  private WxPusherSettings settings() {
    return new WxPusherSettings(
        "default", "device-token", "", "", "Chrome-Windows", "1.1.1", 60, true, false, "", "", "", "", "");
  }

  private WxPusherBlogger blogger(String name) {
    return new WxPusherBlogger("blogger-1", "kol-1", name, List.of("Alpha VIP"), true, "LAST_30", null, "", "");
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
}
