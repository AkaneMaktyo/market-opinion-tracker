package com.personal.tracker.service.youtube;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AliyunSpeechTranscriberTest {
  @Test
  void directUploadSupportsCommonAliyunFormats() {
    assertTrue(AliyunSpeechTranscriber.supportsDirectUpload(Path.of("clip.m4a")));
    assertTrue(AliyunSpeechTranscriber.supportsDirectUpload(Path.of("clip.MP3")));
    assertTrue(AliyunSpeechTranscriber.supportsDirectUpload(Path.of("clip.wav")));
    assertTrue(AliyunSpeechTranscriber.supportsDirectUpload(Path.of("clip.webm")));
    assertTrue(AliyunSpeechTranscriber.supportsDirectUpload(Path.of("clip.opus")));
  }

  @Test
  void directUploadRejectsUnsupportedFormats() {
    assertFalse(AliyunSpeechTranscriber.supportsDirectUpload(Path.of("clip.txt")));
    assertFalse(AliyunSpeechTranscriber.supportsDirectUpload(Path.of("clip.srt")));
  }

  @Test
  void fileTransTaskEnablesSampleRateAdaptiveByDefault() throws Exception {
    String payload = AliyunFileTransClient.buildTaskPayload("app-key", "https://example.com/a.m4a", true);
    var root = new ObjectMapper().readTree(payload);

    assertTrue(root.path("enable_sample_rate_adaptive").asBoolean());
  }
}
