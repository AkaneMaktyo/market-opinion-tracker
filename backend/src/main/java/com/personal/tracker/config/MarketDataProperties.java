package com.personal.tracker.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "market-data")
public class MarketDataProperties {
  private List<String> providers = new ArrayList<>(List.of("yahoo", "bitget"));

  public List<String> providers() {
    if (providers == null) {
      return List.of("yahoo", "bitget");
    }
    List<String> normalized = providers.stream()
        .filter(item -> item != null && !item.isBlank())
        .map(item -> item.trim().toLowerCase())
        .toList();
    return normalized.isEmpty() ? List.of("yahoo", "bitget") : normalized;
  }

  public void setProviders(List<String> providers) {
    this.providers = providers;
  }
}
