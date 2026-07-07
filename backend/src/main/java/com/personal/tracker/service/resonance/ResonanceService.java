package com.personal.tracker.service.resonance;

import com.personal.tracker.repository.resonance.ResonanceRepository;
import com.personal.tracker.repository.resonance.ResonanceRepository.ClusterDraft;
import com.personal.tracker.repository.resonance.ResonanceRepository.ClusterItem;
import com.personal.tracker.repository.resonance.ResonanceRepository.ClusterRecord;
import com.personal.tracker.repository.resonance.ResonanceRepository.ItemDraft;
import com.personal.tracker.repository.resonance.ResonanceRepository.OpinionSignal;
import com.personal.tracker.repository.resonance.ResonanceRepository.RecentMessage;
import com.personal.tracker.service.resonance.ResonanceNotifier.AlertStatusView;
import com.personal.tracker.service.wxpusher.instruments.MessageInstrumentExtractor;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class ResonanceService {
  private static final int MIN_VISIBLE_SCORE = 55;
  private static final Duration WINDOW = Duration.ofHours(24);
  private static final Duration LIST_WINDOW = Duration.ofDays(2);
  private final ResonanceRepository repository;
  private final ResonanceNotifier notifier;

  public ResonanceService(ResonanceRepository repository, ResonanceNotifier notifier) {
    this.repository = repository;
    this.notifier = notifier;
  }

  public List<ResonanceView> list(String symbol, int limit) {
    String since = Instant.now().minus(LIST_WINDOW).toString();
    List<MessageMention> mentions = recentMentions(symbol, since);
    Map<String, MessageMention> latestMentions = latestMentions(mentions);
    List<ResonanceView> clusters = repository.list(symbol, since, Math.max(limit, 20)).stream()
        .map(cluster -> new ResonanceView(cluster, repository.items(cluster.id())))
        .toList();
    var clusterSymbols = clusters.stream()
        .map(view -> view.cluster().symbol())
        .collect(Collectors.toSet());
    return Stream.concat(
            clusters.stream(),
            mentions.stream()
                .filter(mention -> !clusterSymbols.contains(mention.symbol()))
                .map(this::messageView))
        .sorted(Comparator
            .comparing((ResonanceView view) -> radarTime(view, latestMentions)).reversed()
            .thenComparing((ResonanceView view) -> view.cluster().score(), Comparator.reverseOrder()))
        .limit(Math.max(1, Math.min(limit, 100)))
        .toList();
  }

  public AlertStatusView alertStatus() {
    return notifier.status();
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
    return new ResonanceView(repository.cluster(saved.id()), viewItems);
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

  private List<MessageMention> recentMentions(String symbol, String since) {
    String requested = safe(symbol).toUpperCase(Locale.ROOT);
    return repository.recentMessages(since, 600).stream()
        .flatMap(message -> MessageInstrumentExtractor.extract(messageText(message)).stream()
            .map(item -> new MessageMention(
                item,
                message.bloggerName(),
                thesis(message, item),
                abbreviate(messageText(message), 500),
                message.messageTime(),
                message.id())))
        .filter(mention -> requested.isBlank() || requested.equals(mention.symbol()))
        .collect(Collectors.toMap(
            MessageMention::symbol,
            Function.identity(),
            (left, right) -> parseTime(left.messageTime()).isBefore(parseTime(right.messageTime())) ? right : left,
            java.util.LinkedHashMap::new))
        .values()
        .stream()
        .toList();
  }

  private Map<String, MessageMention> latestMentions(List<MessageMention> mentions) {
    return mentions.stream().collect(Collectors.toMap(MessageMention::symbol, Function.identity()));
  }

  private ResonanceView messageView(MessageMention mention) {
    ClusterRecord cluster = new ClusterRecord(
        "msg-" + mention.symbol() + "-" + mention.messageId(),
        "",
        mention.symbol(),
        bucketDate(mention.messageTime()),
        "WATCH",
        "消息",
        50,
        "WATCH",
        "最新消息关注",
        "%s 最新消息提到 %s：%s".formatted(mention.sourceName(), mention.symbol(), mention.thesis()),
        "",
        "",
        "",
        "",
        1,
        1,
        1,
        0,
        mention.sourceName(),
        mention.messageTime(),
        "ACTIVE",
        "PENDING",
        "",
        "",
        mention.messageTime(),
        mention.messageTime());
    ClusterItem item = new ClusterItem(
        "wxmsg-" + mention.messageId(),
        "SUPPORT",
        mention.sourceName(),
        "WATCH",
        "消息",
        mention.thesis(),
        mention.sourceQuote(),
        mention.messageTime());
    return new ResonanceView(cluster, List.of(item));
  }

  private Instant radarTime(ResonanceView view, Map<String, MessageMention> latestMentions) {
    Instant clusterTime = parseTime(view.cluster().lastOpinionAt());
    MessageMention mention = latestMentions.get(view.cluster().symbol());
    if (mention == null) {
      return clusterTime;
    }
    Instant messageTime = parseTime(mention.messageTime());
    return messageTime.isAfter(clusterTime) ? messageTime : clusterTime;
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

  private String thesis(RecentMessage message, String symbol) {
    String text = messageText(message);
    for (String line : text.split("\\R")) {
      String value = line.trim();
      if (!value.isBlank() && value.toUpperCase(Locale.ROOT).contains(symbol)) {
        return abbreviate(value, 160);
      }
    }
    return abbreviate(safe(message.summary()).isBlank() ? safe(message.title()) : safe(message.summary()), 160);
  }

  private String messageText(RecentMessage message) {
    return List.of(message.detailText(), message.summary(), message.title()).stream()
        .map(ResonanceService::safe)
        .filter(value -> !value.isBlank())
        .findFirst()
        .orElse("");
  }

  private String abbreviate(String value, int maxLength) {
    String text = safe(value);
    return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
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

  private record MessageMention(
      String symbol,
      String sourceName,
      String thesis,
      String sourceQuote,
      String messageTime,
      String messageId) {
  }

  public record ResonanceView(ClusterRecord cluster, List<ClusterItem> items) {
  }
}
