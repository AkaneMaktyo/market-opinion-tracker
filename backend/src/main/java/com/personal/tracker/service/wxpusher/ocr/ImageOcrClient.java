package com.personal.tracker.service.wxpusher.ocr;

import com.personal.tracker.config.WxPusherOcrProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ImageOcrClient {
  private static final Logger log = LoggerFactory.getLogger(ImageOcrClient.class);
  private final WxPusherOcrProperties properties;
  private final TesseractOcrClient tesseract;
  private final AliyunGeneralOcrClient aliyun;

  public ImageOcrClient(
      WxPusherOcrProperties properties,
      TesseractOcrClient tesseract,
      AliyunGeneralOcrClient aliyun) {
    this.properties = properties;
    this.tesseract = tesseract;
    this.aliyun = aliyun;
  }

  public String recognize(String imageSource) {
    return switch (properties.provider()) {
      case "tesseract" -> tesseract.recognize(imageSource);
      case "aliyun" -> aliyun.recognize(imageSource);
      case "auto" -> recognizeWithFallback(imageSource);
      default -> throw new IllegalStateException("不支持的 OCR 提供方: " + properties.provider());
    };
  }

  private String recognizeWithFallback(String imageSource) {
    try {
      String text = aliyun.recognize(imageSource);
      log.info("图片 OCR 使用阿里云完成，图片={}", sourceLabel(imageSource));
      return text;
    } catch (IllegalStateException error) {
      log.warn("阿里云 OCR 失败({})，降级使用 Tesseract，图片={}",
          error.getMessage(), sourceLabel(imageSource));
      return tesseract.recognize(imageSource);
    }
  }

  private static String sourceLabel(String imageSource) {
    String source = imageSource == null ? "" : imageSource.trim();
    if (source.startsWith("data:image/")) {
      return "内嵌图片(" + source.length() + "字符)";
    }
    return source;
  }
}
