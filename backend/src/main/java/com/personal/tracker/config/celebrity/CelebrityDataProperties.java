package com.personal.tracker.config.celebrity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "celebrity-data")
public class CelebrityDataProperties {
  private boolean enabled = true;
  private boolean startupSyncEnabled = true;
  private boolean arkEnabled = true;
  private int historyLimit = 8;
  private int symbolResolutionLimit = 24;
  private String secUserAgent = "";
  private String proxyUrl = "";
  private String arkHoldingsUrl = "https://assets.ark-funds.com/fund-documents/funds-etf-csv/"
      + "ARK_INNOVATION_ETF_ARKK_HOLDINGS.csv";

  public boolean enabled() {
    return enabled;
  }

  public boolean startupSyncEnabled() {
    return startupSyncEnabled;
  }

  public boolean arkEnabled() {
    return arkEnabled;
  }

  public int historyLimit() {
    return Math.min(Math.max(historyLimit, 2), 12);
  }

  public int symbolResolutionLimit() {
    return Math.min(Math.max(symbolResolutionLimit, 0), 80);
  }

  public String secUserAgent() {
    return value(secUserAgent);
  }

  public boolean secConfigured() {
    return !secUserAgent().isBlank();
  }

  public String arkHoldingsUrl() {
    return value(arkHoldingsUrl);
  }

  public String proxyUrl() {
    return value(proxyUrl);
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public void setStartupSyncEnabled(boolean startupSyncEnabled) {
    this.startupSyncEnabled = startupSyncEnabled;
  }

  public void setArkEnabled(boolean arkEnabled) {
    this.arkEnabled = arkEnabled;
  }

  public void setHistoryLimit(int historyLimit) {
    this.historyLimit = historyLimit;
  }

  public void setSymbolResolutionLimit(int symbolResolutionLimit) {
    this.symbolResolutionLimit = symbolResolutionLimit;
  }

  public void setSecUserAgent(String secUserAgent) {
    this.secUserAgent = secUserAgent;
  }

  public void setProxyUrl(String proxyUrl) {
    this.proxyUrl = proxyUrl;
  }

  public void setArkHoldingsUrl(String arkHoldingsUrl) {
    this.arkHoldingsUrl = arkHoldingsUrl;
  }

  private static String value(String raw) {
    return raw == null ? "" : raw.trim();
  }
}
