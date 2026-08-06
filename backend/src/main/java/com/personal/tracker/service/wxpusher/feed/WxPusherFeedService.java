package com.personal.tracker.service.wxpusher.feed;

import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSharedMessageRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSharedMessageRepository.RecentMessage;
import com.personal.tracker.service.wxpusher.WxPusherArticleExtractor;
import com.personal.tracker.service.wxpusher.article.WxPusherArticleParser;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class WxPusherFeedService {
  private static final Pattern SOURCE = Pattern.compile(
      "\\[[^\\]\\n]*?｜\\s*([^\\]\\n:]{2,40})\\]");
  private static final Pattern IMAGE_HINT = Pattern.compile(
      "(?i)\\.(?:jpe?g|png|gif|webp)(?:\\s|$|[?#])");
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
        value(message.title()),
        value(message.summary()),
        detailText,
        value(message.sourceUrl()),
        value(message.messageTime()),
        value(message.status()));
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
      String title,
      String summary,
      String detailText,
      String sourceUrl,
      String messageTime,
      String status) {
  }
}
