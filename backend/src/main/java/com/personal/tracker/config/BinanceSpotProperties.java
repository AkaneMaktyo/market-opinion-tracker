package com.personal.tracker.config;

import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "trading.binance")
public class BinanceSpotProperties {
  private boolean enabled;
  private boolean paper = true;
  private String environment = "testnet";
  private String baseUrl = "";
  private String proxyUrl = "";
  private String apiKey = "";
  private String apiSecret = "";
  private String keyType = "HMAC";
  private String privateKeyPath = "";
  private String privateKeyPassphrase = "";
  private long recvWindow = 5000;

  public boolean enabled() {
    return enabled;
  }

  public boolean paper() {
    return paper;
  }

  public String environment() {
    return value(environment, "testnet").toLowerCase(Locale.ROOT);
  }

  public String baseUrl() {
    if (baseUrl != null && !baseUrl.isBlank()) {
      return baseUrl.trim().replaceAll("/+$", "");
    }
    return "mainnet".equals(environment())
        ? "https://api.binance.com"
        : "https://testnet.binance.vision";
  }

  public String apiKey() {
    return value(apiKey, "");
  }

  public String proxyUrl() {
    return value(proxyUrl, "");
  }

  public String apiSecret() {
    return value(apiSecret, "");
  }

  public String keyType() {
    return value(keyType, "HMAC").toUpperCase(Locale.ROOT);
  }

  public String privateKeyPath() {
    return value(privateKeyPath, "");
  }

  public String privateKeyPassphrase() {
    return privateKeyPassphrase == null ? "" : privateKeyPassphrase;
  }

  public long recvWindow() {
    return Math.min(Math.max(recvWindow, 1000), 60000);
  }

  public boolean configured() {
    if (apiKey().isBlank()) {
      return false;
    }
    return switch (keyType()) {
      case "HMAC" -> !apiSecret().isBlank();
      case "RSA" -> !privateKeyPath().isBlank();
      default -> false;
    };
  }

  public boolean liveReady() {
    return enabled && !paper && configured();
  }

  public void ensureTradingReady() {
    if (paper) {
      return;
    }
    if (!enabled) {
      throw new IllegalStateException("币安现货交易未启用");
    }
    if (!configured()) {
      throw new IllegalStateException(configurationMessage());
    }
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public void setPaper(boolean paper) {
    this.paper = paper;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public void setProxyUrl(String proxyUrl) {
    this.proxyUrl = proxyUrl;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public void setApiSecret(String apiSecret) {
    this.apiSecret = apiSecret;
  }

  public void setKeyType(String keyType) {
    this.keyType = keyType;
  }

  public void setPrivateKeyPath(String privateKeyPath) {
    this.privateKeyPath = privateKeyPath;
  }

  public void setPrivateKeyPassphrase(String privateKeyPassphrase) {
    this.privateKeyPassphrase = privateKeyPassphrase;
  }

  public void setRecvWindow(long recvWindow) {
    this.recvWindow = recvWindow;
  }

  private static String value(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String configurationMessage() {
    return switch (keyType()) {
      case "RSA" -> "币安 RSA API Key 或私钥路径未配置完整";
      case "HMAC" -> "币安现货 API Key 或 Secret 未配置";
      default -> "不支持的币安签名类型：" + keyType();
    };
  }
}
