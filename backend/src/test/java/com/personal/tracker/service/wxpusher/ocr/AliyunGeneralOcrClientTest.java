package com.personal.tracker.service.wxpusher.ocr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AliyunGeneralOcrClientTest {
  @Test
  void createsUrlRequestForRemoteImage() {
    var request = AliyunGeneralOcrClient.buildRequest("https://img.example/signal.png");

    assertEquals("https://img.example/signal.png", request.url);
  }

  @Test
  void createsBinaryRequestForEmbeddedImage() throws Exception {
    var request = AliyunGeneralOcrClient.buildRequest("data:image/jpeg;base64,aGVsbG8=");

    assertArrayEquals(
        "hello".getBytes(StandardCharsets.UTF_8),
        request.body.readAllBytes());
  }

  @Test
  void rejectsInvalidEmbeddedImage() {
    assertThrows(
        IllegalArgumentException.class,
        () -> AliyunGeneralOcrClient.buildRequest("data:image/jpeg;base64,%%%"));
  }
}
