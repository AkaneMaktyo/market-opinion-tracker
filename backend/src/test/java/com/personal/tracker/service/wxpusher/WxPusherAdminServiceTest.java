package com.personal.tracker.service.wxpusher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.domain.Kol;
import com.personal.tracker.repository.KolRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.SaveCommand;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.WxPusherBlogger;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.MessageSummary;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.WxPusherMessage;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository.WxPusherSettings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WxPusherAdminServiceTest {
  @Test
  void bloggersAutoSeedDefaultWatchlist() {
    var kols = mock(KolRepository.class);
    var settingsRepository = mock(WxPusherSettingsRepository.class);
    var bloggerRepository = mock(WxPusherBloggerRepository.class);
    var messageRepository = mock(WxPusherMessageRepository.class);
    var aiExtractor = mock(OpenAiJsonExtractor.class);
    var ingestion = mock(WxPusherIngestionService.class);
    var lifecycle = mock(WxPusherMonitorLifecycle.class);
    var service = new WxPusherAdminService(
        kols,
        settingsRepository,
        bloggerRepository,
        messageRepository,
        aiExtractor,
        ingestion,
        lifecycle);
    when(kols.save(anyString(), anyString()))
        .thenAnswer(call -> new Kol("kol-" + call.getArgument(0), call.getArgument(0), call.getArgument(1), "now"));
    when(bloggerRepository.list()).thenReturn(List.of(), List.of(
        blogger("华尔街阿宝分享"),
        blogger("猫姐会员频道"),
        blogger("幂笈投资"),
        blogger("牛顿师兄"),
        blogger("美股投资网")));
    when(messageRepository.summaryByKolIds(any())).thenReturn(Map.of());

    var bloggers = service.bloggers();

    verify(bloggerRepository, times(5)).create(any(SaveCommand.class));
    verify(lifecycle).refresh();
    assertEquals(5, bloggers.size());
  }

  @Test
  void createBloggerSyncsKolAndRequestsRefresh() {
    var kols = mock(KolRepository.class);
    var settingsRepository = mock(WxPusherSettingsRepository.class);
    var bloggerRepository = mock(WxPusherBloggerRepository.class);
    var messageRepository = mock(WxPusherMessageRepository.class);
    var aiExtractor = mock(OpenAiJsonExtractor.class);
    var ingestion = mock(WxPusherIngestionService.class);
    var lifecycle = mock(WxPusherMonitorLifecycle.class);
    var service = new WxPusherAdminService(
        kols,
        settingsRepository,
        bloggerRepository,
        messageRepository,
        aiExtractor,
        ingestion,
        lifecycle);
    when(kols.save("Alpha", "WxPusher 博主"))
        .thenReturn(new Kol("kol-1", "Alpha", "WxPusher 博主", "now"));
    when(bloggerRepository.create(any(SaveCommand.class)))
        .thenReturn(new WxPusherBlogger(
            "b1", "kol-1", "Alpha", List.of("Alpha VIP"), true, "LAST_30", null, "now", "now"));
    when(messageRepository.summaryByKolIds(List.of("kol-1"))).thenReturn(Map.of(
        "kol-1",
        new MessageSummary("kol-1", 2, 1, 1, "2026-05-31T06:00:00Z")));

    var created = service.createBlogger(
        new WxPusherAdminService.BloggerCommand(null, "Alpha", List.of("Alpha VIP"), true));

    var captor = ArgumentCaptor.forClass(SaveCommand.class);
    verify(bloggerRepository).create(captor.capture());
    assertEquals("kol-1", captor.getValue().kolId());
    assertEquals("Alpha", created.bloggerName());
    assertEquals(2, created.messageCount());
    assertNull(captor.getValue().seedCompletedAt());
    verify(lifecycle).refresh();
  }

  @Test
  void updateBloggerResetsSeedWhenRuleChangesAndStaysEnabled() {
    var kols = mock(KolRepository.class);
    var settingsRepository = mock(WxPusherSettingsRepository.class);
    var bloggerRepository = mock(WxPusherBloggerRepository.class);
    var messageRepository = mock(WxPusherMessageRepository.class);
    var aiExtractor = mock(OpenAiJsonExtractor.class);
    var ingestion = mock(WxPusherIngestionService.class);
    var lifecycle = mock(WxPusherMonitorLifecycle.class);
    var service = new WxPusherAdminService(
        kols,
        settingsRepository,
        bloggerRepository,
        messageRepository,
        aiExtractor,
        ingestion,
        lifecycle);
    WxPusherBlogger current = new WxPusherBlogger(
        "b1", "kol-1", "Alpha", List.of("Alpha"), true, "LAST_30", "2026-05-31T00:00:00Z", "now", "now");
    when(bloggerRepository.findById("b1")).thenReturn(current);
    when(kols.save("Alpha Pro", "WxPusher 博主"))
        .thenReturn(new Kol("kol-2", "Alpha Pro", "WxPusher 博主", "now"));
    when(bloggerRepository.update(any(SaveCommand.class)))
        .thenReturn(new WxPusherBlogger(
            "b1", "kol-2", "Alpha Pro", List.of("Alpha", "AP"), true, "LAST_30", null, "now", "later"));
    when(messageRepository.summaryByKolIds(List.of("kol-2"))).thenReturn(Map.of());

    service.updateBlogger(
        new WxPusherAdminService.BloggerCommand("b1", "Alpha Pro", List.of("Alpha", "AP"), true));

    var captor = ArgumentCaptor.forClass(SaveCommand.class);
    verify(bloggerRepository).update(captor.capture());
    assertEquals("kol-2", captor.getValue().kolId());
    assertEquals("Alpha Pro", captor.getValue().bloggerName());
    assertNull(captor.getValue().seedCompletedAt());
    verify(lifecycle).refresh();
  }

  @Test
  void statusShowsMissingTokenIssueBeforeRuntimeError() {
    var kols = mock(KolRepository.class);
    var settingsRepository = mock(WxPusherSettingsRepository.class);
    var bloggerRepository = mock(WxPusherBloggerRepository.class);
    var messageRepository = mock(WxPusherMessageRepository.class);
    var aiExtractor = mock(OpenAiJsonExtractor.class);
    var ingestion = mock(WxPusherIngestionService.class);
    var lifecycle = mock(WxPusherMonitorLifecycle.class);
    var service = new WxPusherAdminService(
        kols,
        settingsRepository,
        bloggerRepository,
        messageRepository,
        aiExtractor,
        ingestion,
        lifecycle);
    when(settingsRepository.get()).thenReturn(new WxPusherSettings(
        "default", "", "", "", "Chrome-Windows", "1.1.1", 60, true, true, "", "", "", "", ""));
    when(bloggerRepository.list()).thenReturn(List.of(
        blogger("华尔街阿宝分享"),
        blogger("猫姐会员频道"),
        blogger("幂笈投资"),
        blogger("牛顿师兄"),
        blogger("美股投资网")));
    when(aiExtractor.health()).thenReturn(new OpenAiJsonExtractor.HealthStatus(false, false, "未配置", null));
    when(lifecycle.runtimeState()).thenReturn(new WxPusherMonitorLifecycle.RuntimeState(
        true, "CONNECTED", "", "", "旧错误"));

    var status = service.status();

    assertFalse(status.running());
    assertEquals("ERROR", status.websocketState());
    assertTrue(status.lastError().contains("WXPUSHER_DEVICE_TOKEN"));
    assertTrue(status.lastError().contains("WXPUSHER_PUSH_TOKEN"));
  }

  @Test
  void retryBatchOnlyProcessesRetryableMessagesAndCountsResult() {
    var kols = mock(KolRepository.class);
    var settingsRepository = mock(WxPusherSettingsRepository.class);
    var bloggerRepository = mock(WxPusherBloggerRepository.class);
    var messageRepository = mock(WxPusherMessageRepository.class);
    var aiExtractor = mock(OpenAiJsonExtractor.class);
    var ingestion = mock(WxPusherIngestionService.class);
    var lifecycle = mock(WxPusherMonitorLifecycle.class);
    var service = new WxPusherAdminService(
        kols,
        settingsRepository,
        bloggerRepository,
        messageRepository,
        aiExtractor,
        ingestion,
        lifecycle);
    WxPusherMessage first = message("m1", "SKIPPED");
    WxPusherMessage second = message("m2", "SKIPPED");
    when(messageRepository.listForRetry("SKIPPED", "kol-1", 2)).thenReturn(List.of(first, second));
    when(messageRepository.findById("m1")).thenReturn(java.util.Optional.of(message("m1", "IMPORTED")));
    when(messageRepository.findById("m2")).thenReturn(java.util.Optional.of(message("m2", "SKIPPED")));

    var result = service.retryBatch("SKIPPED", "kol-1", 2);

    assertEquals(2, result.processed());
    assertEquals(1, result.imported());
    assertEquals(1, result.skipped());
    assertEquals(0, result.failed());
    verify(ingestion).retry("m1");
    verify(ingestion).retry("m2");
  }

  @Test
  void retryBatchRejectsImportedStatus() {
    var service = new WxPusherAdminService(
        mock(KolRepository.class),
        mock(WxPusherSettingsRepository.class),
        mock(WxPusherBloggerRepository.class),
        mock(WxPusherMessageRepository.class),
        mock(OpenAiJsonExtractor.class),
        mock(WxPusherIngestionService.class),
        mock(WxPusherMonitorLifecycle.class));

    assertThrows(IllegalArgumentException.class, () -> service.retryBatch("IMPORTED", "", 10));
  }

  private WxPusherBlogger blogger(String name) {
    return new WxPusherBlogger("id-" + name, "kol-" + name, name, List.of(), true, "LAST_30", null, "now", "now");
  }

  private WxPusherMessage message(String id, String status) {
    return new WxPusherMessage(
        id, "key-" + id, "kol-1", "Alpha", "", "", "", "", "2026-07-08T10:00:00Z",
        "{}", "NVDA 做多", "", status, "", "session-" + id, "now", "now");
  }
}
