package com.personal.tracker.service.wxpusher;

import com.personal.tracker.domain.LiveSession;
import com.personal.tracker.repository.SessionRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.WxPusherBlogger;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.PendingMessage;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.SaveResult;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.WxPusherMessage;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSharedMessageRepository;
import com.personal.tracker.service.imports.OpinionImportWriter;
import com.personal.tracker.service.json.JsonOpinionParser;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WxPusherIngestionService {
  private static final String CONSUMER_NAME = "market_opinion_tracker";
  private final SessionRepository sessionRepository;
  private final WxPusherSettingsRepository settingsRepository;
  private final WxPusherBloggerRepository bloggerRepository;
  private final WxPusherMessageRepository messageRepository;
  private final WxPusherSharedMessageRepository sharedRepository;
  private final WxPusherArticleExtractor articleExtractor;
  private final OpenAiJsonExtractor aiExtractor;
  private final JsonOpinionParser parser;
  private final OpinionImportWriter writer;

  public WxPusherIngestionService(
      SessionRepository sessionRepository,
      WxPusherSettingsRepository settingsRepository,
      WxPusherBloggerRepository bloggerRepository,
      WxPusherMessageRepository messageRepository,
      WxPusherSharedMessageRepository sharedRepository,
      WxPusherArticleExtractor articleExtractor,
      OpenAiJsonExtractor aiExtractor,
      JsonOpinionParser parser,
      OpinionImportWriter writer) {
    this.sessionRepository = sessionRepository;
    this.settingsRepository = settingsRepository;
    this.bloggerRepository = bloggerRepository;
    this.messageRepository = messageRepository;
    this.sharedRepository = sharedRepository;
    this.articleExtractor = articleExtractor;
    this.aiExtractor = aiExtractor;
    this.parser = parser;
    this.writer = writer;
  }

  public int importPending() {
    int imported = 0;
    for (var incoming : sharedRepository.listPending(CONSUMER_NAME, 60)) {
      if (importFromShared(incoming)) {
        imported++;
      }
    }
    return imported;
  }

  public void ingest(WxPusherClient.IncomingMessage incoming) {
    matchBlogger(incoming).ifPresent(blogger -> processMatched(incoming, blogger, null));
  }

  public int ensureMessageSessions(int limit) {
    int hydrated = 0;
    for (WxPusherMessage message : messageRepository.listMissingSessions(limit)) {
      ensureSession(message, storedText(message));
      hydrated++;
    }
    return hydrated;
  }

  public void seedHistory() {
    List<WxPusherClient.IncomingMessage> recent = sharedRepository.listRecent(600);
    if (recent.isEmpty()) {
      return;
    }
    for (WxPusherBlogger blogger : bloggerRepository.enabledPendingSeed()) {
      recent.stream()
          .filter(item -> WxPusherBloggerMatcher.matches(item, blogger))
          .sorted(Comparator.comparingLong(WxPusherClient.IncomingMessage::sortValue))
          .skip(Math.max(0, recent.stream().filter(item -> WxPusherBloggerMatcher.matches(item, blogger)).count() - 30))
          .forEach(item -> processMatched(item, blogger, item.messageKey()));
      bloggerRepository.markSeedCompleted(blogger.id());
    }
  }

  public void retry(String messageId) {
    WxPusherMessage message = messageRepository.findById(messageId)
        .orElseThrow(() -> new IllegalArgumentException("消息不存在"));
    if ("IMPORTED".equalsIgnoreCase(message.status())) {
      throw new IllegalArgumentException("已入库消息不支持重试");
    }
    process(message, null);
  }

  private boolean importFromShared(WxPusherClient.IncomingMessage incoming) {
    var matched = matchBlogger(incoming);
    if (matched.isEmpty()) {
      sharedRepository.saveState(CONSUMER_NAME, incoming.messageKey(), "IGNORED", "", "");
      return false;
    }
    return processMatched(incoming, matched.get(), incoming.messageKey());
  }

  private boolean processMatched(
      WxPusherClient.IncomingMessage incoming,
      WxPusherBlogger blogger,
      String sharedMessageKey) {
    SaveResult saved = messageRepository.createPending(new PendingMessage(
        incoming.messageKey(),
        blogger.kolId(),
        blogger.bloggerName(),
        incoming.title(),
        incoming.summary(),
        incoming.detailUrl(),
        incoming.sourceUrl(),
        incoming.messageTime(),
        incoming.rawPayloadJson()));
    if (!saved.created() && "IMPORTED".equalsIgnoreCase(saved.message().status())) {
      saveSharedState(sharedMessageKey, "IMPORTED", "", saved.message().id());
      return false;
    }
    process(saved.message(), sharedMessageKey);
    return saved.created();
  }

  private java.util.Optional<WxPusherBlogger> matchBlogger(WxPusherClient.IncomingMessage incoming) {
    return WxPusherBloggerMatcher.match(incoming, bloggerRepository.enabled());
  }

  private void process(WxPusherMessage message, String sharedMessageKey) {
    messageRepository.markProcessing(message.id());
    saveSharedState(sharedMessageKey, "PROCESSING", "", message.id());
    String detailText = fallbackText(message);
    String llmJson = "";
    String sessionId = "";
    try {
      detailText = fetchDetail(message);
      sessionId = ensureSession(message, detailText);
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
          sessionId,
          message.kolId(),
          sessionTitle(message),
          sessionDate(message.messageTime()),
          "WXPUSHER_AUTO",
          detailText,
          preview.candidates());
      messageRepository.markImported(message.id(), detailText, llmJson, result.sessionId());
      saveSharedState(sharedMessageKey, "IMPORTED", "", message.id());
    } catch (Exception error) {
      messageRepository.markFailed(message.id(), detailText, llmJson, error.getMessage());
      saveSharedState(sharedMessageKey, "FAILED", error.getMessage(), message.id());
    }
  }

  private String ensureSession(WxPusherMessage message, String detailText) {
    if (message.sessionId() != null && !message.sessionId().isBlank()) {
      return message.sessionId();
    }
    LiveSession session = sessionRepository.create(
        message.kolId(),
        sessionDate(message.messageTime()),
        sessionTitle(message),
        "WXPUSHER_AUTO",
        detailText);
    messageRepository.attachSession(message.id(), session.id());
    return session.id();
  }

  private String sessionTitle(WxPusherMessage message) {
    return "WxPusher / " + message.bloggerName() + " / " + message.messageTime();
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

  private String storedText(WxPusherMessage message) {
    if (message.detailText() != null && !message.detailText().isBlank()) {
      return message.detailText();
    }
    return fallbackText(message);
  }

  private void saveSharedState(String messageKey, String status, String errorMessage, String derivedId) {
    if (messageKey == null || messageKey.isBlank()) {
      return;
    }
    sharedRepository.saveState(CONSUMER_NAME, messageKey, status, errorMessage, derivedId);
  }

  private String sessionDate(String messageTime) {
    if (messageTime != null && messageTime.length() >= 10) {
      return messageTime.substring(0, 10);
    }
    return LocalDate.now().toString();
  }
}
