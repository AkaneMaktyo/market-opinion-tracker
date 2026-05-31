package com.personal.tracker.service.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    var parser = new FlexibleJsonOpinionParser(new ObjectMapper());

    var preview = parser.parse(rawJson);

    assertEquals(1, preview.candidates().size());
    assertEquals("CRYPTO", preview.candidates().get(0).market());
    assertEquals("BTC", preview.candidates().get(0).symbol());
  }
}
