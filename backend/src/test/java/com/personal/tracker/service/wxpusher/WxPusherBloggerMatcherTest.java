package com.personal.tracker.service.wxpusher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.WxPusherBlogger;
import java.util.List;
import org.junit.jupiter.api.Test;

class WxPusherBloggerMatcherTest {
  @Test
  void matchesByAlias() {
    WxPusherBlogger blogger = new WxPusherBlogger(
        "b1",
        "k1",
        "Trader Alpha",
        List.of("Alpha VIP", "Alpha"),
        true,
        "LAST_30",
        null,
        "",
        "");

    var matched = WxPusherBloggerMatcher.match("Alpha VIP", List.of(blogger));

    assertTrue(matched.isPresent());
    assertEquals("b1", matched.get().id());
  }

  @Test
  void matchesByAuthorHandleInsideSummary() {
    WxPusherBlogger blogger = new WxPusherBlogger(
        "b1",
        "k1",
        "懂币猫",
        List.of("dongbimao"),
        true,
        "LAST_30",
        null,
        "",
        "");
    var incoming = new WxPusherClient.IncomingMessage(
        "polling",
        "wxpusher:1",
        "CIA-信息推送",
        "",
        "[懂币猫] dongbimao: > 回复 @dongbimao: #HYPE 走势更新",
        "https://wxpusher.zjiecode.com/api/message/1",
        "https://discord.example/1",
        "2026-05-31T06:00:00Z",
        "1",
        "{}",
        1L);

    var matched = WxPusherBloggerMatcher.match(incoming, List.of(blogger));

    assertTrue(matched.isPresent());
    assertEquals("b1", matched.get().id());
  }

  @Test
  void prefersNestedSourceOverOuterFeedAlias() {
    WxPusherBlogger outer = new WxPusherBlogger(
        "outer",
        "kol-outer",
        "顺哥",
        List.of("CIA-信息推送"),
        true,
        "LAST_30",
        null,
        "",
        "");
    WxPusherBlogger realSource = new WxPusherBlogger(
        "source",
        "kol-source",
        "美股投资网",
        List.of("美股投资网"),
        true,
        "LAST_30",
        null,
        "",
        "");
    var incoming = new WxPusherClient.IncomingMessage(
        "polling",
        "wxpusher:2",
        "CIA-信息推送",
        "您订阅的【CIA-信息推送】有新的消息",
        "[💵｜美股投资网] 美股会员平台-正股: INTC 利好",
        "https://wxpusher.zjiecode.com/api/message/2",
        "https://discord.example/2",
        "2026-07-06T06:00:00Z",
        "2",
        "{}",
        2L);

    var matched = WxPusherBloggerMatcher.match(incoming, List.of(outer, realSource));

    assertTrue(matched.isPresent());
    assertEquals("source", matched.get().id());
  }
}
