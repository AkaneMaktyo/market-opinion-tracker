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
}
