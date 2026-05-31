package com.personal.tracker.service.trading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.config.BitgetTradingProperties;
import com.personal.tracker.repository.ExchangeCredentialRepository;
import com.personal.tracker.repository.ExchangeCredentialRepository.ExchangeCredential;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class BitgetDemoClient {
  private static final String SUCCESS = "00000";

  private final BitgetTradingProperties properties;
  private final ExchangeCredentialRepository exchangeCredentials;
  private final ObjectMapper mapper;
  private final RestClient client;

  public BitgetDemoClient(
      BitgetTradingProperties properties,
      ExchangeCredentialRepository exchangeCredentials,
      ObjectMapper mapper) {
    this.properties = properties;
    this.exchangeCredentials = exchangeCredentials;
    this.mapper = mapper;
    this.client = RestClient.builder()
        .baseUrl(properties.baseUrl())
        .requestFactory(requestFactory())
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
    JsonNode root = requestWithJava(requestPath, signedHeaders);
    if (root == null) {
      root = requestWithPowerShell(requestPath, signedHeaders);
    }
    return parseResponse(root);
  }

  private JsonNode requestWithJava(String requestPath, SignedHeaders signedHeaders) {
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
      return null;
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

  private JsonNode requestWithPowerShell(String requestPath, SignedHeaders signedHeaders) {
    Path output = null;
    try {
      output = Files.createTempFile("bitget-private-", ".json");
      String command = """
          $ProgressPreference = 'SilentlyContinue';
          [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new();
          $headers = @{
            'ACCESS-KEY' = $env:BG_ACCESS_KEY;
            'ACCESS-SIGN' = $env:BG_ACCESS_SIGN;
            'ACCESS-TIMESTAMP' = $env:BG_ACCESS_TIMESTAMP;
            'ACCESS-PASSPHRASE' = $env:BG_ACCESS_PASSPHRASE;
            'locale' = 'en-US';
          };
          if (-not [string]::IsNullOrWhiteSpace($env:BG_PAPER_TRADING)) {
            $headers['paptrading'] = $env:BG_PAPER_TRADING;
          }
          try {
            (Invoke-WebRequest -Uri $env:BG_URL -Headers $headers -TimeoutSec 20 -UseBasicParsing).Content;
          } catch {
            if ($_.ErrorDetails -ne $null -and -not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) {
              $_.ErrorDetails.Message;
              exit 0;
            }
            throw;
          }
          """;
      ProcessBuilder builder = new ProcessBuilder(
          "powershell",
          "-NoProfile",
          "-ExecutionPolicy",
          "Bypass",
          "-Command",
          command)
          .redirectErrorStream(true)
          .redirectOutput(output.toFile());
      applyEnvironment(builder.environment(), signedHeaders);
      builder.environment().put("BG_URL", properties.baseUrl() + requestPath);
      Process process = builder.start();
      CompletableFuture<Integer> exitCode = CompletableFuture.supplyAsync(() -> waitFor(process));
      try {
        if (exitCode.get(25, TimeUnit.SECONDS) != 0) {
          return null;
        }
        return readJson(Files.readAllBytes(output));
      } finally {
        if (process.isAlive()) {
          process.destroyForcibly();
        }
      }
    } catch (TimeoutException error) {
      return null;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      return null;
    } catch (Exception error) {
      return null;
    } finally {
      if (output != null) {
        try {
          Files.deleteIfExists(output);
        } catch (IOException ignored) {
        }
      }
    }
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

  private static void applyEnvironment(Map<String, String> environment, SignedHeaders signedHeaders) {
    environment.put("BG_ACCESS_KEY", signedHeaders.apiKey());
    environment.put("BG_ACCESS_SIGN", signedHeaders.signature());
    environment.put("BG_ACCESS_TIMESTAMP", signedHeaders.timestamp());
    environment.put("BG_ACCESS_PASSPHRASE", signedHeaders.passphrase());
    environment.put("BG_PAPER_TRADING", signedHeaders.paperTrading());
  }

  private static SimpleClientHttpRequestFactory requestFactory() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(8));
    factory.setReadTimeout(Duration.ofSeconds(15));
    return factory;
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

  private static int waitFor(Process process) {
    try {
      return process.waitFor();
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      return -1;
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
