package com.personal.tracker.service.trading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.config.BitgetTradingProperties;
import com.personal.tracker.repository.ExchangeCredentialRepository;
import com.personal.tracker.repository.ExchangeCredentialRepository.ExchangeCredential;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URLEncoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class BitgetDemoClient {
  private static final Logger log = LoggerFactory.getLogger(BitgetDemoClient.class);
  private static final String SUCCESS = "00000";

  private final BitgetTradingProperties properties;
  private final ExchangeCredentialRepository exchangeCredentials;
  private final ObjectMapper mapper;
  private final RestClient client;

  public BitgetDemoClient(
      BitgetTradingProperties properties,
      ExchangeCredentialRepository exchangeCredentials,
      ObjectMapper mapper,
      @Value("${HTTP_PROXY_URL:}") String proxyUrl) {
    this.properties = properties;
    this.exchangeCredentials = exchangeCredentials;
    this.mapper = mapper;
    this.client = RestClient.builder()
        .baseUrl(properties.baseUrl())
        .requestFactory(requestFactory(proxyUrl))
        .build();
  }

  public TradingStatus status() {
    ResolvedCredential credential = resolveCredential(false);
    return new TradingStatus(
        credential.enabled(),
        credential.configured(),
        credential.demo(),
        credential.productType(),
        credential.marginCoin(),
        properties.baseUrl(),
        credential.source());
  }

  public BitgetResponse accounts() {
    ResolvedCredential credential = resolveCredential(true);
    return get(credential, "/api/v2/mix/account/accounts", Map.of(
        "productType", credential.productType()));
  }

  public BitgetResponse positions() {
    ResolvedCredential credential = resolveCredential(true);
    return get(credential, "/api/v2/mix/position/all-position", Map.of(
        "productType", credential.productType(),
        "marginCoin", credential.marginCoin()));
  }

  public BitgetResponse openOrders() {
    ResolvedCredential credential = resolveCredential(true);
    return get(credential, "/api/v2/mix/order/orders-pending", Map.of(
        "productType", credential.productType()));
  }

  private BitgetResponse get(
      ResolvedCredential credential,
      String path,
      Map<String, String> params) {
    String query = queryString(params);
    String requestPath = query.isBlank() ? path : path + "?" + query;
    String timestamp = String.valueOf(Instant.now().toEpochMilli());
    SignedHeaders signedHeaders = signedHeaders(credential, timestamp, requestPath);
    JsonNode root = request(requestPath, signedHeaders);
    return parseResponse(root);
  }

  private JsonNode request(String requestPath, SignedHeaders signedHeaders) {
    try {
      return client.method(HttpMethod.GET)
          .uri(requestPath)
          .headers(headers -> applyHeaders(headers::set, signedHeaders))
          .retrieve()
          .body(JsonNode.class);
    } catch (RestClientResponseException error) {
      JsonNode body = readJson(error.getResponseBodyAsByteArray());
      if (body != null) {
        return body;
      }
      throw new BitgetClientException("Bitget request failed: " + error.getMessage());
    } catch (RuntimeException error) {
      log.warn("Bitget request error on {}: {}", requestPath, error.getMessage());
      throw new BitgetClientException("Bitget request error: " + error.getMessage());
    }
  }

  private BitgetResponse parseResponse(JsonNode root) {
    if (root == null) {
      throw new BitgetClientException("Bitget returned an empty response");
    }
    String code = root.path("code").asText();
    String message = root.path("msg").asText();
    JsonNode data = root.path("data");
    if (!SUCCESS.equals(code)) {
      throw new BitgetClientException("Bitget error: " + code + " " + message);
    }
    return new BitgetResponse(code, message, data);
  }

  private String sign(String secret, String timestamp, String method, String requestPath, String body) {
    try {
      String payload = timestamp + method + requestPath + body;
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception error) {
      throw new BitgetClientException("Bitget signing failed: " + error.getMessage());
    }
  }

  private SignedHeaders signedHeaders(
      ResolvedCredential credential,
      String timestamp,
      String requestPath) {
    return new SignedHeaders(
        credential.apiKey(),
        sign(credential.apiSecret(), timestamp, "GET", requestPath, ""),
        timestamp,
        credential.passphrase(),
        credential.demo() ? "1" : "");
  }

  private static void applyHeaders(HeaderSetter setter, SignedHeaders signedHeaders) {
    setter.set("ACCESS-KEY", signedHeaders.apiKey());
    setter.set("ACCESS-SIGN", signedHeaders.signature());
    setter.set("ACCESS-TIMESTAMP", signedHeaders.timestamp());
    setter.set("ACCESS-PASSPHRASE", signedHeaders.passphrase());
    setter.set("locale", "en-US");
    if (!signedHeaders.paperTrading().isBlank()) {
      setter.set("paptrading", signedHeaders.paperTrading());
    }
  }

  private static SimpleClientHttpRequestFactory requestFactory(String proxyUrl) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(8));
    factory.setReadTimeout(Duration.ofSeconds(15));
    Proxy proxy = proxy(proxyUrl);
    if (proxy != null) {
      factory.setProxy(proxy);
      log.info("Bitget trading client using proxy {}", proxy.address());
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
      log.warn("Ignoring invalid Bitget proxy url: {}", raw);
      return null;
    }
  }

  private static String queryString(Map<String, String> params) {
    Map<String, String> ordered = new LinkedHashMap<>(params);
    return ordered.entrySet().stream()
        .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
        .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
        .reduce((left, right) -> left + "&" + right)
        .orElse("");
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
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

  private ResolvedCredential resolveCredential(boolean required) {
    ResolvedCredential credential = exchangeCredentials
        .findActive("bitget", "classic", "demo")
        .map(this::fromDatabase)
        .orElseGet(this::fromEnvironment);
    if (!required) {
      return credential;
    }
    if (!credential.enabled()) {
      throw new IllegalStateException("Bitget demo trading is disabled");
    }
    if (!credential.configured()) {
      throw new IllegalStateException("Bitget demo trading credentials are incomplete");
    }
    return credential;
  }

  private ResolvedCredential fromDatabase(ExchangeCredential credential) {
    ResolvedCredential resolved = new ResolvedCredential(
        true,
        present(credential.apiKey()) && present(credential.apiSecret()) && present(credential.passphrase()),
        "demo".equalsIgnoreCase(credential.environment()),
        value(credential.apiKey(), ""),
        value(credential.apiSecret(), ""),
        value(credential.passphrase(), ""),
        value(credential.productType(), properties.productType()).toUpperCase(),
        value(credential.marginCoin(), properties.marginCoin()).toUpperCase(),
        "database");
    return resolved.configured() ? resolved : fromEnvironment();
  }

  private ResolvedCredential fromEnvironment() {
    return new ResolvedCredential(
        properties.enabled(),
        properties.configured(),
        properties.demo(),
        properties.apiKey(),
        properties.apiSecret(),
        properties.passphrase(),
        properties.productType(),
        properties.marginCoin(),
        "environment");
  }

  private static String value(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  public record TradingStatus(
      boolean enabled,
      boolean configured,
      boolean demo,
      String productType,
      String marginCoin,
      String baseUrl,
      String source) {
  }

  public record BitgetResponse(String code, String message, JsonNode data) {
  }

  private record SignedHeaders(
      String apiKey,
      String signature,
      String timestamp,
      String passphrase,
      String paperTrading) {
  }

  private record ResolvedCredential(
      boolean enabled,
      boolean configured,
      boolean demo,
      String apiKey,
      String apiSecret,
      String passphrase,
      String productType,
      String marginCoin,
      String source) {
  }

  private interface HeaderSetter {
    void set(String name, String value);
  }

  public static class BitgetClientException extends RuntimeException {
    public BitgetClientException(String message) {
      super(message);
    }
  }
}
