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

  @Test
  void ignoresUnconfiguredNestedSourceInsteadOfOuterFeedAlias() {
    WxPusherBlogger outer = new WxPusherBlogger(
        "outer",
        "kol-outer",
        "顺哥",
        List.of("CIA-信息推送", "顺哥"),
        true,
        "LAST_30",
        null,
        "",
        "");
    var incoming = new WxPusherClient.IncomingMessage(
        "polling",
        "wxpusher:gold",
        "CIA-信息推送",
        "您订阅的【CIA-信息推送】有新的消息",
        "[✨黄金帝国-PREMIUM-CIRCLE-⭕] PREMIUM SIGNALS: GOLD SELL NOW @ 4081",
        "https://wxpusher.zjiecode.com/api/message/gold",
        "https://discord.example/gold",
        "2026-07-08T14:09:39Z",
        "gold",
        "{}",
        3L);

    var matched = WxPusherBloggerMatcher.match(incoming, List.of(outer));

    assertTrue(matched.isEmpty());
  }

  @Test
  void ignoresDecoratedDetailSourceEvenWhenOuterFeedIsMentionedLater() {
    WxPusherBlogger outer = new WxPusherBlogger(
        "outer",
        "kol-outer",
        "Shun",
        List.of("CIA feed", "Shun"),
        true,
        "LAST_30",
        null,
        "",
        "");
    var incoming = new WxPusherClient.IncomingMessage(
        "detail",
        "wxpusher:gold-detail-real",
        "CIA feed",
        "subscribed CIA feed update",
        "\u2728GoldenEmpire-PREMIUM-CIRCLE\nGOLD SELL NOW @ 4081\nsubscribed CIA feed update",
        "https://wxpusher.zjiecode.com/api/message/gold",
        "https://discord.example/gold",
        "2026-07-08T14:09:39Z",
        "gold",
        "{}",
        4L);

    var matched = WxPusherBloggerMatcher.match(incoming, List.of(outer));

    assertTrue(matched.isEmpty());
  }

  @Test
  void ignoresUnconfiguredBracketSourceAfterSubscriptionTitle() {
    WxPusherBlogger outer = new WxPusherBlogger(
        "outer",
        "kol-outer",
        "Shun",
        List.of("CIA feed", "Shun"),
        true,
        "LAST_30",
        null,
        "",
        "");
    var incoming = new WxPusherClient.IncomingMessage(
        "detail",
        "wxpusher:gold-second-line",
        "CIA feed",
        "subscribed CIA feed update",
        "subscribed CIA feed update\n"
            + "[\u2728GoldenEmpire-PREMIUM-CIRCLE-\u2b55] PREMIUM SIGNALS: Hello team!",
        "https://wxpusher.zjiecode.com/api/message/gold",
        "https://discord.example/gold",
        "2026-07-08T14:09:39Z",
        "gold",
        "{}",
        4L);

    var matched = WxPusherBloggerMatcher.match(incoming, List.of(outer));

    assertTrue(matched.isEmpty());
    assertTrue(WxPusherBloggerMatcher.hasExplicitSource(incoming));
  }

  @Test
  void ignoresRealGoldSourceEvenWhenShunHasOuterFeedAlias() {
    WxPusherBlogger shun = new WxPusherBlogger(
        "shun",
        "kol-shun",
        "\u987a\u54e5",
        List.of("\u987a\u54e5", "CIA-\u4fe1\u606f\u63a8\u9001"),
        true,
        "LAST_30",
        null,
        "",
        "");
    var incoming = new WxPusherClient.IncomingMessage(
        "detail",
        "wxpusher:real-gold-photo",
        "",
        "\u60a8\u8ba2\u9605\u7684\u3010CIA-\u4fe1\u606f\u63a8\u9001\u3011\u6709\u65b0\u7684\u6d88\u606f",
        "\u60a8\u8ba2\u9605\u7684\u3010CIA-\u4fe1\u606f\u63a8\u9001\u3011\u6709\u65b0\u7684\u6d88\u606f\n"
            + "[\u2728\u9ec4\u91d1\u5e1d\u56fd-\ud835\udc0f\ud835\udc11\ud835\udc04\ud835\udc0c\ud835\udc08\ud835\udc14\ud835\udc0c-\ud835\udc02\ud835\udc08\ud835\udc11\ud835\udc02\ud835\udc0b\ud835\udc04-\u2b55] PREMIUM SIGNALS: [Photo]",
        "https://wxpusher.zjiecode.com/api/message/gold",
        "https://discord.example/gold",
        "2026-06-03T15:26:34Z",
        "gold",
        "{}",
        4L);

    var matched = WxPusherBloggerMatcher.match(incoming, List.of(shun));

    assertTrue(WxPusherBloggerMatcher.hasExplicitSource(incoming));
    assertTrue(matched.isEmpty());
  }

  @Test
  void ignoresUnconfiguredCompactDetailSource() {
    WxPusherBlogger outer = new WxPusherBlogger(
        "outer",
        "kol-outer",
        "顺哥",
        List.of("CIA-信息推送", "顺哥"),
        true,
        "LAST_30",
        null,
        "",
        "");
    var incoming = new WxPusherClient.IncomingMessage(
        "detail",
        "wxpusher:gold-detail",
        "",
        "",
        "✨黄金帝国-PREMIUM-CIRCLE-⭕\nGOLD SELL NOW @ 4081",
        "https://wxpusher.zjiecode.com/api/message/gold",
        "https://discord.example/gold",
        "2026-07-08T14:09:39Z",
        "gold",
        "{}",
        4L);

    var matched = WxPusherBloggerMatcher.match(incoming, List.of(outer));

    assertTrue(matched.isEmpty());
  }
}
