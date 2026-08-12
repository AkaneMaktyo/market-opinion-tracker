package com.personal.tracker.service.youtube;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AliyunOssClientTest {
  @Test
  void upgradesSignedAudioUrlToHttps() {
    String signed = "http://audio.example.com/video.m4a?Expires=1&Signature=test";

    assertEquals(
        "https://audio.example.com/video.m4a?Expires=1&Signature=test",
        AliyunOssClient.secureUrl(signed));
  }

  @Test
  void preservesHttpsSignedAudioUrl() {
    String signed = "https://audio.example.com/video.m4a?Expires=1&Signature=test";

    assertEquals(signed, AliyunOssClient.secureUrl(signed));
  }
}
