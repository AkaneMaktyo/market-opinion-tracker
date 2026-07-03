package com.personal.tracker.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.config.LlmProperties;
import com.personal.tracker.repository.llm.LlmCallLogRepository;
import com.personal.tracker.repository.llm.LlmCallLogRepository.LogEntry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OpenAiCompatibleChatService {
  private final LlmProperties properties;
  private final ObjectMapper mapper;
  private final LlmCallLogRepository logs;
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(20))
      .build();

  public OpenAiCompatibleChatService(
      LlmProperties properties,
      ObjectMapper mapper,
      LlmCallLogRepository logs) {
    this.properties = properties;
    this.mapper = mapper;
    this.logs = logs;
  }

  public String chat(String scene, String systemPrompt, String userPrompt) {
    properties.ensureSceneEnabled(scene);
    properties.ensureConfigured();
    Instant startedAt = Instant.now();
    String requestPreview = safe(userPrompt);
    String responseText = "";
    String status = "FAILED";
    String errorMessage = "";
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(properties.baseUrl() + "/chat/completions"))
          .header("Authorization", "Bearer " + properties.apiKey())
          .header("Content-Type", "application/json")
          .timeout(Duration.ofSeconds(60))
          .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
              "model", properties.model(),
              "temperature", 0,
              "stream", false,
              "messages", List.of(
                  Map.of("role", "system", "content", safe(systemPrompt)),
                  Map.of("role", "user", "content", requestPreview))))))
          .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      responseText = readResponse(response.body());
      status = "SUCCESS";
      return responseText;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      errorMessage = "调用 LLM 被中断";
      throw new IllegalStateException(errorMessage, error);
    } catch (IOException error) {
      errorMessage = "调用 LLM 失败: " + error.getMessage();
      throw new IllegalStateException(errorMessage, error);
    } catch (RuntimeException error) {
      errorMessage = safe(error.getMessage());
      throw error;
    } finally {
      writeLog(
          scene,
          status,
          requestPreview,
          responseText,
          errorMessage,
          Duration.between(startedAt, Instant.now()).toMillis());
    }
  }

  private void writeLog(
      String scene,
      String status,
      String requestPreview,
      String responseText,
      String errorMessage,
      long durationMs) {
    try {
      logs.create(new LogEntry(
          safe(scene),
          properties.model(),
          safe(status),
          requestPreview.length(),
          responseText.length(),
          durationMs,
          requestPreview,
          responseText,
          errorMessage));
    } catch (RuntimeException ignored) {
      // 审计落库失败不影响主业务调用
    }
  }

  private String readResponse(String body) throws IOException {
    JsonNode root = mapper.readTree(body);
    JsonNode content = root.path("choices").path(0).path("message").path("content");
    String text = readContent(content).trim();
    if (text.isBlank()) {
      throw new IllegalStateException("LLM 没有返回可用内容");
    }
    return text;
  }

  private String readContent(JsonNode content) {
    if (content == null || content.isMissingNode() || content.isNull()) {
      return "";
    }
    if (content.isTextual()) {
      return content.asText("");
    }
    if (content.isArray()) {
      StringBuilder builder = new StringBuilder();
      content.forEach(item -> builder.append(item.path("text").asText("")));
      return builder.toString();
    }
    return content.toString();
  }

  private String safe(String text) {
    return text == null ? "" : text.trim();
  }
}
