package com.personal.tracker.service.wxpusher.instruments;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MessageInstrumentExtractorTest {
  @Test
  void extractsLikelySymbolsAndIgnoresCommonNoise() {
    List<String> symbols = MessageInstrumentExtractor.extract(
        "NVDA 和 AMZN 受 AI 需求影响，VIP 群提到 AWS，CIA-信息推送，另有 $CRWV +5%");

    assertEquals(List.of("NVDA", "AMZN", "CRWV"), symbols);
  }

  @Test
  void ignoresEnglishTradingNoiseAndCompanyNames() {
    List<String> symbols = MessageInstrumentExtractor.extract(
        "$GOLD BUY NOW TP SL GOOD TEAM PREMIUM NVIDIA TSMC SELL\n"
            + "PCB/CCL 和 CF-H 产业链观察\n"
            + "LRCX -6% 泛林集团回落");

    assertEquals(List.of("GOLD", "LRCX"), symbols);
  }
  @Test
  void extractsMixedCaseSymbolsFromChineseMessages() {
    List<String> symbols = MessageInstrumentExtractor.extract("今天可以慢慢小仓位开始买Mu soxl 正股");

    assertEquals(List.of("MU", "SOXL"), symbols);
  }

  @Test
  void extractsTickerWithPriceFromChineseMessages() {
    List<String> symbols = MessageInstrumentExtractor.extract("""
        Qcom 190 to 226 \u4eca\u5929\u3002\u606d\u559c\u53d1\u8d22
        \u987a\u54e5\u3002Mu 1200 \u4ee5\u4e0a\u98ce\u9669\u5f88\u5927
        \u987a\u54e5\u3002Nke 45 \u5c0f\u8d5a\u94b1\u76c8\u5229\u8dd1\u8def\u4e86\uff0c\u9700\u8981\u8d44\u91d1\u641esoxl
        """);

    assertEquals(List.of("QCOM", "MU", "NKE", "SOXL"), symbols);
  }

  @Test
  void ignoresLongMixedCaseProductNames() {
    List<String> symbols = MessageInstrumentExtractor.extract("NVDA 新一代Kyber AI机架推迟，市场关注供应链");

    assertEquals(List.of("NVDA"), symbols);
  }

  @Test
  void extractsChineseAliasesWithoutExplicitTicker() {
    List<String> symbols = MessageInstrumentExtractor.extract("""
        美光强支撑到了
        黄金立即卖出
        以太坊方向：做空
        """);

    assertEquals(Set.of("MU", "GOLD", "ETH"), Set.copyOf(symbols));
  }

  @Test
  void extractsUtf8ChineseAliasesFromMarketContext() {
    List<String> symbols = MessageInstrumentExtractor.extract(
        "\u82f9\u679c\u5c06\u589e\u52a0\u4e0e\u535a\u901a\u7684\u652f\u51fa\uff0c"
            + "\u82f1\u4f1f\u8fbe\u548c\u53f0\u79ef\u7535\u9700\u6c42\u63d0\u5347");

    assertEquals(Set.of("AAPL", "AVGO", "NVDA", "TSM"), Set.copyOf(symbols));
  }

  @Test
  void ignoresUtf8ChineseAliasesWithoutMarketContext() {
    List<String> symbols = MessageInstrumentExtractor.extract(
        "\u9ec4\u91d1\u9891\u9053\u66f4\u65b0\uff0c\u4eca\u665a\u804a\u5929\u5185\u5bb9\u6574\u7406");

    assertEquals(List.of(), symbols);
  }

  @Test
  void ignoresAliasLinesWithoutMarketContext() {
    List<String> symbols = MessageInstrumentExtractor.extract("黄金频道更新：今晚聊天内容整理");

    assertEquals(List.of(), symbols);
  }

  @Test
  void extractsSymbolsFromMarketNewsContext() {
    List<String> symbols = MessageInstrumentExtractor.extract(
        "WMT 美国通胀担忧升温，沃尔玛降价举措利好民众，零售商跟进让利");

    assertEquals(List.of("WMT"), symbols);
  }

  @Test
  void normalizesGoldAliasesAndIgnoresProductWords() {
    List<String> symbols = MessageInstrumentExtractor.extract(
        "XAU +160 pips running now\nNVDA Vera ASIC 服务器需求提升，市场关注AMD");

    assertEquals(Set.of("GOLD", "NVDA", "AMD"), Set.copyOf(symbols));
  }

  @Test
  void extractsCryptoShorthandAndIgnoresDiscordDotCom() {
    List<String> symbols = MessageInstrumentExtractor.extract(
        "目前0.8X B 62900、0.15X E，1778、0.2X BNB 580.3、0.25X 黄金，4119\n"
            + "GOLD SELL NOW @ 4081 https://discord.com/channels/1\n"
            + "I did not all in, let it wait");

    assertEquals(Set.of("BTC", "ETH", "BNB", "GOLD"), Set.copyOf(symbols));
  }

  @Test
  void ignoresValuationIndicatorAndProductTerms() {
    List<String> symbols = MessageInstrumentExtractor.extract(
        "SPY CAPE估值过高，KDJ和RSI还没有进入超卖区\n"
            + "ASML EUV订单增加利好盘面，INTC Xeon涨价带动走强，PLAN需要等待");

    assertEquals(Set.of("SPY", "ASML", "INTC"), Set.copyOf(symbols));
  }

  @Test
  void ignoresEnglishSentenceFragmentsAroundRealSymbols() {
    List<String> symbols = MessageInstrumentExtractor.extract(
        "SOXL BUY 157 to 213, 30 percent up in 3 days\n"
        + "ADBE PLTR buy, but most position already sell out\n"
        + "Trump Taco, SOXL buy rebound");

    assertEquals(Set.of("SOXL", "ADBE", "PLTR"), Set.copyOf(symbols));
  }
}
