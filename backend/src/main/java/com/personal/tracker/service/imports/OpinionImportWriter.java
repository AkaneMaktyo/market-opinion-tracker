package com.personal.tracker.service.imports;

import com.personal.tracker.domain.LiveSession;
import com.personal.tracker.repository.KolRepository;
import com.personal.tracker.repository.SessionRepository;
import com.personal.tracker.service.ImportService.ImportCandidate;
import com.personal.tracker.service.OpinionService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OpinionImportWriter {
  private final SessionRepository sessions;
  private final OpinionService opinions;

  public OpinionImportWriter(
      SessionRepository sessions,
      OpinionService opinions) {
    this.sessions = sessions;
    this.opinions = opinions;
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

  public record WriteResult(String sessionId, int savedOpinions) {
  }
}
