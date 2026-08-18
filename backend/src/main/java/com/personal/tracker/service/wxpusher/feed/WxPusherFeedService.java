package com.personal.tracker.service.wxpusher.feed;

import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSharedMessageRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSharedMessageRepository.RecentMessage;
import com.personal.tracker.service.wxpusher.WxPusherArticleExtractor;
import com.personal.tracker.service.wxpusher.article.WxPusherArticleParser;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class WxPusherFeedService {
  private static final Pattern SOURCE = Pattern.compile(
      "\\[[^\\]\\n]*?｜\\s*([^\\]\\n:]{2,40})\\]");
  private static final Pattern IMAGE_HINT = Pattern.compile(
      "(?i)\\.(?:jpe?g|png|gif|webp)(?:\\s|$|[?#])");
  private static final Pattern IMAGE_PAYLOAD = Pattern.compile(
      "(?m)^" + Pattern.quote(WxPusherArticleParser.IMAGE_PREFIX) + "[^\\r\\n]*(?:\\r?\\n|$)");
  private final WxPusherSharedMessageRepository messages;
  private final WxPusherSettingsRepository settings;
  private final WxPusherArticleExtractor articles;

  public WxPusherFeedService(
      WxPusherSharedMessageRepository messages,
      WxPusherSettingsRepository settings,
      WxPusherArticleExtractor articles) {
    this.messages = messages;
    this.settings = settings;
    this.articles = articles;
  }

  public List<FeedMessage> recent(int limit) {
    return messages.listRecentFeed(limit).stream()
        .map(this::view)
        .toList();
  }

  /** 多关键词搜索最近消息，正文包含图片识别文字，并按命中强度与时间排序。 */
  public List<FeedMessage> search(String keyword, int sinceDays, int limit) {
    String cleaned = normalize(keyword);
    List<String> terms = terms(cleaned);
    if (terms.isEmpty()) {
      return List.of();
    }
    int days = Math.max(1, Math.min(sinceDays, 365));
    int resultLimit = Math.max(1, Math.min(limit, 200));
    int candidateLimit = Math.min(1000, Math.max(resultLimit, resultLimit * 4));
    return messages.searchRecentFeed(terms, days, candidateLimit).stream()
        .filter(message -> matchesAllTerms(message, terms))
        .map(message -> new SearchHit(view(message), relevance(message, cleaned, terms)))
        .sorted(Comparator.comparingInt(SearchHit::score)
            .thenComparing(
                hit -> time(hit.message().messageTime()),
                Comparator.reverseOrder()))
        .limit(resultLimit)
        .map(SearchHit::message)
        .toList();
  }

  public FeedMessage detail(String id) {
    RecentMessage message = messages.findRecentFeedById(id)
        .orElseThrow(() -> new IllegalArgumentException("消息不存在"));
    String detailText = content(message);
    if (needsFetch(message, detailText)) {
      try {
        detailText = articles.fetchText(message.detailUrl(), settings.get());
      } catch (RuntimeException ignored) {
        // 详情链接可能过期，保留已收到的消息摘要。
      }
    }
    return view(message, detailText);
  }

  private FeedMessage view(RecentMessage message) {
    return view(message, content(message));
  }

  private FeedMessage view(RecentMessage message, String detailText) {
    return new FeedMessage(
        message.id(),
        message.messageKey(),
        bloggerName(message),
        value(message.kolId()),
        value(message.title()),
        value(message.summary()),
        detailText,
        value(message.sourceUrl()),
        value(message.messageTime()),
        value(message.status()),
        value(message.recognitionStatus()),
        value(message.recognitionId()),
        message.recognitionCandidateCount());
  }

  private boolean needsFetch(RecentMessage message, String detailText) {
    if (value(message.detailUrl()).isBlank()) return false;
    // 包含图片但 detailText 中没有图片标记 → 需要抓取原文获取图片 URL
    if (!detailText.contains(WxPusherArticleParser.IMAGE_PREFIX)
        && IMAGE_HINT.matcher(value(message.summary())).find()) {
      return true;
    }
    // detailText 为空或等于截断的 summary → 需要抓取原文获取完整内容
    String summary = value(message.summary()).trim();
    if (detailText.isBlank() || detailText.equals(summary)) {
      return true;
    }
    return false;
  }

  private String bloggerName(RecentMessage message) {
    if (!value(message.processedBloggerName()).isBlank()) {
      return message.processedBloggerName().trim();
    }
    var matcher = SOURCE.matcher(value(message.summary()));
    if (matcher.find()) {
      String name = matcher.group(1).trim();
      return name.contains("顺哥") ? "顺哥" : name;
    }
    return value(message.sourceName()).isBlank() ? "未知 KOL" : message.sourceName().trim();
  }

  private String content(RecentMessage message) {
    return value(message.detailText()).isBlank()
        ? value(message.summary()).trim()
        : message.detailText().trim();
  }

  private static String value(String input) {
    return input == null ? "" : input;
  }

  public record FeedMessage(
      String id,
      String messageKey,
      String bloggerName,
      String kolId,
      String title,
      String summary,
      String detailText,
      String sourceUrl,
      String messageTime,
      String status,
      String priceAlertRecognitionStatus,
      String priceAlertRecognitionId,
      int priceAlertCandidateCount) {
  }

  private static List<String> terms(String input) {
    return Pattern.compile("[\\p{P}\\p{S}\\s]+")
        .splitAsStream(input)
        .map(String::trim)
        .filter(term -> !term.isBlank())
        .distinct()
        .limit(8)
        .toList();
  }

  private static int relevance(RecentMessage message, String query, List<String> terms) {
    String blogger = normalize(value(message.processedBloggerName()) + " " + value(message.sourceName()));
    String title = normalize(message.title());
    String summary = normalize(message.summary());
    String detail = normalize(searchableDetail(message.detailText()));
    int score = fieldScore(title, query, terms, 0, 12)
        + fieldScore(blogger, query, terms, 2, 10)
        + fieldScore(summary, query, terms, 5, 6)
        + fieldScore(detail, query, terms, 8, 3);
    return score;
  }

  private static int fieldScore(
      String field,
      String query,
      List<String> terms,
      int phraseScore,
      int tokenWeight) {
    if (field.isBlank()) return 100;
    int score = field.equals(query) ? phraseScore - 4 : field.contains(query) ? phraseScore : 30;
    long hits = terms.stream().filter(field::contains).count();
    return score - (int) hits * tokenWeight;
  }

  private static String normalize(String input) {
    if (input == null) return "";
    return Normalizer.normalize(input, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .replaceAll("\\s+", " ")
        .trim();
  }

  private static boolean matchesAllTerms(RecentMessage message, List<String> terms) {
    String searchable = normalize(String.join(" ",
        value(message.processedBloggerName()),
        value(message.sourceName()),
        value(message.title()),
        value(message.summary()),
        searchableDetail(message.detailText())));
    return terms.stream().allMatch(searchable::contains);
  }

  private static String searchableDetail(String detailText) {
    return IMAGE_PAYLOAD.matcher(value(detailText)).replaceAll("");
  }

  private static Instant time(String value) {
    try {
      return Instant.parse(value);
    } catch (RuntimeException ignored) {
      return Instant.EPOCH;
    }
  }

  private record SearchHit(FeedMessage message, int score) {
  }
}
