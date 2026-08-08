package com.personal.tracker.service.market.bitget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class BitgetPublicApiClient {
  private static final Logger log = LoggerFactory.getLogger(BitgetPublicApiClient.class);
  private static final String BASE_URL = "https://api.bitget.com";
  private final ObjectMapper mapper;
  private final RestClient client;

  public BitgetPublicApiClient(ObjectMapper mapper, @Value("${HTTP_PROXY_URL:}") String proxyUrl) {
    this.mapper = mapper;
    this.client = RestClient.builder()
        .baseUrl(BASE_URL)
        .requestFactory(requestFactory(proxyUrl))
        .build();
  }

  public JsonNode get(String uri) {
    try {
      return client.get().uri(uri).retrieve().body(JsonNode.class);
    } catch (RestClientResponseException error) {
      JsonNode body = readJson(error.getResponseBodyAsByteArray());
      if (body != null) {
        return body;
      }
      log.debug("Bitget Java client status error, trying curl fallback: {}", error.getMessage());
      return getWithCurl(BASE_URL + uri);
    } catch (RuntimeException error) {
      log.debug("Bitget Java client failed, trying curl fallback: {}", error.getMessage());
      return getWithCurl(BASE_URL + uri);
    }
  }

  private SimpleClientHttpRequestFactory requestFactory(String proxyUrl) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(8));
    factory.setReadTimeout(Duration.ofSeconds(15));
    Proxy proxy = proxy(proxyUrl);
    if (proxy != null) {
      factory.setProxy(proxy);
    }
    return factory;
  }

  private static Proxy proxy(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      URI uri = URI.create(raw.contains("://") ? raw : "http://" + raw);
      int port = uri.getPort() == -1 ? 7897 : uri.getPort();
      return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(uri.getHost(), port));
    } catch (IllegalArgumentException error) {
      return null;
    }
  }

  JsonNode getWithCurl(String url) {
    Path output = null;
    try {
      output = Files.createTempFile("bitget-public-", ".json");
      Process process = new ProcessBuilder(
          "curl", "--silent", "--show-error", "--location", "--max-time", "15", url)
          .redirectErrorStream(true)
          .redirectOutput(output.toFile())
          .start();
      CompletableFuture<Integer> exitCode = CompletableFuture.supplyAsync(() -> waitFor(process));
      try {
        if (exitCode.get(20, TimeUnit.SECONDS) != 0) {
          return null;
        }
        return readJson(Files.readAllBytes(output));
      } finally {
        if (process.isAlive()) {
          process.destroyForcibly();
        }
      }
    } catch (TimeoutException error) {
      log.debug("Bitget curl fallback timed out: {}", url);
      return null;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      return null;
    } catch (Exception error) {
      log.debug("Bitget curl fallback failed: {}", error.getMessage());
      return null;
    } finally {
      delete(output);
    }
  }

  private JsonNode readJson(byte[] data) {
    if (data == null || data.length == 0) {
      return null;
    }
    try {
      return mapper.readTree(data);
    } catch (IOException error) {
      return null;
    }
  }

  private int waitFor(Process process) {
    try {
      return process.waitFor();
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      return -1;
    }
  }

  private void delete(Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException error) {
      log.debug("Bitget temp file cleanup failed: {}", error.getMessage());
    }
  }
}
