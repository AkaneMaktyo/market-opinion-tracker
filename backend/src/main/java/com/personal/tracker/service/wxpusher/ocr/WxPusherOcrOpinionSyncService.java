package com.personal.tracker.service.wxpusher.ocr;

import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.WxPusherMessage;
import com.personal.tracker.service.imports.OpinionImportWriter;
import com.personal.tracker.service.wxpusher.fallback.WxPusherFallbackOpinionExtractor;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class WxPusherOcrOpinionSyncService {
  private final WxPusherMessageRepository messages;
  private final WxPusherImageOcrService imageOcr;
  private final OpinionImportWriter writer;

  public WxPusherOcrOpinionSyncService(
      WxPusherMessageRepository messages,
      WxPusherImageOcrService imageOcr,
      OpinionImportWriter writer) {
    this.messages = messages;
    this.imageOcr = imageOcr;
    this.writer = writer;
  }

  public int saveFallback(WxPusherMessage message, String detailText, String sessionId) {
    if (!imageOcr.containsOcrText(detailText)) {
      return 0;
    }
    String text = String.join("\n", detailText, message.summary(), message.title());
    var contextSymbols = messages.recentImportedSymbols(
        message.kolId(), message.messageTime(), 1);
    var candidates = WxPusherFallbackOpinionExtractor.extract(text, contextSymbols);
    if (candidates.isEmpty()) {
      return 0;
    }
    return writer.writeMessageFallback(
        sessionId,
        message.id(),
        message.kolId(),
        sessionTitle(message),
        sessionDate(message.messageTime()),
        detailText,
        message.messageTime(),
        candidates).savedOpinions();
  }

  public void updateImportedSource(String sessionId, String detailText) {
    if (imageOcr.containsOcrText(detailText)) {
      writer.updateSessionSourceQuote(sessionId, detailText);
    }
  }

  private String sessionTitle(WxPusherMessage message) {
    return "WxPusher / " + message.bloggerName() + " / " + message.messageTime();
  }

  private String sessionDate(String messageTime) {
    if (messageTime != null && messageTime.length() >= 10) {
      return messageTime.substring(0, 10);
    }
    return LocalDate.now().toString();
  }
}
