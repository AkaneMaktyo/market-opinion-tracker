package com.personal.tracker.service.wxpusher.fallback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.personal.tracker.domain.PriceLevel;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WxPusherFallbackOpinionExtractorTest {
  @Test
  void extractsBearishGoldSignalWithTargetsAndStop() {
    var opinions = WxPusherFallbackOpinionExtractor.extract(
        "GOLD SELL NOW @ 4081 MORE SELL @ 4091 TP : 4166 TP : 4142 SL : 4099");

    assertEquals(1, opinions.size());
    var gold = opinions.get(0);
    assertEquals("GOLD", gold.symbol());
    assertEquals("BEARISH", gold.direction());
    assertEquals("IGNORE", gold.positionAction());
    assertEquals("短线", gold.horizon());
    assertTrue(gold.priceNotesText().contains("TP"));
    assertTrue(gold.priceLevels().stream().anyMatch(level -> isLevel(level, "TARGET")));
    assertTrue(gold.priceLevels().stream().anyMatch(level -> isLevel(level, "STOP")));
  }

  @Test
  void extractsBullishOpenActionFromChineseBuyMessage() {
    var opinions = WxPusherFallbackOpinionExtractor.extract(
        "今天可以慢慢小仓位开始买Mu soxl 正股，但是需要灵活进进出出。");

    assertEquals(2, opinions.size());
    assertEquals("MU", opinions.get(0).symbol());
    assertEquals("BULLISH", opinions.get(0).direction());
    assertEquals("OPEN", opinions.get(0).positionAction());
    assertEquals("SOXL", opinions.get(1).symbol());
    assertEquals("BULLISH", opinions.get(1).direction());
  }

  @Test
  void keepsMarketNewsAsWatchWithoutProductNoise() {
    var opinions = WxPusherFallbackOpinionExtractor.extract(
        "AVGO 苹果与博通签订定制ASIC合作协议，Vera服务器需求提升，市场关注供应链");

    assertEquals(2, opinions.size());
    assertEquals(Set.of("AVGO", "AAPL"), Set.copyOf(opinions.stream().map(item -> item.symbol()).toList()));
    assertTrue(opinions.stream().allMatch(item -> "WATCH".equals(item.direction())));
  }

  @Test
  void doesNotExtractUrlOrPercentAsPriceLevel() {
    var opinions = WxPusherFallbackOpinionExtractor.extract(
        "PWRL BUY 20%\n"
            + "https://discord.com/channels/1295108691275813036/1511501236791279647/1513566163794268273");

    assertEquals(1, opinions.size());
    assertEquals("PWRL", opinions.get(0).symbol());
    assertTrue(opinions.get(0).priceLevels().isEmpty());
  }

  @Test
  void usesContextSymbolWhenTextHasOpinionButNoTicker() {
    var opinions = WxPusherFallbackOpinionExtractor.extract(
        "\u4eca\u5929\u538b\u529b\u4f4d 193.6, 213.5\uff0c"
            + "\u5927\u652f\u6491 137-157\uff0c\u73b0\u4ef7\u5206\u6279\u4e70",
        java.util.List.of("SOXL"));

    assertEquals(1, opinions.size());
    assertEquals("SOXL", opinions.get(0).symbol());
    assertEquals("BULLISH", opinions.get(0).direction());
    assertEquals("OPEN", opinions.get(0).positionAction());
    assertTrue(!opinions.get(0).priceLevels().isEmpty());
  }

  @Test
  void ignoresContextSymbolWhenTextHasNoOpinion() {
    var opinions = WxPusherFallbackOpinionExtractor.extract(
        "\u4eca\u5929\u5148\u597d\u597d\u4f11\u606f\uff0c\u665a\u4e0a\u518d\u804a",
        java.util.List.of("SOXL"));

    assertTrue(opinions.isEmpty());
  }

  private static boolean isLevel(PriceLevel level, String type) {
    return type.equals(level.levelType());
  }
}
