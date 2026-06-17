package com.personal.tracker.service.resonance;

import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.repository.resonance.ResonanceRepository;
import com.personal.tracker.repository.resonance.ResonanceRepository.AlertDraft;
import com.personal.tracker.repository.resonance.ResonanceRepository.ClusterItem;
import com.personal.tracker.repository.resonance.ResonanceRepository.ClusterRecord;
import com.personal.tracker.service.notify.WxPusherPushClient;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ResonanceNotifier {
  private static final int DEFAULT_MIN_SCORE = 70;
  private final Environment environment;
  private final ResonanceRepository repository;
  private final WxPusherPushClient pushClient;

  public ResonanceNotifier(
      Environment environment,
      ResonanceRepository repository,
      WxPusherPushClient pushClient) {
    this.environment = environment;
    this.repository = repository;
    this.pushClient = pushClient;
  }

  public void notifyIfNeeded(ClusterRecord cluster, List<ClusterItem> items) {
    if (cluster.score() < minScore() || "SENT".equalsIgnoreCase(cluster.alertStatus())) {
      return;
    }
    String title = "%s %s %d分共振".formatted(
        cluster.symbol(),
        directionLabel(cluster.direction()),
        cluster.score());
    String content = content(cluster, items);
    WxPusherPushClient.PushResult result = pushClient.send(
        title,
        content,
        "RESONANCE",
        "POSITION_NOTIFY");
    String sentAt = result.ok() ? JdbcSupport.now() : "";
    String status = result.ok() ? "SENT" : result.status();
    repository.createAlert(new AlertDraft(cluster.id(), title, content, status, result.error(), sentAt));
    repository.markAlert(cluster.id(), status, result.error(), sentAt);
  }

  public AlertStatusView status() {
    int minScore = minScore();
    if (pushClient.isConfigured("RESONANCE", "POSITION_NOTIFY")) {
      return new AlertStatusView(
          minScore,
          true,
          "分数达到 %d 分的共振会自动推送到 WxPusher。".formatted(minScore));
    }
    return new AlertStatusView(
        minScore,
        false,
        "分数达到 %d 分才会推送，但当前服务端还没配置 WxPusher 推送凭证。".formatted(minScore));
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
        cluster.symbol(),
        directionLabel(cluster.direction()),
        cluster.score(),
        cluster.sourceCount(),
        cluster.conflictCount(),
        cluster.action(),
        blank(cluster.triggerText(), "等待价格或消息确认"),
        blank(cluster.invalidationText(), "暂无明确失效条件"),
        blank(cluster.summary(), "暂无摘要"),
        support);
  }

  private int minScore() {
    String value = environment.getProperty("RESONANCE_ALERT_MIN_SCORE", "");
    try {
      return value.isBlank() ? DEFAULT_MIN_SCORE : Integer.parseInt(value);
    } catch (NumberFormatException error) {
      return DEFAULT_MIN_SCORE;
    }
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

  public record AlertStatusView(int minScore, boolean pushReady, String message) {
  }
}
