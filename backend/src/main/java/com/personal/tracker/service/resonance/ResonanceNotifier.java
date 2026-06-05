package com.personal.tracker.service.resonance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.repository.resonance.ResonanceRepository;
import com.personal.tracker.repository.resonance.ResonanceRepository.AlertDraft;
import com.personal.tracker.repository.resonance.ResonanceRepository.ClusterItem;
import com.personal.tracker.repository.resonance.ResonanceRepository.ClusterRecord;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class ResonanceNotifier {
  private static final String SEND_URL = "https://wxpusher.zjiecode.com/api/send/message";
  private final Environment environment;
  private final ObjectMapper mapper;
  private final ResonanceRepository repository;
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();

  public ResonanceNotifier(
      Environment environment,
      ObjectMapper mapper,
      ResonanceRepository repository) {
    this.environment = environment;
    this.mapper = mapper;
    this.repository = repository;
  }

  public void notifyIfNeeded(ClusterRecord cluster, List<ClusterItem> items) {
    if (cluster.score() < minScore() || "SENT".equalsIgnoreCase(cluster.alertStatus())) {
      return;
    }
    String title = "%s %s %d分共振".formatted(cluster.symbol(), directionLabel(cluster.direction()), cluster.score());
    String content = content(cluster, items);
    NotifyResult result = send(title, content);
    String sentAt = result.ok() ? JdbcSupport.now() : "";
    String status = result.ok() ? "SENT" : result.status();
    repository.createAlert(new AlertDraft(cluster.id(), title, content, status, result.error(), sentAt));
    repository.markAlert(cluster.id(), status, result.error(), sentAt);
  }

  private NotifyResult send(String title, String content) {
    try {
      String spt = env("RESONANCE_WXPUSHER_SPT", "POSITION_NOTIFY_WXPUSHER_SPT");
      if (!spt.isBlank()) {
        return parse(sendSpt(spt, content));
      }
      String token = env("RESONANCE_WXPUSHER_APP_TOKEN", "POSITION_NOTIFY_WXPUSHER_APP_TOKEN");
      List<String> uids = listEnv("RESONANCE_WXPUSHER_UIDS", "POSITION_NOTIFY_WXPUSHER_UIDS");
      List<Integer> topics = topicIds("RESONANCE_WXPUSHER_TOPIC_IDS", "POSITION_NOTIFY_WXPUSHER_TOPIC_IDS");
      if (token.isBlank() || (uids.isEmpty() && topics.isEmpty())) {
        return new NotifyResult(false, "WAITING_CONFIG", "WxPusher 推送凭证未配置");
      }
      return parse(sendApp(token, uids, topics, title, content));
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      return new NotifyResult(false, "FAILED", "WxPusher 推送被中断");
    } catch (IOException | RuntimeException error) {
      return new NotifyResult(false, "FAILED", error.getMessage());
    }
  }

  private String sendSpt(String spt, String content) throws IOException, InterruptedException {
    String url = SEND_URL + "/" + spt + "/" + URLEncoder.encode(content, StandardCharsets.UTF_8);
    HttpRequest request = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(10))
        .GET()
        .build();
    return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
  }

  private String sendApp(
      String token,
      List<String> uids,
      List<Integer> topics,
      String title,
      String content) throws IOException, InterruptedException {
    Map<String, Object> payload = Map.of(
        "appToken", token,
        "summary", title.length() > 100 ? title.substring(0, 100) : title,
        "content", content,
        "contentType", 1,
        "uids", uids,
        "topicIds", topics);
    HttpRequest request = HttpRequest.newBuilder(URI.create(SEND_URL))
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(10))
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
        .build();
    return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
  }

  private NotifyResult parse(String body) throws IOException {
    JsonNode root = mapper.readTree(body);
    String recordError = recordError(root.path("data"));
    if (!recordError.isBlank()) {
      return new NotifyResult(false, "FAILED", recordError);
    }
    String code = root.path("code").asText("");
    boolean ok = root.path("success").asBoolean(false)
        || List.of("0", "200", "1000").contains(code);
    return ok
        ? new NotifyResult(true, "SENT", "")
        : new NotifyResult(false, "FAILED", root.path("msg").asText(body));
  }

  private String recordError(JsonNode data) {
    if (!data.isArray()) {
      return "";
    }
    for (JsonNode item : data) {
      String code = item.path("code").asText("");
      boolean delivered = item.hasNonNull("sendRecordId") || item.hasNonNull("messageId");
      if (!List.of("0", "200", "1000").contains(code) && !(code.isBlank() && delivered)) {
        return item.path("status").asText(item.toString());
      }
    }
    return "";
  }

  private String content(ClusterRecord cluster, List<ClusterItem> items) {
    String support = items.stream()
        .filter(item -> "SUPPORT".equals(item.role()))
        .limit(3)
        .map(item -> "- %s：%s".formatted(item.sourceName(), item.thesis()))
        .reduce((left, right) -> left + "\n" + right)
        .orElse("- 暂无明细");
    return """
        【共振雷达】%s %s
        分数：%d / 来源：%d / 冲突：%d
        建议：%s
        触发：%s
        失效：%s
        摘要：%s
        证据：
        %s
        """.formatted(
        cluster.symbol(), directionLabel(cluster.direction()), cluster.score(),
        cluster.sourceCount(), cluster.conflictCount(), cluster.action(),
        blank(cluster.triggerText(), "等待价格或消息确认"),
        blank(cluster.invalidationText(), "暂无明确失效条件"),
        blank(cluster.summary(), "暂无摘要"),
        support);
  }

  private int minScore() {
    String value = env("RESONANCE_ALERT_MIN_SCORE", "");
    try {
      return value.isBlank() ? 85 : Integer.parseInt(value);
    } catch (NumberFormatException error) {
      return 85;
    }
  }

  private List<String> listEnv(String primary, String fallback) {
    String value = env(primary, fallback);
    if (value.isBlank()) {
      return List.of();
    }
    return Arrays.stream(value.split("[,;\\s]+")).map(String::trim).filter(item -> !item.isBlank()).toList();
  }

  private List<Integer> topicIds(String primary, String fallback) {
    return listEnv(primary, fallback).stream().flatMap(value -> {
      try {
        return java.util.stream.Stream.of(Integer.parseInt(value));
      } catch (NumberFormatException error) {
        return java.util.stream.Stream.empty();
      }
    }).toList();
  }

  private String env(String primary, String fallback) {
    String value = environment.getProperty(primary);
    if ((value == null || value.isBlank()) && !fallback.isBlank()) {
      value = environment.getProperty(fallback);
    }
    return value == null ? "" : value.trim();
  }

  private String directionLabel(String direction) {
    return switch (direction) {
      case "BULLISH" -> "看多";
      case "BEARISH" -> "看空";
      case "RANGE" -> "震荡";
      default -> "观察";
    };
  }

  private String blank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private record NotifyResult(boolean ok, String status, String error) {
  }
}
