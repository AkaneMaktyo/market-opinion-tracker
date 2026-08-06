package com.personal.tracker.service.imports;

import com.personal.tracker.domain.Opinion;
import com.personal.tracker.domain.LiveSession;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.KolRepository;
import com.personal.tracker.repository.OpinionRepository;
import com.personal.tracker.repository.SessionRepository;
import com.personal.tracker.service.ImportService.ImportCandidate;
import com.personal.tracker.service.OpinionService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class OpinionImportWriter {
  private static final Pattern OCR_BLOCK = Pattern.compile(
      "\\[图片转文字 \\d+]\\s*([\\s\\S]*?)\\s*\\[/图片转文字]");
  private final SessionRepository sessions;
  private final OpinionService opinions;
  private final InstrumentRepository instruments;
  private final OpinionRepository opinionRepository;

  public OpinionImportWriter(
      SessionRepository sessions,
      OpinionService opinions,
      InstrumentRepository instruments,
      OpinionRepository opinionRepository) {
    this.sessions = sessions;
    this.opinions = opinions;
    this.instruments = instruments;
    this.opinionRepository = opinionRepository;
  }

  public WriteResult write(
      String sessionId,
      String kolId,
      String title,
      String sessionDate,
      String source,
      String rawText,
      List<ImportCandidate> items) {
    LiveSession session = existingOrCreateSession(sessionId, kolId, title, sessionDate, source, rawText);
    int saved = 0;
    for (ImportCandidate item : safe(items)) {
      opinions.create(new OpinionService.CreateOpinionCommand(
          session.id(),
          item.symbol(),
          item.displayName(),
          item.market(),
          null,
          item.direction(),
          item.positionAction(),
          value(item.horizon(), "未指定"),
          item.thesis(),
          item.triggerCondition(),
          item.risksText(),
          null,
          item.sourceQuote(),
          null,
          item.rawDirection(),
          item.risksText(),
          item.catalystsText(),
          item.priceNotesText(),
          item.rawItemJson(),
          sessionDate + "T09:30:00",
          item.priceLevels()));
      saved++;
    }
    return new WriteResult(session.id(), saved);
  }

  public WriteResult write(
      String kolId,
      String title,
      String sessionDate,
      String source,
      String rawText,
      List<ImportCandidate> items) {
    return write("", kolId, title, sessionDate, source, rawText, items);
  }

  public WriteResult writeMessageFallback(
      String sessionId,
      String messageId,
      String kolId,
      String title,
      String sessionDate,
      String rawText,
      String opinionTime,
      List<ImportCandidate> items) {
    LiveSession session = existingOrCreateSession(
        sessionId, kolId, title, sessionDate, "WXPUSHER_OCR_MESSAGE", rawText);
    String sourceQuote = extractOcrText(rawText);
    int saved = 0;
    for (ImportCandidate item : safe(items)) {
      var instrument = instruments.saveIfAbsent(
          item.symbol(), item.displayName(), item.market(), null);
      String symbol = instrument.symbol();
      opinionRepository.upsertMessage(messageOpinionId(messageId, symbol), new Opinion(
          null,
          session.id(),
          instrument.id(),
          symbol,
          "WATCH",
          "消息",
          value(item.thesis(), sourceQuote),
          "",
          "",
          null,
          sourceQuote,
          null,
          "OCR文字",
          "",
          "",
          "",
          messageJson(messageId, symbol),
          value(opinionTime, sessionDate + "T09:30:00"),
          "MESSAGE",
          null));
      saved++;
    }
    return new WriteResult(session.id(), saved);
  }

  public void removeMessageFallbacks(String sessionId) {
    if (sessionId != null && !sessionId.isBlank()) {
      opinionRepository.deleteMessageFallbacks(sessionId);
    }
  }

  public void updateSessionSourceQuote(String sessionId, String rawText) {
    if (sessionId != null && !sessionId.isBlank()) {
      opinionRepository.updateSourceQuoteBySession(sessionId, extractOcrText(rawText));
    }
  }

  private LiveSession existingOrCreateSession(
      String sessionId,
      String kolId,
      String title,
      String sessionDate,
      String source,
      String rawText) {
    if (sessionId != null && !sessionId.isBlank()) {
      return sessions.findById(sessionId)
          .orElseGet(() -> createSession(kolId, title, sessionDate, source, rawText));
    }
    return createSession(kolId, title, sessionDate, source, rawText);
  }

  private LiveSession createSession(
      String kolId,
      String title,
      String sessionDate,
      String source,
      String rawText) {
    return sessions.create(
        value(kolId, KolRepository.DEFAULT_ID),
        sessionDate,
        value(title, "JSON 导入直播"),
        source,
        value(rawText, ""));
  }

  private static List<ImportCandidate> safe(List<ImportCandidate> items) {
    return items == null ? List.of() : items;
  }

  private static String value(String input, String fallback) {
    return input == null || input.isBlank() ? fallback : input.trim();
  }

  private static String extractOcrText(String rawText) {
    var matcher = OCR_BLOCK.matcher(value(rawText, ""));
    StringBuilder result = new StringBuilder();
    while (matcher.find()) {
      if (!result.isEmpty()) {
        result.append("\n\n");
      }
      result.append(matcher.group(1).trim());
    }
    return result.isEmpty() ? value(rawText, "") : result.toString();
  }

  private static String messageOpinionId(String messageId, String symbol) {
    String source = value(messageId, "unknown") + ":" + value(symbol, "UNKNOWN");
    return "wxmsg-" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
  }

  private static String messageJson(String messageId, String symbol) {
    return "{\"fallback\":\"message\",\"sourceMessageId\":\"%s\",\"symbol\":\"%s\"}"
        .formatted(value(messageId, ""), value(symbol, ""));
  }

  public record WriteResult(String sessionId, int savedOpinions) {
  }
}
