package com.personal.tracker.repository.llm;

import com.personal.tracker.repository.JdbcSupport;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class LlmCallLogRepository {
  private static final int PREVIEW_LIMIT = 500;
  private final JdbcTemplate jdbc;
  private final RowMapper<LlmCallLog> mapper = (rs, rowNum) -> new LlmCallLog(
      rs.getString("id"),
      rs.getString("scene"),
      rs.getString("model"),
      rs.getString("status"),
      rs.getInt("request_chars"),
      rs.getInt("response_chars"),
      rs.getLong("duration_ms"),
      rs.getString("request_preview"),
      rs.getString("response_preview"),
      rs.getString("error_message"),
      rs.getString("created_at"));

  public LlmCallLogRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void create(LogEntry entry) {
    jdbc.update("""
        INSERT INTO llm_call_logs(
          id, scene, model, status, request_chars, response_chars, duration_ms,
          request_preview, response_preview, error_message, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        JdbcSupport.id(),
        text(entry.scene()),
        text(entry.model()),
        text(entry.status()),
        Math.max(0, entry.requestChars()),
        Math.max(0, entry.responseChars()),
        Math.max(0L, entry.durationMs()),
        preview(entry.requestPreview()),
        preview(entry.responsePreview()),
        text(entry.errorMessage()),
        JdbcSupport.now());
  }

  public List<LlmCallLog> list(String date, int limit) {
    StringBuilder sql = new StringBuilder("SELECT * FROM llm_call_logs");
    List<Object> args = new ArrayList<>();
    if (!safeDate(date).isBlank()) {
      sql.append(" WHERE created_at LIKE ?");
      args.add(safeDate(date) + "%");
    }
    sql.append(" ORDER BY created_at DESC LIMIT ?");
    args.add(bounded(limit, 200));
    return jdbc.query(sql.toString(), mapper, args.toArray());
  }

  public List<SceneSummary> summarize(String date) {
    StringBuilder sql = new StringBuilder("""
        SELECT scene, status, COUNT(*) total_count
        FROM llm_call_logs
        """);
    List<Object> args = new ArrayList<>();
    if (!safeDate(date).isBlank()) {
      sql.append(" WHERE created_at LIKE ?");
      args.add(safeDate(date) + "%");
    }
    sql.append(" GROUP BY scene, status ORDER BY scene, status");
    return jdbc.query(sql.toString(), (rs, rowNum) -> new SceneSummary(
        rs.getString("scene"),
        rs.getString("status"),
        rs.getInt("total_count")), args.toArray());
  }

  private String text(String value) {
    return value == null ? "" : value.trim();
  }

  private String preview(String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.length() <= PREVIEW_LIMIT ? trimmed : trimmed.substring(0, PREVIEW_LIMIT);
  }

  private String safeDate(String value) {
    if (value == null || value.isBlank()) {
      return LocalDate.now().toString();
    }
    return value.trim();
  }

  private int bounded(int value, int max) {
    return Math.max(1, Math.min(value, max));
  }

  public record LogEntry(
      String scene,
      String model,
      String status,
      int requestChars,
      int responseChars,
      long durationMs,
      String requestPreview,
      String responsePreview,
      String errorMessage) {
  }

  public record LlmCallLog(
      String id,
      String scene,
      String model,
      String status,
      int requestChars,
      int responseChars,
      long durationMs,
      String requestPreview,
      String responsePreview,
      String errorMessage,
      String createdAt) {
  }

  public record SceneSummary(String scene, String status, int totalCount) {
  }
}
