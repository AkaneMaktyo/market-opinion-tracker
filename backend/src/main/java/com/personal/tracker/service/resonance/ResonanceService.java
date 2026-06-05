package com.personal.tracker.service.resonance;

import com.personal.tracker.repository.resonance.ResonanceRepository;
import com.personal.tracker.repository.resonance.ResonanceRepository.ClusterDraft;
import com.personal.tracker.repository.resonance.ResonanceRepository.ClusterItem;
import com.personal.tracker.repository.resonance.ResonanceRepository.ClusterRecord;
import com.personal.tracker.repository.resonance.ResonanceRepository.ItemDraft;
import com.personal.tracker.repository.resonance.ResonanceRepository.OpinionSignal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ResonanceService {
  private static final int MIN_VISIBLE_SCORE = 55;
  private static final Duration WINDOW = Duration.ofHours(24);
  private final ResonanceRepository repository;
  private final ResonanceNotifier notifier;

  public ResonanceService(ResonanceRepository repository, ResonanceNotifier notifier) {
    this.repository = repository;
    this.notifier = notifier;
  }

  public List<ResonanceView> list(String symbol, int limit) {
    return repository.list(symbol, null, limit).stream()
        .map(cluster -> new ResonanceView(cluster, repository.items(cluster.id())))
        .toList();
  }

  public List<ResonanceView> refreshForSymbol(String symbol) {
    List<OpinionSignal> signals = repository.recentSignals(symbol, 200);
    if (signals.isEmpty()) {
      return List.of();
    }
    List<OpinionSignal> window = latestWindow(signals);
    Map<ClusterKey, List<OpinionSignal>> grouped = window.stream()
        .filter(item -> !"WATCH".equalsIgnoreCase(item.direction()))
        .collect(Collectors.groupingBy(this::clusterKey));
    return grouped.entrySet().stream()
        .map(entry -> build(entry.getKey(), entry.getValue(), window))
        .filter(Objects::nonNull)
        .sorted(Comparator.comparingInt((ResonanceView view) -> view.cluster().score()).reversed())
        .toList();
  }

  private ResonanceView build(
      ClusterKey key,
      List<OpinionSignal> support,
      List<OpinionSignal> window) {
    List<OpinionSignal> conflicts = window.stream()
        .filter(item -> isOpposite(key.direction(), item.direction()))
        .toList();
    OpinionSignal latest = support.stream()
        .max(Comparator.comparing(item -> parseTime(item.opinionTime())))
        .orElseThrow();
    int sourceCount = distinctSources(support);
    int score = score(sourceCount, support.size(), conflicts.size(), support);
    if (score < MIN_VISIBLE_SCORE) {
      return null;
    }
    ClusterRecord saved = repository.save(new ClusterDraft(
        latest.instrumentId(),
        latest.symbol(),
        bucketDate(latest.opinionTime()),
        key.direction(),
        key.horizon(),
        score,
        grade(score),
        action(score, key.direction()),
        summary(latest.symbol(), key.direction(), support, conflicts),
        joinUnique(support.stream().map(OpinionSignal::triggerCondition).toList(), 3),
        joinUnique(support.stream().map(OpinionSignal::invalidation).toList(), 3),
        joinUnique(support.stream().map(OpinionSignal::risksText).toList(), 3),
        joinUnique(support.stream().map(OpinionSignal::catalystsText).toList(), 3),
        sourceCount,
        support.size() + conflicts.size(),
        support.size(),
        conflicts.size(),
        sourceNames(support),
        latest.opinionTime()));
    List<ItemDraft> items = mergeItems(support, conflicts);
    repository.replaceItems(saved.id(), items);
    List<ClusterItem> viewItems = repository.items(saved.id());
    notifier.notifyIfNeeded(saved, viewItems);
    return new ResonanceView(saved, viewItems);
  }

  private List<OpinionSignal> latestWindow(List<OpinionSignal> signals) {
    Instant anchor = signals.stream()
        .map(item -> parseTime(item.opinionTime()))
        .max(Instant::compareTo)
        .orElse(Instant.now());
    Instant floor = anchor.minus(WINDOW);
    return signals.stream()
        .filter(item -> !parseTime(item.opinionTime()).isBefore(floor))
        .toList();
  }

  private ClusterKey clusterKey(OpinionSignal signal) {
    return new ClusterKey(safe(signal.direction()).toUpperCase(Locale.ROOT), horizonBucket(signal.horizon()));
  }

  private int score(
      int sourceCount,
      int supportCount,
      int conflictCount,
      List<OpinionSignal> support) {
    int value = 42
        + Math.min(32, Math.max(0, sourceCount - 1) * 16)
        + Math.min(8, supportCount * 2)
        + (hasAny(support.stream().map(OpinionSignal::triggerCondition).toList()) ? 6 : 0)
        + (hasAny(support.stream().map(OpinionSignal::catalystsText).toList()) ? 6 : 0)
        - Math.min(25, conflictCount * 12);
    if (sourceCount < 2) {
      value -= 5;
    }
    return Math.max(0, Math.min(100, value));
  }

  private String grade(int score) {
    if (score >= 85) {
      return "STRONG";
    }
    if (score >= 70) {
      return "ACTIONABLE";
    }
    return "WATCH";
  }

  private String action(int score, String direction) {
    if (score < 70) {
      return "只观察，等待更多独立来源确认";
    }
    return switch (direction) {
      case "BULLISH" -> score >= 85 ? "重点关注做多机会" : "等触发后关注做多";
      case "BEARISH" -> score >= 85 ? "重点关注避险或做空机会" : "等触发后偏空处理";
      case "RANGE" -> "按震荡策略处理，等待边界确认";
      default -> "保持观察";
    };
  }

  private String summary(
      String symbol,
      String direction,
      List<OpinionSignal> support,
      List<OpinionSignal> conflicts) {
    String lead = support.stream()
        .map(OpinionSignal::thesis)
        .filter(text -> !safe(text).isBlank())
        .findFirst()
        .orElse("出现同向讨论");
    String conflictText = conflicts.isEmpty() ? "暂无明显反向观点" : "存在反向观点，需要降低仓位或等待确认";
    return "%s 出现 %d 个来源%s共振：%s。%s".formatted(
        symbol, distinctSources(support), directionLabel(direction), lead, conflictText);
  }

  private List<ItemDraft> mergeItems(List<OpinionSignal> support, List<OpinionSignal> conflicts) {
    List<ItemDraft> supportItems = support.stream().map(item -> itemDraft(item, "SUPPORT")).toList();
    List<ItemDraft> conflictItems = conflicts.stream().map(item -> itemDraft(item, "CONFLICT")).toList();
    return java.util.stream.Stream.concat(supportItems.stream(), conflictItems.stream()).toList();
  }

  private ItemDraft itemDraft(OpinionSignal item, String role) {
    return new ItemDraft(
        item.opinionId(),
        role,
        item.sourceName(),
        item.direction(),
        item.horizon(),
        item.thesis(),
        item.sourceQuote(),
        item.opinionTime());
  }

  private int distinctSources(List<OpinionSignal> support) {
    return (int) support.stream().map(OpinionSignal::sourceName).filter(name -> !safe(name).isBlank()).distinct().count();
  }

  private String sourceNames(List<OpinionSignal> support) {
    return support.stream()
        .map(OpinionSignal::sourceName)
        .filter(name -> !safe(name).isBlank())
        .collect(Collectors.toCollection(LinkedHashSet::new))
        .stream()
        .collect(Collectors.joining(", "));
  }

  private String joinUnique(List<String> values, int limit) {
    return values.stream()
        .map(ResonanceService::safe)
        .filter(value -> !value.isBlank())
        .collect(Collectors.toCollection(LinkedHashSet::new))
        .stream()
        .limit(limit)
        .collect(Collectors.joining("\n"));
  }

  private boolean hasAny(List<String> values) {
    return values.stream().anyMatch(value -> !safe(value).isBlank());
  }

  private boolean isOpposite(String left, String right) {
    return ("BULLISH".equalsIgnoreCase(left) && "BEARISH".equalsIgnoreCase(right))
        || ("BEARISH".equalsIgnoreCase(left) && "BULLISH".equalsIgnoreCase(right));
  }

  private String horizonBucket(String value) {
    String text = safe(value).toUpperCase(Locale.ROOT);
    if (text.contains("LONG") || text.contains("长") || text.contains("中长")) {
      return "中长线";
    }
    if (text.contains("DAY") || text.contains("1D") || text.contains("日")) {
      return "日内/波段";
    }
    return "短线";
  }

  private String directionLabel(String direction) {
    return switch (direction) {
      case "BULLISH" -> "看多";
      case "BEARISH" -> "看空";
      case "RANGE" -> "震荡";
      default -> "观察";
    };
  }

  private String bucketDate(String value) {
    return LocalDateTime.ofInstant(parseTime(value), ZoneOffset.UTC).toLocalDate().toString();
  }

  private static Instant parseTime(String value) {
    String text = safe(value);
    try {
      return Instant.parse(text);
    } catch (Exception ignored) {
      try {
        return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
      } catch (Exception ignoredAgain) {
        try {
          return LocalDate.parse(text).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (Exception ignoredLast) {
          return Instant.EPOCH;
        }
      }
    }
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private record ClusterKey(String direction, String horizon) {
  }

  public record ResonanceView(ClusterRecord cluster, List<ClusterItem> items) {
  }
}
