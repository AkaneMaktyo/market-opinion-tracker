package com.personal.tracker.service.wxpusher;

import com.personal.tracker.domain.LiveSession;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.JdbcSupport;
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
import com.personal.tracker.service.wxpusher.fallback.WxPusherFallbackOpinionExtractor;
import com.personal.tracker.service.wxpusher.instruments.MessageInstrumentExtractor;
import com.personal.tracker.service.wxpusher.ocr.WxPusherImageOcrService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WxPusherIngestionService {
  private static final String CONSUMER_NAME = "market_opinion_tracker";
  private static final String WXPUSHER_LLM_DISABLED = "WxPusher LLM 提取已禁用";
  private static final String WXPUSHER_SOURCE_UNCONFIGURED = "WxPusher 消息来源未配置为 KOL";
  private final SessionRepository sessionRepository;
  private final WxPusherSettingsRepository settingsRepository;
  private final WxPusherBloggerRepository bloggerRepository;
  private final WxPusherMessageRepository messageRepository;
  private final WxPusherSharedMessageRepository sharedRepository;
  private final WxPusherArticleExtractor articleExtractor;
  private final WxPusherImageOcrService imageOcrService;
  private final OpenAiJsonExtractor aiExtractor;
  private final JsonOpinionParser parser;
  private final OpinionImportWriter writer;
  private final InstrumentRepository instruments;

  public WxPusherIngestionService(
      SessionRepository sessionRepository,
      WxPusherSettingsRepository settingsRepository,
      WxPusherBloggerRepository bloggerRepository,
      WxPusherMessageRepository messageRepository,
      WxPusherSharedMessageRepository sharedRepository,
      WxPusherArticleExtractor articleExtractor,
      WxPusherImageOcrService imageOcrService,
      OpenAiJsonExtractor aiExtractor,
      JsonOpinionParser parser,
      OpinionImportWriter writer,
      InstrumentRepository instruments) {
    this.sessionRepository = sessionRepository;
    this.settingsRepository = settingsRepository;
    this.bloggerRepository = bloggerRepository;
    this.messageRepository = messageRepository;
    this.sharedRepository = sharedRepository;
    this.articleExtractor = articleExtractor;
    this.imageOcrService = imageOcrService;
    this.aiExtractor = aiExtractor;
    this.parser = parser;
    this.writer = writer;
    this.instruments = instruments;
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

  public OcrBackfillResult backfillOcrHistory(int limit) {
    int eligible = 0;
    int converted = 0;
    int imported = 0;
    List<String> failedIds = new ArrayList<>();
    for (WxPusherMessage message : messageRepository.listLegacyImageMessages(limit)) {
      if (!imageOcrService.requiresSourceRefresh(message.bloggerName(), message.detailText())) {
        continue;
      }
      eligible++;
      try {
        if ("IMPORTED".equalsIgnoreCase(message.status())) {
          refreshImportedOcrText(message);
        } else {
          process(message, null);
        }
        WxPusherMessage updated = messageRepository.findById(message.id()).orElse(message);
        if (imageOcrService.containsOcrText(updated.detailText())) {
          converted++;
          if ("IMPORTED".equalsIgnoreCase(updated.status())) {
            imported++;
          }
        } else {
          failedIds.add(message.id());
        }
      } catch (RuntimeException error) {
        failedIds.add(message.id());
      }
    }
    return new OcrBackfillResult(
        eligible,
        converted,
        imported,
        failedIds.size(),
        failedIds.stream().limit(50).toList());
  }

  private boolean importFromShared(WxPusherClient.IncomingMessage incoming) {
    var matched = matchBlogger(incoming);
    if (matched.isEmpty()) {
      sharedRepository.saveState(CONSUMER_NAME, incoming.messageKey(), "IGNORED", "", "");
      return false;
    }
    return processMatched(incoming, matched.get(), incoming.messageKey());
  }

  private void refreshImportedOcrText(WxPusherMessage message) {
    String detailText = fetchDetail(message);
    detailText = imageOcrService.convert(message.bloggerName(), detailText);
    if (!imageOcrService.containsOcrText(detailText)) {
      throw new IllegalStateException("历史消息没有取得可入库的图片文字");
    }
    ensureSession(message, detailText);
    messageRepository.updateDetailText(message.id(), detailText);
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
      if (unconfiguredDetailSource(message, detailText)) {
        messageRepository.markSkipped(message.id(), detailText, WXPUSHER_SOURCE_UNCONFIGURED);
        saveSharedState(sharedMessageKey, "SKIPPED", WXPUSHER_SOURCE_UNCONFIGURED, message.id());
        return;
      }
      message = reassignFromDetail(message, detailText);
      detailText = imageOcrService.convert(message.bloggerName(), detailText);
      sessionId = ensureSession(message, detailText);
      saveMentionedInstruments(message, detailText);
      if (!aiExtractor.extractionEnabled()) {
        importKeywordFallback(message, sharedMessageKey, detailText, sessionId);
        return;
      }
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
      var current = sessionRepository.findById(message.sessionId());
      if (current.isPresent() && current.get().kolId().equals(message.kolId())) {
        if (imageOcrService.containsOcrText(detailText)) {
          sessionRepository.updateRawText(message.sessionId(), detailText);
        }
        return message.sessionId();
      }
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

  private WxPusherMessage reassignFromDetail(WxPusherMessage message, String detailText) {
    WxPusherClient.IncomingMessage detailView = detailMessage(message, detailText);
    return WxPusherBloggerMatcher.match(detailView, bloggerRepository.enabled())
        .filter(blogger -> !blogger.kolId().equals(message.kolId()))
        .map(blogger -> {
          messageRepository.reassign(message.id(), blogger.kolId(), blogger.bloggerName());
          return withBlogger(message, blogger);
        })
        .orElse(message);
  }

  private boolean unconfiguredDetailSource(WxPusherMessage message, String detailText) {
    WxPusherClient.IncomingMessage detailView = detailMessage(message, detailText);
    return blockedExternalSource(detailText)
        || WxPusherBloggerMatcher.hasExplicitSource(detailView)
        && WxPusherBloggerMatcher.match(detailView, bloggerRepository.enabled()).isEmpty();
  }

  private boolean blockedExternalSource(String detailText) {
    String value = (detailText == null ? "" : detailText).toLowerCase();
    return value.contains("premium signals")
        || value.contains("\u9ec4\u91d1\u5e1d\u56fd")
        || value.contains("goldenempire")
        || value.contains("forex-nightvex")
        || value.contains("\u8212\u7434\u884c\u60c5\u5206\u6790")
        || value.contains("\u61c2\u5e01\u732b")
        || value.contains("\u989c\u9a70")
        || value.contains("yekoi")
        || value.contains("ye-\u65f6\u95f4\u9886\u4e3b");
  }

  private WxPusherClient.IncomingMessage detailMessage(WxPusherMessage message, String detailText) {
    return new WxPusherClient.IncomingMessage(
        "detail",
        message.messageKey(),
        "",
        message.title(),
        detailText,
        message.detailUrl(),
        message.sourceUrl(),
        message.messageTime(),
        "",
        message.rawPayloadJson(),
        0L);
  }

  private WxPusherMessage withBlogger(WxPusherMessage message, WxPusherBlogger blogger) {
    return new WxPusherMessage(
        message.id(),
        message.messageKey(),
        blogger.kolId(),
        blogger.bloggerName(),
        message.title(),
        message.summary(),
        message.detailUrl(),
        message.sourceUrl(),
        message.messageTime(),
        message.rawPayloadJson(),
        message.detailText(),
        message.llmOutputJson(),
        message.status(),
        message.errorMessage(),
        message.sessionId(),
        message.createdAt(),
        message.updatedAt());
  }

  private String sessionTitle(WxPusherMessage message) {
    return "WxPusher / " + message.bloggerName() + " / " + message.messageTime();
  }

  private String fetchDetail(WxPusherMessage message) {
    boolean refreshLegacyImage = imageOcrService.requiresSourceRefresh(
        message.bloggerName(),
        message.detailText());
    if (message.detailText() != null && !message.detailText().isBlank() && !refreshLegacyImage) {
      return unusableDetail(message.detailText()) ? fallbackText(message) : message.detailText();
    }
    if (isRetryState(message.status()) && !refreshLegacyImage) {
      return fallbackText(message);
    }
    if (message.detailUrl() == null || message.detailUrl().isBlank()) {
      return fallbackText(message);
    }
    try {
      String text = articleExtractor.fetchText(message.detailUrl(), settingsRepository.get());
      return text == null || text.isBlank() || unusableDetail(text) ? fallbackText(message) : text;
    } catch (RuntimeException error) {
      return fallbackText(message);
    }
  }

  private boolean unusableDetail(String text) {
    if (text.contains("\u6d88\u606f\u5185\u5bb9\u4e0d\u5b58\u5728")
        || text.contains("\u9519\u8bef\u63d0\u793a")) {
      return true;
    }
    return text.contains("消息内容不存在") || text.contains("错误提示");
  }

  private boolean isRetryState(String status) {
    return "SKIPPED".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status);
  }

  private String fallbackText(WxPusherMessage message) {
    return List.of(message.title(), message.summary(), message.sourceUrl()).stream()
        .filter(item -> item != null && !item.isBlank())
        .reduce((left, right) -> left + "\n" + right)
        .orElse("");
  }

  private String storedText(WxPusherMessage message) {
    if (message.detailText() != null && !message.detailText().isBlank()) {
      return unusableDetail(message.detailText()) ? fallbackText(message) : message.detailText();
    }
    return fallbackText(message);
  }

  private void saveMentionedInstruments(WxPusherMessage message, String detailText) {
    String text = String.join("\n", detailText, message.summary(), message.title());
    for (String symbol : MessageInstrumentExtractor.extract(text)) {
      instruments.saveIfAbsent(symbol, symbol, JdbcSupport.market("", symbol), null);
    }
  }

  private void importKeywordFallback(
      WxPusherMessage message,
      String sharedMessageKey,
      String detailText,
      String sessionId) {
    String text = String.join("\n", detailText, message.summary(), message.title());
    var contextSymbols = messageRepository.recentImportedSymbols(message.kolId(), message.messageTime(), 1);
    var candidates = WxPusherFallbackOpinionExtractor.extract(text, contextSymbols);
    if (candidates.isEmpty()) {
      messageRepository.markSkipped(message.id(), detailText, WXPUSHER_LLM_DISABLED);
      saveSharedState(sharedMessageKey, "SKIPPED", WXPUSHER_LLM_DISABLED, message.id());
      return;
    }
    var result = writer.write(
        sessionId,
        message.kolId(),
        sessionTitle(message),
        sessionDate(message.messageTime()),
        "WXPUSHER_KEYWORD_FALLBACK",
        detailText,
        candidates);
    String output = "{\"fallback\":\"keyword\",\"savedOpinions\":%d}".formatted(result.savedOpinions());
    messageRepository.markImported(message.id(), detailText, output, result.sessionId());
    saveSharedState(sharedMessageKey, "IMPORTED", "", message.id());
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

  public record OcrBackfillResult(
      int eligible,
      int converted,
      int imported,
      int failed,
      List<String> failedMessageIds) {
  }
}
