package com.personal.tracker.service.wxpusher;

public final class WxPusherMessageKey {
  private WxPusherMessageKey() {
  }

  public static String build(
      String sourceUrl,
      String detailUrl,
      String channel,
      String fallbackId,
      String summary) {
    if (hasText(sourceUrl)) {
      return "wxpusher:src:" + sourceUrl.trim();
    }
    if (hasText(detailUrl)) {
      return "wxpusher:detail:" + detailUrl.trim();
    }
    if (hasText(fallbackId)) {
      return "wxpusher:" + channel + ":" + fallbackId.trim();
    }
    return "wxpusher:" + channel + ":" + Integer.toHexString((summary == null ? "" : summary).hashCode());
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
