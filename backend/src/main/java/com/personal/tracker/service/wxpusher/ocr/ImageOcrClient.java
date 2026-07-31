package com.personal.tracker.service.wxpusher.ocr;

import com.personal.tracker.config.WxPusherOcrProperties;
import org.springframework.stereotype.Component;

@Component
public class ImageOcrClient {
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
      default -> throw new IllegalStateException("不支持的 OCR 提供方: " + properties.provider());
    };
  }
}
