package com.personal.tracker.service.wxpusher;

import com.personal.tracker.config.LlmProperties;
import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.service.llm.OpenAiCompatibleChatService;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class OpenAiJsonExtractor {
  private static final Duration HEALTH_TTL = Duration.ofMinutes(5);
  private static final String EXTRACT_SCENE = "WXPUSHER_EXTRACT";
  private static final String HEALTH_SCENE = "WXPUSHER_HEALTH";
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
      持仓动作只能输出 OPEN/CLOSE/IGNORE。
      市场使用 US/HK/CRYPTO/UNKNOWN。
      没有明确交易观点时，按具体品种划分输出空数组。
      """;
  private final LlmProperties properties;
  private final OpenAiCompatibleChatService chatService;
  private volatile HealthStatus lastHealth = new HealthStatus(false, false, "未检查", null);

  public OpenAiJsonExtractor(LlmProperties properties, OpenAiCompatibleChatService chatService) {
    this.properties = properties;
    this.chatService = chatService;
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
    return stripCodeFence(chatService.chat(EXTRACT_SCENE, SYSTEM_PROMPT, prompt));
  }

  public boolean extractionEnabled() {
    return properties.wxpusherEnabled();
  }

  public synchronized HealthStatus health() {
    if (!properties.wxpusherEnabled()) {
      return new HealthStatus(
          properties.configured(),
          false,
          "WxPusher LLM 提取已禁用",
          lastHealth.checkedAt());
    }
    if (!properties.configured()) {
      lastHealth = new HealthStatus(false, false, "未配置 LLM 环境变量", null);
      return lastHealth;
    }
    if (lastHealth.checkedAt() != null
        && Instant.parse(lastHealth.checkedAt()).plus(HEALTH_TTL).isAfter(Instant.now())) {
      return lastHealth;
    }
    try {
      String result = chatService.chat(HEALTH_SCENE, SYSTEM_PROMPT, "只回答 ok");
      lastHealth = new HealthStatus(true, !result.isBlank(), result, JdbcSupport.now());
    } catch (RuntimeException error) {
      lastHealth = new HealthStatus(true, false, error.getMessage(), JdbcSupport.now());
    }
    return lastHealth;
  }

  private String stripCodeFence(String text) {
    String trimmed = text == null ? "" : text.trim();
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
