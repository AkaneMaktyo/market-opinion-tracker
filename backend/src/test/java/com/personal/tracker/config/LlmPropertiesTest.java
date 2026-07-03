package com.personal.tracker.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LlmPropertiesTest {
  @Test
  void onlyYoutubeScenesRemainEnabledWhenWxpusherIsOff() {
    var properties = new LlmProperties();
    properties.setYoutubeAutoImportEnabled(true);
    properties.setWxpusherEnabled(false);

    assertTrue(properties.sceneEnabled("YOUTUBE_AUTO_IMPORT"));
    assertFalse(properties.sceneEnabled("WXPUSHER_EXTRACT"));
    assertFalse(properties.sceneEnabled("CUSTOM_SCENE"));
  }

  @Test
  void throwsWhenSceneIsDisabled() {
    var properties = new LlmProperties();
    properties.setWxpusherEnabled(false);

    assertThrows(IllegalStateException.class, () -> properties.ensureSceneEnabled("WXPUSHER_HEALTH"));
  }
}
