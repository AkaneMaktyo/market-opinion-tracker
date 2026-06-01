package com.personal.tracker.service.wxpusher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.domain.Kol;
import com.personal.tracker.repository.KolRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.SaveCommand;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.WxPusherBlogger;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository.WxPusherSettings;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WxPusherAdminServiceTest {
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

    var created = service.createBlogger(
        new WxPusherAdminService.BloggerCommand(null, "Alpha", List.of("Alpha VIP"), true));

    var captor = ArgumentCaptor.forClass(SaveCommand.class);
    verify(bloggerRepository).create(captor.capture());
    assertEquals("kol-1", captor.getValue().kolId());
    assertEquals("Alpha", created.bloggerName());
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
    when(bloggerRepository.list()).thenReturn(List.of());
    when(bloggerRepository.enabled()).thenReturn(List.of());
    when(aiExtractor.health()).thenReturn(new OpenAiJsonExtractor.HealthStatus(false, false, "未配置", null));
    when(lifecycle.runtimeState()).thenReturn(new WxPusherMonitorLifecycle.RuntimeState(
        true, "CONNECTED", "", "", "旧错误"));

    var status = service.status();

    assertFalse(status.running());
    assertEquals("ERROR", status.websocketState());
    assertTrue(status.lastError().contains("WXPUSHER_DEVICE_TOKEN"));
    assertTrue(status.lastError().contains("WXPUSHER_PUSH_TOKEN"));
  }
}
