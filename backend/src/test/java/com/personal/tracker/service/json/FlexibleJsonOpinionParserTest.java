package com.personal.tracker.service.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.service.positions.PositionActionResolver;
import org.junit.jupiter.api.Test;

class FlexibleJsonOpinionParserTest {
  @Test
  void keepsMarketFromJsonOutput() throws Exception {
    String rawJson = """
        {
          "总体摘要": {
            "主线": "加密偏强"
          },
          "按具体品种划分": [
            {
              "品种": "比特币",
              "代码": "BTC",
              "市场": "CRYPTO",
              "方向": "看多",
              "周期": "短线",
              "关键判断": "强势延续",
              "催化": [],
              "触发条件": "突破前高",
              "风险": [],
              "关键价位": ["70000"],
              "原文摘录": "比特币继续看多"
            }
          ],
          "待确认映射": {}
        }
        """;
    var parser = new FlexibleJsonOpinionParser(new ObjectMapper(), new PositionActionResolver());

    var preview = parser.parse(rawJson);

    assertEquals(1, preview.candidates().size());
    assertEquals("CRYPTO", preview.candidates().get(0).market());
    assertEquals("BTC", preview.candidates().get(0).symbol());
  }

  @Test
  void resolvesPositionActionsOnlyForExplicitActions() throws Exception {
    String rawJson = """
        {
          "按具体品种划分": [
            {"品种":"英伟达","代码":"NVDA","方向":"看空","关键判断":"估值承压"},
            {"品种":"特斯拉","代码":"TSLA","方向":"看多","关键判断":"今天买入观察仓"},
            {"品种":"苹果","代码":"AAPL","方向":"看多","持仓动作":"CLOSE","关键判断":"卖出锁定利润"}
          ]
        }
        """;
    var parser = new FlexibleJsonOpinionParser(new ObjectMapper(), new PositionActionResolver());

    var preview = parser.parse(rawJson);

    assertEquals("IGNORE", preview.candidates().get(0).positionAction());
    assertEquals("OPEN", preview.candidates().get(1).positionAction());
    assertEquals("CLOSE", preview.candidates().get(2).positionAction());
  }
}
