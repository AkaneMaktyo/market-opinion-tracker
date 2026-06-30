package com.personal.tracker.repository.youtube;

import com.personal.tracker.repository.JdbcSupport;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class YouTubeOpinionImportRepository {
  private final JdbcTemplate jdbc;
  private final RowMapper<ImportState> mapper = (rs, rowNum) -> new ImportState(
      rs.getString("video_id"),
      rs.getString("status"),
      rs.getString("session_id"),
      rs.getString("llm_output_json"),
      rs.getString("error_message"),
      rs.getString("imported_at"),
      rs.getString("created_at"),
      rs.getString("updated_at"));

  public YouTubeOpinionImportRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<ImportState> find(String videoId) {
    return jdbc.query(
            "SELECT * FROM youtube_opinion_imports WHERE video_id = ? LIMIT 1",
            mapper,
            value(videoId))
        .stream()
        .findFirst();
  }

  public void markProcessing(String videoId) {
    save(videoId, "PROCESSING", "", "", "", "");
  }

  public void markImported(String videoId, String sessionId, String llmOutputJson) {
    save(videoId, "IMPORTED", sessionId, llmOutputJson, "", JdbcSupport.now());
  }

  public void markEmpty(String videoId, String llmOutputJson, String errorMessage) {
    save(videoId, "EMPTY", "", llmOutputJson, errorMessage, "");
  }

  public void markFailed(String videoId, String llmOutputJson, String errorMessage) {
    save(videoId, "FAILED", "", llmOutputJson, errorMessage, "");
  }

  private void save(
      String videoId,
      String status,
      String sessionId,
      String llmOutputJson,
      String errorMessage,
      String importedAt) {
    String now = JdbcSupport.now();
    jdbc.update("""
        INSERT INTO youtube_opinion_imports(
          video_id, status, session_id, llm_output_json, error_message, imported_at, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          status = VALUES(status),
          session_id = VALUES(session_id),
          llm_output_json = VALUES(llm_output_json),
          error_message = VALUES(error_message),
          imported_at = VALUES(imported_at),
          updated_at = VALUES(updated_at)
        """,
        value(videoId),
        value(status),
        value(sessionId),
        nullableText(llmOutputJson),
        nullableText(errorMessage),
        value(importedAt),
        now,
        now);
  }

  private String value(String input) {
    return input == null ? "" : input.trim();
  }

  private String nullableText(String input) {
    return input == null ? "" : input;
  }

  public record ImportState(
      String videoId,
      String status,
      String sessionId,
      String llmOutputJson,
      String errorMessage,
      String importedAt,
      String createdAt,
      String updatedAt) {
  }
}
