package com.personal.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
  private String baseUrl = "";
  private String apiKey = "";
  private String model = "";

  public String baseUrl() {
    return normalize(baseUrl).replaceAll("/+$", "");
  }

  public String apiKey() {
    return normalize(apiKey);
  }

  public String model() {
    return normalize(model);
  }

  public boolean configured() {
    return !baseUrl().isBlank() && !apiKey().isBlank() && !model().isBlank();
  }

  public void ensureConfigured() {
    if (!configured()) {
      throw new IllegalStateException("LLM 配置不完整，请先设置 LLM_BASE_URL、LLM_API_KEY 和 LLM_MODEL");
    }
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public void setModel(String model) {
    this.model = model;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}
