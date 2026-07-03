package com.personal.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
  private String baseUrl = "";
  private String apiKey = "";
  private String model = "";
  private boolean wxpusherEnabled = true;
  private boolean youtubeAutoImportEnabled = false;

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

  public boolean wxpusherEnabled() {
    return wxpusherEnabled;
  }

  public boolean youtubeAutoImportEnabled() {
    return youtubeAutoImportEnabled;
  }

  public boolean sceneEnabled(String scene) {
    String normalized = normalize(scene);
    if (normalized.startsWith("YOUTUBE_")) {
      return youtubeAutoImportEnabled();
    }
    if (normalized.startsWith("WXPUSHER_")) {
      return wxpusherEnabled();
    }
    return false;
  }

  public void ensureSceneEnabled(String scene) {
    if (!sceneEnabled(scene)) {
      throw new IllegalStateException("LLM 场景未启用: " + normalize(scene));
    }
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

  public void setWxpusherEnabled(boolean wxpusherEnabled) {
    this.wxpusherEnabled = wxpusherEnabled;
  }

  public void setYoutubeAutoImportEnabled(boolean youtubeAutoImportEnabled) {
    this.youtubeAutoImportEnabled = youtubeAutoImportEnabled;
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}
