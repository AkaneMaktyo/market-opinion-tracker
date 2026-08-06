package com.personal.tracker.service.wxpusher.ocr;

import java.util.Base64;

final class OcrImageSource {
  private OcrImageSource() {
  }

  static boolean isRemote(String source) {
    String value = value(source);
    return value.startsWith("http://") || value.startsWith("https://");
  }

  static byte[] embeddedBytes(String source) {
    String value = value(source);
    int comma = value.indexOf(',');
    if (!value.startsWith("data:image/") || comma < 0
        || !value.substring(0, comma).toLowerCase().endsWith(";base64")) {
      throw new IllegalArgumentException("不支持的 OCR 图片来源");
    }
    try {
      byte[] image = Base64.getDecoder().decode(value.substring(comma + 1));
      validateSize(image);
      return image;
    } catch (IllegalArgumentException error) {
      throw new IllegalArgumentException("OCR Base64 图片无效: " + error.getMessage(), error);
    }
  }

  static void validateSize(byte[] image) {
    if (image == null || image.length == 0 || image.length > 10 * 1024 * 1024) {
      throw new IllegalArgumentException("OCR 图片大小必须在 1 字节到 10MB 之间");
    }
  }

  static String suffix(String source) {
    String value = value(source).toLowerCase();
    if (value.startsWith("data:image/png") || value.matches(".*\\.png(?:\\?.*)?$")) {
      return ".png";
    }
    if (value.startsWith("data:image/webp") || value.matches(".*\\.webp(?:\\?.*)?$")) {
      return ".webp";
    }
    return ".jpg";
  }

  private static String value(String input) {
    return input == null ? "" : input.trim();
  }
}
