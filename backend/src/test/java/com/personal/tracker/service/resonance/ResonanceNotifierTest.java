package com.personal.tracker.service.resonance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.repository.resonance.ResonanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ResonanceNotifierTest {
  @Test
  void statusUsesActionableThresholdByDefault() {
    var notifier = new ResonanceNotifier(
        new MockEnvironment(),
        new ObjectMapper(),
        mock(ResonanceRepository.class));

    var status = notifier.status();

    assertEquals(70, status.minScore());
    assertFalse(status.pushReady());
    assertTrue(status.message().contains("70"));
  }

  @Test
  void statusRecognizesConfiguredPushTarget() {
    var env = new MockEnvironment()
        .withProperty("RESONANCE_ALERT_MIN_SCORE", "82")
        .withProperty("RESONANCE_WXPUSHER_APP_TOKEN", "token-1")
        .withProperty("RESONANCE_WXPUSHER_UIDS", "uid-1,uid-2");
    var notifier = new ResonanceNotifier(
        env,
        new ObjectMapper(),
        mock(ResonanceRepository.class));

    var status = notifier.status();

    assertEquals(82, status.minScore());
    assertTrue(status.pushReady());
    assertTrue(status.message().contains("82"));
  }
}
