package com.personal.tracker.service.wxpusher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.config.LlmProperties;
import com.personal.tracker.repository.JdbcSupport;
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
import org.springframework.stereotype.Component;

@Component
public class OpenAiJsonExtractor {
  private static final Duration HEALTH_TTL = Duration.ofMinutes(5);
  private static final String SYSTEM_PROMPT = """
      你是市场观点结构化助手。
      只输出合法 JSON，不要输出 Markdown、解释、注释或代码块。
      输出结构必须是：
      {
        "总体摘要": {
          "大盘与风格": "",
          "主线": "",
          "风险提示": ""
        },
        "按具体品种划分": [
          {
            "品种": "",
            "代码": "",
            "市场": "",
            "方向": "",
            "持仓动作": "",
            "周期": "",
            "关键判断": "",
            "催化": [],
            "触发条件": "",
            "风险": [],
            "关键价位": [],
            "原文摘录": ""
          }
        ],
        "待确认映射": {}
      }
      只保留可交易品种，方向优先使用 看多/看空/震荡/观望/谨慎。
      持仓动作只能输出 OPEN/CLOSE/IGNORE：买入、建仓、开仓、加仓、持有、继续持有为 OPEN；卖出、清仓、平仓、止盈、止损、退出、减仓到零为 CLOSE；普通看空或只观察为 IGNORE。
      市场使用 US/HK/CRYPTO/UNKNOWN。
      没有明确交易观点时，按具体品种划分输出空数组。
      """;
  private final LlmProperties properties;
  private final ObjectMapper mapper;
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(20))
      .build();
  private volatile HealthStatus lastHealth = new HealthStatus(false, false, "未检测", null);

  public OpenAiJsonExtractor(LlmProperties properties, ObjectMapper mapper) {
    this.properties = properties;
    this.mapper = mapper;
  }

  public String extract(
      String bloggerName,
      String title,
      String summary,
      String detailText,
      String sourceUrl) {
    properties.ensureConfigured();
    String prompt = """
        博主：%s
        标题：%s
        摘要：%s
        原文链接：%s
        正文：
        %s
        """.formatted(blank(bloggerName), blank(title), blank(summary), blank(sourceUrl), blank(detailText));
    return stripCodeFence(callChat(prompt));
  }

  public synchronized HealthStatus health() {
    if (!properties.configured()) {
      lastHealth = new HealthStatus(false, false, "未配置 LLM 环境变量", null);
      return lastHealth;
    }
    if (lastHealth.checkedAt() != null
        && Instant.parse(lastHealth.checkedAt()).plus(HEALTH_TTL).isAfter(Instant.now())) {
      return lastHealth;
    }
    try {
      String result = callChat("只回复 ok");
      lastHealth = new HealthStatus(true, !result.isBlank(), result, JdbcSupport.now());
    } catch (RuntimeException error) {
      lastHealth = new HealthStatus(true, false, error.getMessage(), JdbcSupport.now());
    }
    return lastHealth;
  }

  private String callChat(String userPrompt) {
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
                  Map.of("role", "system", "content", SYSTEM_PROMPT),
                  Map.of("role", "user", "content", userPrompt))))))
          .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      JsonNode root = mapper.readTree(response.body());
      JsonNode content = root.path("choices").path(0).path("message").path("content");
      String text = readContent(content).trim();
      if (text.isBlank()) {
        throw new IllegalStateException("LLM 没有返回可用内容");
      }
      return text;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("调用 LLM 被中断", error);
    } catch (IOException error) {
      throw new IllegalStateException("调用 LLM 失败: " + error.getMessage(), error);
    }
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

  private String stripCodeFence(String text) {
    String trimmed = text.trim();
    if (!trimmed.startsWith("```")) {
      return trimmed;
    }
    return trimmed.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
  }

  private String blank(String value) {
    return value == null ? "" : value.trim();
  }

  public record HealthStatus(
      boolean configured,
      boolean reachable,
      String message,
      String checkedAt) {
  }
}
