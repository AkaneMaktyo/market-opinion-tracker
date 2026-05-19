package com.personal.tracker.service;

import com.personal.tracker.domain.LiveSession;
import com.personal.tracker.domain.PriceLevel;
import com.personal.tracker.repository.KolRepository;
import com.personal.tracker.repository.SessionRepository;
import com.personal.tracker.service.json.JsonOpinionParser;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ImportService {
  private final SessionRepository sessions;
  private final OpinionService opinions;
  private final JsonOpinionParser parser;

  public ImportService(
      SessionRepository sessions,
      OpinionService opinions,
      JsonOpinionParser parser) {
    this.sessions = sessions;
    this.opinions = opinions;
    this.parser = parser;
  }

  public ImportPreview preview(ImportPreviewRequest request) {
    requireImportInput(request.sessionDate(), request.rawJson());
    try {
      return parser.parse(request.rawJson());
    } catch (IllegalArgumentException error) {
      throw error;
    } catch (Exception error) {
      throw new IllegalArgumentException("JSON 解析失败: " + error.getMessage());
    }
  }

  public ImportCommitResult commit(ImportCommitRequest request) {
    requireImportInput(request.sessionDate(), request.rawJson());
    String date = request.sessionDate().trim();
    LiveSession session = sessions.create(
        value(request.kolId(), KolRepository.DEFAULT_ID),
        date,
        value(request.title(), "JSON 导入直播"),
        "JSON_IMPORT",
        request.rawJson());
    int saved = 0;
    for (ImportCandidate item : request.items()) {
      if (!item.selected()) {
        continue;
      }
      opinions.create(new OpinionService.CreateOpinionCommand(
          session.id(), item.symbol(), item.displayName(), null,
          item.direction(), value(item.horizon(), "未指定"), item.thesis(), item.triggerCondition(),
          item.risksText(), null, item.sourceQuote(), null,
          item.rawDirection(), item.risksText(), item.catalystsText(),
          item.priceNotesText(), item.rawItemJson(), date + "T09:30:00",
          item.priceLevels()));
      saved++;
    }
    return new ImportCommitResult(session.id(), saved);
  }

  private void requireImportInput(String sessionDate, String rawJson) {
    if (sessionDate == null || sessionDate.isBlank()) {
      throw new IllegalArgumentException("请先选择直播时间节点");
    }
    if (rawJson == null || rawJson.isBlank()) {
      throw new IllegalArgumentException("请先粘贴 JSON 内容");
    }
  }

  private String value(String input, String fallback) {
    return input == null || input.isBlank() ? fallback : input.trim();
  }

  public record ImportPreviewRequest(String kolId, String title, String sessionDate, String rawJson) {
  }

  public record ImportPreview(
      List<String> summary,
      List<String> mappingNotes,
      List<ImportCandidate> candidates,
      List<SkippedItem> skipped) {
  }

  public record ImportCandidate(
      boolean selected,
      String symbol,
      String displayName,
      String direction,
      String rawDirection,
      String horizon,
      String thesis,
      String catalystsText,
      String triggerCondition,
      String risksText,
      String priceNotesText,
      String sourceQuote,
      String rawItemJson,
      List<PriceLevel> priceLevels) {
  }

  public record SkippedItem(String name, String reason) {
  }

  public record ImportCommitRequest(
      String kolId,
      String title,
      String sessionDate,
      String rawJson,
      List<ImportCandidate> items) {
    public ImportCommitRequest {
      items = items == null ? List.of() : items;
    }
  }

  public record ImportCommitResult(String sessionId, int savedOpinions) {
  }
}
