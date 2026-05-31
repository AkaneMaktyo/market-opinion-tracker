package com.personal.tracker.service.wxpusher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class WxPusherClientTest {
  @Test
  void findsNestedRestMessageList() throws Exception {
    var client = new WxPusherClient(new ObjectMapper());
    var root = new ObjectMapper().readTree("""
        {
          "payload": {
            "records": [
              { "messageId": "1", "title": "A" },
              { "messageId": "2", "title": "B" }
            ]
          }
        }
        """);

    @SuppressWarnings("unchecked")
    List<Object> items = (List<Object>) findListMethod().invoke(client, root);

    assertEquals(2, items.size());
  }

  private Method findListMethod() throws Exception {
    Method method = WxPusherClient.class.getDeclaredMethod(
        "findList",
        com.fasterxml.jackson.databind.JsonNode.class);
    method.setAccessible(true);
    return method;
  }
}
