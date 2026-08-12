package com.personal.tracker.service.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/** 极光推送客户端：服务端触发，经极光/厂商通道送达手机通知栏。 */
@Service
public class JpushPushClient {
  private static final Logger log = LoggerFactory.getLogger(JpushPushClient.class);
  private static final String PUSH_URL = "https://api.jpush.cn/v3/push";
  private static final int DEFAULT_TIME_TO_LIVE_SECONDS = 259_200;
  private static final int MAX_TIME_TO_LIVE_SECONDS = 864_000;
  private static final String ANDROID_INTENT_PREFIX = "intent:#Intent;action=android.intent.action.MAIN;"
      + "component=com.personal.marketopiniontracker/com.personal.marketopiniontracker.MainActivity;S.messageId=";
  private final Environment environment;
  private final ObjectMapper mapper;
  private final HttpClient http;

  @Autowired
  public JpushPushClient(Environment environment, ObjectMapper mapper) {
    this(
        environment,
        mapper,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
  }

  JpushPushClient(Environment environment, ObjectMapper mapper, HttpClient http) {
    this.environment = environment;
    this.mapper = mapper;
    this.http = http;
  }

  public boolean isConfigured() {
    return !appKey().isBlank() && !masterSecret().isBlank() && !alias().isBlank();
  }

  public PushResult push(String title, String content, String messageId) {
    if (!isConfigured()) {
      return new PushResult(false, "WAITING_CONFIG", "极光推送未配置");
    }
    try {
      Map<String, Object> payload = Map.of(
          "platform", List.of("android"),
          "audience", Map.of("alias", List.of(alias())),
          "notification", Map.of(
              "android", Map.of(
                  "alert", value(content),
                  "title", value(title),
                  "intent", Map.of("url", notificationIntent(messageId)),
                  "extras", Map.of("messageId", value(messageId)))),
          "options", Map.of(
              "apns_production", false,
              "time_to_live", timeToLiveSeconds()));
      HttpRequest request = HttpRequest.newBuilder(URI.create(pushUrl()))
          .header("Content-Type", "application/json")
          .header("Authorization", "Basic " + credentials())
          .timeout(Duration.ofSeconds(10))
          .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
          .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      PushResult result = parse(response.statusCode(), response.body());
      log.info("极光推送 [{}] msg={} status={} error={}", title, messageId, result.status(), result.error());
      return result;
    } catch (IOException | RuntimeException error) {
      log.warn("极光推送失败 [{}] msg={}: {}", title, messageId, error.getMessage());
      return new PushResult(false, "FAILED", error.getMessage());
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      log.warn("极光推送被中断 [{}] msg={}", title, messageId);
      return new PushResult(false, "FAILED", "推送被中断");
    }
  }

  private PushResult parse(int statusCode, String body) {
    if (body == null || body.isBlank()) {
      return new PushResult(false, "FAILED", "极光返回为空（HTTP " + statusCode + "）");
    }
    try {
      JsonNode root = mapper.readTree(body);
      long sent = root.path("sendno").asLong(0);
      long msgId = root.path("msg_id").asLong(0);
      if (root.path("error").isObject()) {
        JsonNode error = root.path("error");
        return new PushResult(false, "FAILED", error.path("message").asText(body));
      }
      if (statusCode < 200 || statusCode >= 300) {
        return new PushResult(false, "FAILED", "极光请求失败（HTTP " + statusCode + "）: " + body);
      }
      return sent > 0 || msgId > 0
          ? new PushResult(true, "SENT", "")
          : new PushResult(false, "FAILED", body);
    } catch (IOException error) {
      return new PushResult(false, "FAILED", "极光返回解析失败: " + error.getMessage());
    }
  }

  private String credentials() {
    String raw = appKey() + ":" + masterSecret();
    return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  private String pushUrl() {
    return environment.getProperty("JPUSH_PUSH_URL", PUSH_URL).trim();
  }

  private int timeToLiveSeconds() {
    String raw = environment.getProperty("JPUSH_TIME_TO_LIVE_SECONDS", "").trim();
    if (raw.isBlank()) {
      return DEFAULT_TIME_TO_LIVE_SECONDS;
    }
    try {
      return Math.max(0, Math.min(MAX_TIME_TO_LIVE_SECONDS, Integer.parseInt(raw)));
    } catch (NumberFormatException error) {
      return DEFAULT_TIME_TO_LIVE_SECONDS;
    }
  }

  private String alias() {
    return environment.getProperty("JPUSH_ALIAS", "").trim();
  }

  private String appKey() {
    return environment.getProperty("JPUSH_APP_KEY", "").trim();
  }

  private String masterSecret() {
    return environment.getProperty("JPUSH_MASTER_SECRET", "").trim();
  }

  private static String value(String input) {
    return input == null ? "" : input.trim();
  }

  private static String notificationIntent(String messageId) {
    String safeId = value(messageId).replaceAll("[^A-Za-z0-9._-]", "");
    return ANDROID_INTENT_PREFIX + safeId + ";end";
  }

  public record PushResult(boolean ok, String status, String error) {
  }
}
