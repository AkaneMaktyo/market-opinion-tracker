package com.personal.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "wxpusher.ocr")
public class WxPusherOcrProperties {
  private boolean enabled = true;
  private String groupName = "顺哥vip小群";
  private String provider = "auto";
  private String endpoint = "ocr-api.cn-hangzhou.aliyuncs.com";
  private int maxImages = 10;
  private String tesseractCommand = "tesseract";
  private String tesseractLanguage = "chi_sim+eng";

  public boolean enabled() {
    return enabled;
  }

  public String groupName() {
    return value(groupName, "顺哥vip小群");
  }

  public String endpoint() {
    return value(endpoint, "ocr-api.cn-hangzhou.aliyuncs.com");
  }

  public String provider() {
    return value(provider, "auto").toLowerCase();
  }

  public int maxImages() {
    return Math.max(1, Math.min(maxImages, 30));
  }

  public String tesseractCommand() {
    return value(tesseractCommand, "tesseract");
  }

  public String tesseractLanguage() {
    return value(tesseractLanguage, "chi_sim+eng");
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public void setGroupName(String groupName) {
    this.groupName = groupName;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public void setMaxImages(int maxImages) {
    this.maxImages = maxImages;
  }

  public void setTesseractCommand(String tesseractCommand) {
    this.tesseractCommand = tesseractCommand;
  }

  public void setTesseractLanguage(String tesseractLanguage) {
    this.tesseractLanguage = tesseractLanguage;
  }

  private static String value(String input, String fallback) {
    return input == null || input.isBlank() ? fallback : input.trim();
  }
}
