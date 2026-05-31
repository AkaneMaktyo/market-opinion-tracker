package com.personal.tracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "trading.bitget")
public class BitgetTradingProperties {
  private boolean enabled;
  private boolean demo = true;
  private String baseUrl = "https://api.bitget.com";
  private String apiKey = "";
  private String apiSecret = "";
  private String passphrase = "";
  private String productType = "USDT-FUTURES";
  private String marginCoin = "USDT";

  public boolean enabled() {
    return enabled;
  }

  public boolean demo() {
    return demo;
  }

  public String baseUrl() {
    return valueOrDefault(baseUrl, "https://api.bitget.com");
  }

  public String apiKey() {
    return valueOrDefault(apiKey, "");
  }

  public String apiSecret() {
    return valueOrDefault(apiSecret, "");
  }

  public String passphrase() {
    return valueOrDefault(passphrase, "");
  }

  public String productType() {
    return valueOrDefault(productType, "USDT-FUTURES").toUpperCase();
  }

  public String marginCoin() {
    return valueOrDefault(marginCoin, "USDT").toUpperCase();
  }

  public boolean configured() {
    return present(apiKey) && present(apiSecret) && present(passphrase);
  }

  public void ensureReady() {
    if (!enabled) {
      throw new IllegalStateException("Bitget demo trading is disabled");
    }
    if (!configured()) {
      throw new IllegalStateException("Bitget demo trading credentials are incomplete");
    }
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public void setDemo(boolean demo) {
    this.demo = demo;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public void setApiSecret(String apiSecret) {
    this.apiSecret = apiSecret;
  }

  public void setPassphrase(String passphrase) {
    this.passphrase = passphrase;
  }

  public void setProductType(String productType) {
    this.productType = productType;
  }

  public void setMarginCoin(String marginCoin) {
    this.marginCoin = marginCoin;
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  private static String valueOrDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
