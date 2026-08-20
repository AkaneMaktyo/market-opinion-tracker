package com.personal.tracker.config.celebrity;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class CelebrityHttpClientFactory {
  private final CelebrityDataProperties properties;

  public CelebrityHttpClientFactory(CelebrityDataProperties properties) {
    this.properties = properties;
  }

  public HttpClient create() {
    HttpClient.Builder builder = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(12))
        .followRedirects(HttpClient.Redirect.NORMAL);
    proxyAddress(properties.proxyUrl()).ifPresent(address -> builder.proxy(ProxySelector.of(address)));
    return builder.build();
  }

  private static java.util.Optional<InetSocketAddress> proxyAddress(String raw) {
    if (raw == null || raw.isBlank()) {
      return java.util.Optional.empty();
    }
    try {
      URI uri = URI.create(raw.trim());
      if (uri.getHost() == null) {
        return java.util.Optional.empty();
      }
      int port = uri.getPort() > 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
      return java.util.Optional.of(InetSocketAddress.createUnresolved(uri.getHost(), port));
    } catch (IllegalArgumentException error) {
      return java.util.Optional.empty();
    }
  }
}
