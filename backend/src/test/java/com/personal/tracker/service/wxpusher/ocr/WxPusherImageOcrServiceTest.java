package com.personal.tracker.service.wxpusher.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.config.WxPusherOcrProperties;
import org.junit.jupiter.api.Test;

class WxPusherImageOcrServiceTest {
  @Test
  void convertsOnlyTargetGroupImagesToText() {
    var properties = properties();
    var client = mock(ImageOcrClient.class);
    var service = new WxPusherImageOcrService(properties, client);
    when(client.recognize("https://img.example/signal.png"))
        .thenReturn("  BTC 看多\n\n  回踩 115000 做多 ");

    String result = service.convert(
        "顺哥 VIP 小群",
        "正文\nWXPUSHER_IMAGE_URL=https://img.example/signal.png");

    assertEquals("正文\nWXPUSHER_IMAGE_URL=https://img.example/signal.png\n"
        + "[图片转文字 1]\nBTC 看多\n回踩 115000 做多\n[/图片转文字]", result);
  }

  @Test
  void detectsTargetGroupFromArticleTextWhenBloggerUsesCanonicalName() {
    var client = mock(ImageOcrClient.class);
    var service = new WxPusherImageOcrService(properties(), client);
    when(client.recognize("https://img.example/signal.png")).thenReturn("NVDA 看多");

    String result = service.convert(
        "顺哥",
        "💎｜顺哥vip小群\nWXPUSHER_IMAGE_URL=https://img.example/signal.png");

    assertEquals(
        "💎｜顺哥vip小群\nWXPUSHER_IMAGE_URL=https://img.example/signal.png\n"
            + "[图片转文字 1]\nNVDA 看多\n[/图片转文字]",
        result);
  }

  @Test
  void leavesOtherGroupsOnOriginalPlaceholderBehavior() {
    var client = mock(ImageOcrClient.class);
    var service = new WxPusherImageOcrService(properties(), client);

    String result = service.convert(
        "其他群",
        "正文\nWXPUSHER_IMAGE_URL=https://img.example/signal.png");

    assertEquals("正文\nWXPUSHER_IMAGE_URL=https://img.example/signal.png", result);
    verify(client, never()).recognize("https://img.example/signal.png");
  }

  @Test
  void rejectsMoreImagesThanConfiguredLimit() {
    var properties = properties();
    properties.setMaxImages(1);
    var service = new WxPusherImageOcrService(properties, mock(ImageOcrClient.class));
    String detail = """
        WXPUSHER_IMAGE_URL=https://img.example/1.png
        WXPUSHER_IMAGE_URL=https://img.example/2.png
        """;

    assertThrows(IllegalStateException.class, () -> service.convert("顺哥vip小群", detail));
  }

  @Test
  void requestsRefreshForLegacyTargetImagePlaceholder() {
    var service = new WxPusherImageOcrService(
        properties(),
        mock(ImageOcrClient.class));

    assertEquals(true, service.requiresSourceRefresh("顺哥vip小群", "正文\n[图片]"));
    assertEquals(true, service.requiresSourceRefresh("顺哥", "💎｜顺哥vip小群\n[图片]"));
    assertEquals(true, service.requiresSourceRefresh(
        "顺哥vip小群",
        "[图片转文字 1]\nBTC\n[/图片转文字]"));
    assertEquals(false, service.requiresSourceRefresh("其他群", "正文\n[图片]"));
    assertEquals(false, service.requiresSourceRefresh(
        "顺哥vip小群",
        "WXPUSHER_IMAGE_URL=https://img.example/1.png"));
  }

  @Test
  void detectsStoredOcrTextBlocks() {
    var service = new WxPusherImageOcrService(
        properties(),
        mock(ImageOcrClient.class));

    assertEquals(true, service.containsOcrText("[图片转文字 1]\nBTC\n[/图片转文字]"));
    assertEquals(false, service.containsOcrText("[图片]"));
  }

  @Test
  void repeatedConversionKeepsOneOcrBlockWithoutCallingOcrAgain() {
    var client = mock(ImageOcrClient.class);
    var service = new WxPusherImageOcrService(properties(), client);
    String detail = """
        WXPUSHER_IMAGE_URL=https://img.example/signal.png
        [图片转文字 1]
        NVDA 看多
        [/图片转文字]
        [图片转文字 1]
        NVDA 看多
        [/图片转文字]
        """;

    String result = service.convert("顺哥vip小群", detail);

    assertEquals("""
        WXPUSHER_IMAGE_URL=https://img.example/signal.png
        [图片转文字 1]
        NVDA 看多
        [/图片转文字]""", result);
    verify(client, never()).recognize("https://img.example/signal.png");
  }

  private WxPusherOcrProperties properties() {
    var properties = new WxPusherOcrProperties();
    properties.setEnabled(true);
    properties.setGroupName("顺哥vip小群");
    return properties;
  }
}
