package com.personal.tracker.service.wxpusher;

import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.WxPusherBlogger;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.PendingMessage;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.WxPusherMessage;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository.WxPusherSettings;
import com.personal.tracker.service.imports.OpinionImportWriter;
import com.personal.tracker.service.json.JsonOpinionParser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WxPusherIngestionService {
  private final WxPusherSettingsRepository settingsRepository;
  private final WxPusherBloggerRepository bloggerRepository;
  private final WxPusherMessageRepository messageRepository;
  private final WxPusherClient client;
  private final WxPusherArticleExtractor articleExtractor;
  private final OpenAiJsonExtractor aiExtractor;
  private final JsonOpinionParser parser;
  private final OpinionImportWriter writer;

  public WxPusherIngestionService(
      WxPusherSettingsRepository settingsRepository,
      WxPusherBloggerRepository bloggerRepository,
      WxPusherMessageRepository messageRepository,
      WxPusherClient client,
      WxPusherArticleExtractor articleExtractor,
      OpenAiJsonExtractor aiExtractor,
      JsonOpinionParser parser,
      OpinionImportWriter writer) {
    this.settingsRepository = settingsRepository;
    this.bloggerRepository = bloggerRepository;
    this.messageRepository = messageRepository;
    this.client = client;
    this.articleExtractor = articleExtractor;
    this.aiExtractor = aiExtractor;
    this.parser = parser;
    this.writer = writer;
  }

  public void ingest(WxPusherClient.IncomingMessage incoming) {
    var matched = WxPusherBloggerMatcher.match(incoming, bloggerRepository.enabled());
    if (matched.isEmpty()) {
      return;
    }
    var saved = messageRepository.createPending(new PendingMessage(
        incoming.messageKey(),
        matched.get().kolId(),
        matched.get().bloggerName(),
        incoming.title(),
        incoming.summary(),
        incoming.detailUrl(),
        incoming.sourceUrl(),
        incoming.messageTime(),
        incoming.rawPayloadJson()));
    if (!saved.created()) {
      return;
    }
    process(saved.message());
  }

  public void seedHistory() {
    WxPusherSettings settings = settingsRepository.get();
    if (!settings.pollingReady()) {
      return;
    }
    for (WxPusherBlogger blogger : bloggerRepository.enabledPendingSeed()) {
      seedBlogger(settings, blogger);
      bloggerRepository.markSeedCompleted(blogger.id());
    }
  }

  public void retry(String messageId) {
    WxPusherMessage message = messageRepository.findById(messageId)
        .orElseThrow(() -> new IllegalArgumentException("消息不存在"));
    if ("IMPORTED".equalsIgnoreCase(message.status())) {
      throw new IllegalArgumentException("已入库消息不支持重试");
    }
    process(message);
  }

  private void seedBlogger(WxPusherSettings settings, WxPusherBlogger blogger) {
    List<WxPusherClient.IncomingMessage> matched = new ArrayList<>();
    String cursor = client.maxCursor();
    for (int page = 0; page < 8 && matched.size() < 30; page++) {
      List<WxPusherClient.IncomingMessage> items = client.fetchPage(settings, cursor);
      if (items.isEmpty()) {
        break;
      }
      matched.addAll(items.stream()
          .filter(item -> WxPusherBloggerMatcher.matches(item, blogger))
          .toList());
      String nextCursor = items.get(items.size() - 1).pageCursor();
      if (nextCursor == null || nextCursor.isBlank() || nextCursor.equals(cursor)) {
        break;
      }
      cursor = nextCursor;
    }
    matched.stream()
        .sorted(Comparator.comparingLong(WxPusherClient.IncomingMessage::sortValue))
        .skip(Math.max(0, matched.size() - 30))
        .forEach(this::ingest);
  }

  private void process(WxPusherMessage message) {
    messageRepository.markProcessing(message.id());
    String detailText = fallbackText(message);
    String llmJson = "";
    try {
      detailText = fetchDetail(message);
      llmJson = aiExtractor.extract(
          message.bloggerName(),
          message.title(),
          message.summary(),
          detailText,
          message.sourceUrl());
      var preview = parser.parse(llmJson);
      if (preview.candidates().isEmpty()) {
        throw new IllegalStateException("模型没有提取到任何可入库观点");
      }
      var result = writer.write(
          message.kolId(),
          "WxPusher / " + message.bloggerName() + " / " + message.messageTime(),
          sessionDate(message.messageTime()),
          "WXPUSHER_AUTO",
          llmJson,
          preview.candidates());
      messageRepository.markImported(message.id(), detailText, llmJson, result.sessionId());
    } catch (Exception error) {
      messageRepository.markFailed(message.id(), detailText, llmJson, error.getMessage());
    }
  }

  private String fetchDetail(WxPusherMessage message) {
    if (message.detailUrl() == null || message.detailUrl().isBlank()) {
      return fallbackText(message);
    }
    try {
      String text = articleExtractor.fetchText(message.detailUrl(), settingsRepository.get());
      return text == null || text.isBlank() ? fallbackText(message) : text;
    } catch (RuntimeException error) {
      return fallbackText(message);
    }
  }

  private String fallbackText(WxPusherMessage message) {
    return List.of(message.title(), message.summary(), message.sourceUrl()).stream()
        .filter(item -> item != null && !item.isBlank())
        .reduce((left, right) -> left + "\n" + right)
        .orElse("");
  }

  private String sessionDate(String messageTime) {
    if (messageTime != null && messageTime.length() >= 10) {
      return messageTime.substring(0, 10);
    }
    return LocalDate.now().toString();
  }
}
