package com.personal.tracker.service.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.repository.wxpusher.WxPusherNotifySettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class WxPusherPushClientTest {
  @Test
  void isConfiguredSupportsFallbackPrefixes() {
    var env = new MockEnvironment()
        .withProperty("POSITION_NOTIFY_WXPUSHER_APP_TOKEN", "token-1")
        .withProperty("POSITION_NOTIFY_WXPUSHER_UIDS", "uid-1");
    var client = new WxPusherPushClient(env, new ObjectMapper(), mock(WxPusherNotifySettingsRepository.class));

    assertTrue(client.isConfigured("YOUTUBE", "RESONANCE", "POSITION_NOTIFY"));
  }

  @Test
  void sendReturnsWaitingConfigWhenTargetMissing() {
    var client = new WxPusherPushClient(
        new MockEnvironment(),
        new ObjectMapper(),
        mock(WxPusherNotifySettingsRepository.class));

    WxPusherPushClient.PushResult result = client.send("title", "content", "YOUTUBE");

    assertFalse(result.ok());
    assertEquals("WAITING_CONFIG", result.status());
  }

  @Test
  void isConfiguredFallsBackToStoredNotifySettings() {
    var repository = mock(WxPusherNotifySettingsRepository.class);
    when(repository.get()).thenReturn(new WxPusherNotifySettingsRepository.WxPusherNotifySettings(
        "default", "", "token-2", "uid-2 uid-3", "", "", ""));
    var client = new WxPusherPushClient(new MockEnvironment(), new ObjectMapper(), repository);

    assertTrue(client.isConfigured("YOUTUBE"));
  }
}
