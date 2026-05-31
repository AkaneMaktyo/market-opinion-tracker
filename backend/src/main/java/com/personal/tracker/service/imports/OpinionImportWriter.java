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
      String kolId,
      String title,
      String sessionDate,
      String source,
      String rawText,
      List<ImportCandidate> items) {
    LiveSession session = sessions.create(
        value(kolId, KolRepository.DEFAULT_ID),
        sessionDate,
        value(title, "JSON 导入直播"),
        source,
        value(rawText, ""));
    int saved = 0;
    for (ImportCandidate item : safe(items)) {
      opinions.create(new OpinionService.CreateOpinionCommand(
          session.id(),
          item.symbol(),
          item.displayName(),
          item.market(),
          null,
          item.direction(),
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

  private static List<ImportCandidate> safe(List<ImportCandidate> items) {
    return items == null ? List.of() : items;
  }

  private static String value(String input, String fallback) {
    return input == null || input.isBlank() ? fallback : input.trim();
  }

  public record WriteResult(String sessionId, int savedOpinions) {
  }
}
