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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/** 极光推送客户端：服务端触发，经极光/厂商通道送达手机通知栏。 */
@Service
public class JpushPushClient {
  private static final String PUSH_URL = "https://api.jpush.cn/v3/push";
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
                  "extras", Map.of("messageId", value(messageId)))),
          "options", Map.of("apns_production", false));
      HttpRequest request = HttpRequest.newBuilder(URI.create(PUSH_URL))
          .header("Content-Type", "application/json")
          .header("Authorization", "Basic " + credentials())
          .timeout(Duration.ofSeconds(10))
          .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
          .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return parse(response.body());
    } catch (IOException | RuntimeException error) {
      return new PushResult(false, "FAILED", error.getMessage());
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      return new PushResult(false, "FAILED", "推送被中断");
    }
  }

  private PushResult parse(String body) {
    if (body == null || body.isBlank()) {
      return new PushResult(false, "FAILED", "极光返回为空");
    }
    try {
      JsonNode root = mapper.readTree(body);
      long sent = root.path("sendno").asLong(0);
      long msgId = root.path("msg_id").asLong(0);
      if (root.path("error").isObject()) {
        JsonNode error = root.path("error");
        return new PushResult(false, "FAILED", error.path("message").asText(body));
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

  public record PushResult(boolean ok, String status, String error) {
  }
}
