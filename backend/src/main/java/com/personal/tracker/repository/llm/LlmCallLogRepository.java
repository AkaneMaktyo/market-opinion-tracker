package com.personal.tracker.repository.llm;

import com.personal.tracker.repository.JdbcSupport;
import jakarta.annotation.PostConstruct;
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
  private volatile boolean schemaReady;
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
      rs.getString("message_id"),
      rs.getInt("http_status"),
      rs.getInt("prompt_tokens"),
      rs.getInt("completion_tokens"),
      rs.getInt("total_tokens"),
      rs.getString("created_at"));

  public LlmCallLogRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void initialize() {
    ensureSchema();
  }

  public void create(LogEntry entry) {
    ensureSchema();
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

  public String beginAudit(String messageId, String scene, String model, String requestBody) {
    ensureSchema();
    String id = JdbcSupport.id();
    String body = requestBody == null ? "" : requestBody;
    jdbc.update("""
        INSERT INTO llm_call_logs(
          id, scene, model, status, request_chars, response_chars, duration_ms,
          request_preview, response_preview, error_message, created_at,
          message_id, request_body, response_body, http_status,
          provider_request_id, prompt_tokens, completion_tokens, total_tokens
        ) VALUES (?, ?, ?, 'PENDING', ?, 0, 0, ?, '', '', ?, ?, ?, '', 0, '', 0, 0, 0)
        """,
        id, text(scene), text(model), body.length(), preview(body), JdbcSupport.now(),
        text(messageId), body);
    return id;
  }

  public void completeAudit(String id, AuditCompletion completion) {
    ensureSchema();
    String response = completion.responseBody() == null ? "" : completion.responseBody();
    int updated = jdbc.update("""
        UPDATE llm_call_logs
        SET status = ?, response_chars = ?, duration_ms = ?, response_preview = ?,
            error_message = ?, response_body = ?, http_status = ?, provider_request_id = ?,
            prompt_tokens = ?, completion_tokens = ?, total_tokens = ?
        WHERE id = ?
        """,
        text(completion.status()), response.length(), Math.max(0L, completion.durationMs()),
        preview(response), text(completion.errorMessage()), response,
        Math.max(0, completion.httpStatus()), text(completion.providerRequestId()),
        Math.max(0, completion.promptTokens()), Math.max(0, completion.completionTokens()),
        Math.max(0, completion.totalTokens()), id);
    if (updated != 1) {
      throw new IllegalStateException("LLM 审计记录更新失败: " + id);
    }
  }

  public AuditDetail detail(String id) {
    ensureSchema();
    return jdbc.query("""
        SELECT id, scene, model, status, message_id, request_body, response_body,
               http_status, provider_request_id, prompt_tokens, completion_tokens,
               total_tokens, duration_ms, error_message, created_at
        FROM llm_call_logs WHERE id = ?
        """, (rs, rowNum) -> new AuditDetail(
        rs.getString("id"), rs.getString("scene"), rs.getString("model"),
        rs.getString("status"), rs.getString("message_id"), rs.getString("request_body"),
        rs.getString("response_body"), rs.getInt("http_status"),
        rs.getString("provider_request_id"), rs.getInt("prompt_tokens"),
        rs.getInt("completion_tokens"), rs.getInt("total_tokens"),
        rs.getLong("duration_ms"), rs.getString("error_message"),
        rs.getString("created_at")), id).stream().findFirst()
        .orElseThrow(() -> new IllegalArgumentException("LLM 调用记录不存在"));
  }

  public List<LlmCallLog> list(String date, int limit) {
    ensureSchema();
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
    ensureSchema();
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

  private void ensureSchema() {
    if (schemaReady) return;
    synchronized (this) {
      if (schemaReady) return;
      jdbc.execute("""
        CREATE TABLE IF NOT EXISTS llm_call_logs (
          id VARCHAR(64) PRIMARY KEY,
          scene VARCHAR(64) NOT NULL,
          model VARCHAR(120) NOT NULL,
          status VARCHAR(32) NOT NULL,
          request_chars INT NOT NULL,
          response_chars INT NOT NULL,
          duration_ms BIGINT NOT NULL,
          request_preview TEXT,
          response_preview TEXT,
          error_message TEXT,
          created_at VARCHAR(64) NOT NULL,
          INDEX idx_llm_call_scene(scene, created_at),
          INDEX idx_llm_call_status(status, created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
          """);
      ensureColumn("message_id", "VARCHAR(64)");
      ensureColumn("request_body", "LONGTEXT");
      ensureColumn("response_body", "LONGTEXT");
      ensureColumn("http_status", "INT");
      ensureColumn("provider_request_id", "VARCHAR(255)");
      ensureColumn("prompt_tokens", "INT NOT NULL DEFAULT 0");
      ensureColumn("completion_tokens", "INT NOT NULL DEFAULT 0");
      ensureColumn("total_tokens", "INT NOT NULL DEFAULT 0");
      schemaReady = true;
    }
  }

  private void ensureColumn(String column, String definition) {
    Integer count = jdbc.queryForObject("""
        SELECT COUNT(*) FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'llm_call_logs' AND column_name = ?
        """, Integer.class, column);
    if (count == null || count == 0) {
      jdbc.execute("ALTER TABLE llm_call_logs ADD COLUMN " + column + " " + definition);
    }
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
      String messageId,
      int httpStatus,
      int promptTokens,
      int completionTokens,
      int totalTokens,
      String createdAt) {
  }

  public record AuditCompletion(
      String status,
      String responseBody,
      String errorMessage,
      int httpStatus,
      String providerRequestId,
      int promptTokens,
      int completionTokens,
      int totalTokens,
      long durationMs) {
  }

  public record AuditDetail(
      String id,
      String scene,
      String model,
      String status,
      String messageId,
      String requestBody,
      String responseBody,
      int httpStatus,
      String providerRequestId,
      int promptTokens,
      int completionTokens,
      int totalTokens,
      long durationMs,
      String errorMessage,
      String createdAt) {
  }

  public record SceneSummary(String scene, String status, int totalCount) {
  }
}
