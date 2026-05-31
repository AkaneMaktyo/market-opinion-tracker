package com.personal.tracker.service;

import com.personal.tracker.domain.PriceLevel;
import com.personal.tracker.repository.KolRepository;
import com.personal.tracker.service.imports.OpinionImportWriter;
import com.personal.tracker.service.json.JsonOpinionParser;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ImportService {
  private final JsonOpinionParser parser;
  private final OpinionImportWriter writer;

  public ImportService(
      JsonOpinionParser parser,
      OpinionImportWriter writer) {
    this.parser = parser;
    this.writer = writer;
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
    var result = writer.write(
        value(request.kolId(), KolRepository.DEFAULT_ID),
        request.title(),
        request.sessionDate().trim(),
        "JSON_IMPORT",
        request.rawJson(),
        request.items().stream().filter(ImportCandidate::selected).toList());
    return new ImportCommitResult(result.sessionId(), result.savedOpinions());
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
      String market,
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
