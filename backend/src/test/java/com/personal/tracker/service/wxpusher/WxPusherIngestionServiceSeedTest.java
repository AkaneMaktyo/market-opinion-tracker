package com.personal.tracker.service.wxpusher;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.WxPusherBlogger;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository.WxPusherSettings;
import com.personal.tracker.service.imports.OpinionImportWriter;
import com.personal.tracker.service.json.JsonOpinionParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class WxPusherIngestionServiceSeedTest {
  @Test
  void seedsPendingHistoryOnlyOncePerPendingBloggerBatch() {
    var settingsRepository = mock(WxPusherSettingsRepository.class);
    var bloggerRepository = mock(WxPusherBloggerRepository.class);
    var messageRepository = mock(WxPusherMessageRepository.class);
    var client = mock(WxPusherClient.class);
    var articleExtractor = mock(WxPusherArticleExtractor.class);
    var aiExtractor = mock(OpenAiJsonExtractor.class);
    JsonOpinionParser parser = mock(JsonOpinionParser.class);
    var writer = mock(OpinionImportWriter.class);
    var service = new WxPusherIngestionService(
        settingsRepository,
        bloggerRepository,
        messageRepository,
        client,
        articleExtractor,
        aiExtractor,
        parser,
        writer);
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
    when(settingsRepository.get()).thenReturn(settings);
    when(bloggerRepository.enabledPendingSeed()).thenReturn(List.of(blogger), List.of());
    when(client.maxCursor()).thenReturn("MAX");
    when(client.fetchPage(settings, "MAX")).thenReturn(List.of());

    service.seedHistory();
    service.seedHistory();

    verify(client, times(1)).fetchPage(settings, "MAX");
    verify(bloggerRepository, times(1)).markSeedCompleted("blogger-1");
  }
}
