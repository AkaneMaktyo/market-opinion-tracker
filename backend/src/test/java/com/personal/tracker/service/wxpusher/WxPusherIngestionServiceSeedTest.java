package com.personal.tracker.service.wxpusher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.SessionRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.WxPusherBlogger;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSharedMessageRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository.WxPusherSettings;
import com.personal.tracker.service.imports.OpinionImportWriter;
import com.personal.tracker.service.json.JsonOpinionParser;
import com.personal.tracker.service.wxpusher.ocr.WxPusherImageOcrService;
import java.util.List;
import org.junit.jupiter.api.Test;

class WxPusherIngestionServiceSeedTest {
  @Test
  void seedsPendingHistoryOnlyOncePerPendingBloggerBatch() {
    var sessionRepository = mock(SessionRepository.class);
    var settingsRepository = mock(WxPusherSettingsRepository.class);
    var bloggerRepository = mock(WxPusherBloggerRepository.class);
    var messageRepository = mock(WxPusherMessageRepository.class);
    var sharedRepository = mock(WxPusherSharedMessageRepository.class);
    var articleExtractor = mock(WxPusherArticleExtractor.class);
    var imageOcrService = mock(WxPusherImageOcrService.class);
    var aiExtractor = mock(OpenAiJsonExtractor.class);
    var parser = mock(JsonOpinionParser.class);
    var writer = mock(OpinionImportWriter.class);
    var instruments = mock(InstrumentRepository.class);
    var service = new WxPusherIngestionService(
        sessionRepository,
        settingsRepository,
        bloggerRepository,
        messageRepository,
        sharedRepository,
        articleExtractor,
        imageOcrService,
        aiExtractor,
        parser,
        writer,
        instruments);
    WxPusherSettings settings = new WxPusherSettings(
        "default",
        "device-token",
        "",
        "",
        "Chrome-Windows",
        "1.1.1",
        60,
        true,
        false,
        "",
        "",
        "",
        "",
        "");
    WxPusherBlogger blogger = new WxPusherBlogger(
        "blogger-1",
        "kol-1",
        "Alpha",
        List.of("Alpha VIP"),
        true,
        "LAST_30",
        null,
        "",
        "");
    WxPusherClient.IncomingMessage recent = new WxPusherClient.IncomingMessage(
        "polling",
        "wxpusher:src:https://source",
        "Alpha",
        "标题",
        "摘要",
        "https://wxpusher.zjiecode.com/api/message/1",
        "https://source",
        "2026-05-31T06:00:00Z",
        "123",
        "{\"id\":1}",
        1L);
    when(settingsRepository.get()).thenReturn(settings);
    when(bloggerRepository.enabledPendingSeed()).thenReturn(List.of(blogger), List.of());
    when(sharedRepository.listRecent(600)).thenReturn(List.of(recent));
    when(messageRepository.createPending(any()))
        .thenReturn(new WxPusherMessageRepository.SaveResult(
            new WxPusherMessageRepository.WxPusherMessage(
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
                ""),
            false));

    service.seedHistory();
    service.seedHistory();

    verify(sharedRepository, times(2)).listRecent(600);
    verify(bloggerRepository, times(1)).markSeedCompleted("blogger-1");
  }
}
