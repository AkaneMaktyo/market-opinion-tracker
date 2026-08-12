package com.personal.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "price-alert.deepseek")
public class PriceAlertDeepSeekProperties {
  public static final String MODEL = "deepseek-v4-flash";
  private String baseUrl = "https://api.deepseek.com";
  private String apiKey = "";

  public String baseUrl() {
    String value = baseUrl == null ? "" : baseUrl.trim();
    return (value.isBlank() ? "https://api.deepseek.com" : value).replaceAll("/+$", "");
  }

  public String apiKey() {
    return apiKey == null ? "" : apiKey.trim();
  }

  public boolean configured() {
    return !apiKey().isBlank();
  }

  public void ensureConfigured() {
    if (!configured()) {
      throw new IllegalStateException("智能价格提醒未配置 PRICE_ALERT_DEEPSEEK_API_KEY");
    }
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }
}
