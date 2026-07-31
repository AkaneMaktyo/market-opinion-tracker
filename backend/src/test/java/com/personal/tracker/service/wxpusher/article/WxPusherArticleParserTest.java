package com.personal.tracker.service.wxpusher.article;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WxPusherArticleParserTest {
  @Test
  void preservesLazyLoadedImageUrlForOcr() {
    String html = """
        <html><body>
          <main class="article-content">
            <p>顺哥观点</p>
            <img src="data:image/gif;base64,placeholder" data-src="/images/signal.jpg?x=1&amp;y=2">
          </main>
        </body></html>
        """;

    String text = WxPusherArticleParser.parse(
        html,
        "https://wxpusher.zjiecode.com/api/message/123");

    assertEquals(
        "顺哥观点\nWXPUSHER_IMAGE_URL=https://wxpusher.zjiecode.com/images/signal.jpg?x=1&y=2",
        text);
  }

  @Test
  void preservesEmbeddedBase64ImageForOcr() {
    String html = "<main class=\"article-content\"><img src=\"data:image/png;base64,abc\"></main>";

    assertEquals("WXPUSHER_IMAGE_URL=data:image/png;base64,abc", WxPusherArticleParser.parse(
        html,
        "https://wxpusher.zjiecode.com/api/message/123"));
  }
}
