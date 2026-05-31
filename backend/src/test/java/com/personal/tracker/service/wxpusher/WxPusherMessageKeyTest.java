package com.personal.tracker.service.wxpusher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WxPusherMessageKeyTest {
  @Test
  void prefersSourceUrlWhenBuildingMessageKey() {
    String key = WxPusherMessageKey.build(
        "https://origin.example/post/1",
        "https://wxpusher.zjiecode.com/api/message/a",
        "polling",
        "123",
        "summary");

    assertEquals("wxpusher:src:https://origin.example/post/1", key);
  }

  @Test
  void fallsBackToChannelAndMessageId() {
    String key = WxPusherMessageKey.build("", "", "websocket", "qid-1", "summary");

    assertEquals("wxpusher:websocket:qid-1", key);
  }
}
