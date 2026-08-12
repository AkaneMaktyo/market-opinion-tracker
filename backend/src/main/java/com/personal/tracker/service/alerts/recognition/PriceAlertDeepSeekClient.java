package com.personal.tracker.service.alerts.recognition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.config.PriceAlertDeepSeekProperties;
import com.personal.tracker.repository.llm.LlmCallLogRepository;
import com.personal.tracker.repository.llm.LlmCallLogRepository.AuditCompletion;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PriceAlertDeepSeekClient {
  static final String SCENE = "MESSAGE_PRICE_ALERT_RECOGNITION";
  private final PriceAlertDeepSeekProperties properties;
  private final LlmCallLogRepository logs;
  private final ObjectMapper mapper;
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(20)).build();

  public PriceAlertDeepSeekClient(
      PriceAlertDeepSeekProperties properties,
      LlmCallLogRepository logs,
      ObjectMapper mapper) {
    this.properties = properties;
    this.logs = logs;
    this.mapper = mapper;
  }

  public String recognize(String messageId, String systemPrompt, String userPrompt) {
    properties.ensureConfigured();
    String requestBody = requestBody(systemPrompt, userPrompt);
    String auditId = logs.beginAudit(
        messageId, SCENE, PriceAlertDeepSeekProperties.MODEL, requestBody);
    Instant started = Instant.now();
    String responseBody = "";
    int httpStatus = 0;
    try {
      HttpRequest request = HttpRequest.newBuilder(
              URI.create(properties.baseUrl() + "/chat/completions"))
          .header("Authorization", "Bearer " + properties.apiKey())
          .header("Content-Type", "application/json")
          .timeout(Duration.ofSeconds(90))
          .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
          .build();
      HttpResponse<String> response = http.send(
          request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      httpStatus = response.statusCode();
      responseBody = response.body() == null ? "" : response.body();
      JsonNode root = mapper.readTree(responseBody);
      if (httpStatus < 200 || httpStatus >= 300) {
        throw new IllegalStateException("DeepSeek 返回 HTTP " + httpStatus + ": " + apiError(root));
      }
      String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
      if (content.isBlank()) {
        throw new IllegalStateException("DeepSeek 未返回识别结果");
      }
      complete(auditId, "SUCCESS", responseBody, "", httpStatus, root, started);
      return content;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      failAudit(auditId, responseBody, "DeepSeek 调用被中断", httpStatus, started);
      throw new IllegalStateException("DeepSeek 调用被中断", error);
    } catch (Exception error) {
      failAudit(auditId, responseBody, message(error), httpStatus, started);
      if (error instanceof RuntimeException runtime) throw runtime;
      throw new IllegalStateException("DeepSeek 调用失败: " + message(error), error);
    }
  }

  private String requestBody(String systemPrompt, String userPrompt) {
    try {
      return mapper.writeValueAsString(Map.of(
          "model", PriceAlertDeepSeekProperties.MODEL,
          "stream", false,
          "temperature", 0,
          "max_tokens", 16384,
          "thinking", Map.of("type", "disabled"),
          "response_format", Map.of("type", "json_object"),
          "messages", List.of(
              Map.of("role", "system", "content", systemPrompt),
              Map.of("role", "user", "content", userPrompt))));
    } catch (Exception error) {
      throw new IllegalStateException("无法生成 DeepSeek 请求", error);
    }
  }

  private void failAudit(
      String auditId, String response, String error, int httpStatus, Instant started) {
    JsonNode root = read(response);
    complete(auditId, "FAILED", response, error, httpStatus, root, started);
  }

  private void complete(
      String auditId,
      String status,
      String response,
      String error,
      int httpStatus,
      JsonNode root,
      Instant started) {
    JsonNode usage = root.path("usage");
    logs.completeAudit(auditId, new AuditCompletion(
        status, response, error, httpStatus, root.path("id").asText(""),
        usage.path("prompt_tokens").asInt(0), usage.path("completion_tokens").asInt(0),
        usage.path("total_tokens").asInt(0),
        Duration.between(started, Instant.now()).toMillis()));
  }

  private JsonNode read(String value) {
    try {
      return value == null || value.isBlank() ? mapper.createObjectNode() : mapper.readTree(value);
    } catch (Exception ignored) {
      return mapper.createObjectNode();
    }
  }

  private String apiError(JsonNode root) {
    String value = root.path("error").path("message").asText("");
    return value.isBlank() ? "未知错误" : value;
  }

  private String message(Throwable error) {
    return error.getMessage() == null || error.getMessage().isBlank()
        ? error.getClass().getSimpleName() : error.getMessage();
  }
}
