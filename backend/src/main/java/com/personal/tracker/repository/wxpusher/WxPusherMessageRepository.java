package com.personal.tracker.repository.wxpusher;

import com.personal.tracker.repository.JdbcSupport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class WxPusherMessageRepository {
  private final JdbcTemplate jdbc;
  private final RowMapper<WxPusherMessage> mapper = (rs, rowNum) -> new WxPusherMessage(
      rs.getString("id"),
      rs.getString("message_key"),
      rs.getString("kol_id"),
      rs.getString("blogger_name"),
      rs.getString("title"),
      rs.getString("summary"),
      rs.getString("detail_url"),
      rs.getString("source_url"),
      rs.getString("message_time"),
      rs.getString("raw_payload_json"),
      rs.getString("detail_text"),
      rs.getString("llm_output_json"),
      rs.getString("status"),
      rs.getString("error_message"),
      rs.getString("session_id"),
      rs.getString("created_at"),
      rs.getString("updated_at"));

  public WxPusherMessageRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public SaveResult createPending(PendingMessage command) {
    String id = JdbcSupport.id();
    String now = JdbcSupport.now();
    try {
      jdbc.update("""
          INSERT INTO wxpusher_messages(
            id, message_key, kol_id, blogger_name, title, summary, detail_url, source_url,
            message_time, raw_payload_json, detail_text, llm_output_json, status,
            error_message, session_id, created_at, updated_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '', '', 'PENDING', '', '', ?, ?)
          """,
          id,
          command.messageKey(),
          command.kolId(),
          command.bloggerName(),
          blank(command.title()),
          blank(command.summary()),
          blank(command.detailUrl()),
          blank(command.sourceUrl()),
          blank(command.messageTime()),
          blank(command.rawPayloadJson()),
          now,
          now);
      return new SaveResult(findById(id).orElseThrow(), true);
    } catch (DuplicateKeyException error) {
      return new SaveResult(findByMessageKey(command.messageKey()).orElseThrow(), false);
    }
  }

  public Optional<WxPusherMessage> findById(String id) {
    return jdbc.query("SELECT * FROM wxpusher_messages WHERE id = ?", mapper, id)
        .stream()
        .findFirst();
  }

  public Optional<WxPusherMessage> findByMessageKey(String messageKey) {
    return jdbc.query("SELECT * FROM wxpusher_messages WHERE message_key = ?", mapper, messageKey)
        .stream()
        .findFirst();
  }

  public void markProcessing(String id) {
    updateState(id, "PROCESSING", "", null, null, null);
  }

  public void markFailed(String id, String detailText, String llmOutputJson, String errorMessage) {
    updateState(id, "FAILED", errorMessage, detailText, llmOutputJson, null);
  }

  public void markSkipped(String id, String detailText, String errorMessage) {
    updateState(id, "SKIPPED", errorMessage, detailText, null, null);
  }

  public void markImported(String id, String detailText, String llmOutputJson, String sessionId) {
    updateState(id, "IMPORTED", "", detailText, llmOutputJson, sessionId);
  }

  public void attachSession(String id, String sessionId) {
    jdbc.update("""
        UPDATE wxpusher_messages
        SET session_id = ?, updated_at = ?
        WHERE id = ?
        """, blank(sessionId), JdbcSupport.now(), id);
  }

  public void reassign(String id, String kolId, String bloggerName) {
    jdbc.update("""
        UPDATE wxpusher_messages
        SET kol_id = ?, blogger_name = ?, updated_at = ?
        WHERE id = ?
        """, blank(kolId), blank(bloggerName), JdbcSupport.now(), id);
  }

  public List<WxPusherMessage> list(String status, String kolId, int limit) {
    StringBuilder sql = new StringBuilder("SELECT * FROM wxpusher_messages WHERE 1 = 1");
    List<Object> args = new ArrayList<>();
    if (status != null && !status.isBlank()) {
      sql.append(" AND status = ?");
      args.add(status.trim().toUpperCase());
    }
    if (kolId != null && !kolId.isBlank()) {
      sql.append(" AND kol_id = ?");
      args.add(kolId.trim());
    }
    sql.append(" ORDER BY updated_at DESC, created_at DESC LIMIT ?");
    args.add(Math.max(1, Math.min(limit, 100)));
    return jdbc.query(sql.toString(), mapper, args.toArray());
  }

  public List<WxPusherMessage> listForRetry(String status, String kolId, int limit) {
    StringBuilder sql = new StringBuilder("SELECT * FROM wxpusher_messages WHERE 1 = 1");
    List<Object> args = new ArrayList<>();
    if (status != null && !status.isBlank()) {
      sql.append(" AND status = ?");
      args.add(status.trim().toUpperCase());
    }
    if (kolId != null && !kolId.isBlank()) {
      sql.append(" AND kol_id = ?");
      args.add(kolId.trim());
    }
    sql.append(" ORDER BY updated_at ASC, created_at ASC LIMIT ?");
    args.add(Math.max(1, Math.min(limit, 100)));
    return jdbc.query(sql.toString(), mapper, args.toArray());
  }

  public List<WxPusherMessage> listMissingSessions(int limit) {
    return jdbc.query("""
        SELECT * FROM wxpusher_messages
        WHERE session_id IS NULL OR session_id = ''
        ORDER BY updated_at DESC, created_at DESC
        LIMIT ?
        """, mapper, Math.max(1, Math.min(limit, 200)));
  }

  public List<WxPusherMessage> findByKolSince(String kolId, String sinceDate, int limit) {
    return jdbc.query("""
        SELECT * FROM wxpusher_messages
        WHERE kol_id = ? AND message_time >= ? AND status <> 'IMPORTED'
        ORDER BY message_time DESC, updated_at DESC
        LIMIT ?
        """, mapper, kolId, sinceDate, Math.max(1, Math.min(limit, 1000)));
  }

  public List<WxPusherMessage> findByKolSinceContaining(
      String kolId, String sinceDate, String symbol, String name, int limit) {
    String symbolPattern = "%" + symbol.trim() + "%";
    String namePattern = name == null || name.trim().length() < 3 ? symbolPattern : "%" + name.trim() + "%";
    return jdbc.query("""
        SELECT * FROM wxpusher_messages
        WHERE kol_id = ? AND message_time >= ? AND status <> 'IMPORTED'
          AND (title LIKE ? OR summary LIKE ? OR detail_text LIKE ?
               OR title LIKE ? OR summary LIKE ? OR detail_text LIKE ?)
        ORDER BY message_time DESC, updated_at DESC
        LIMIT ?
        """, mapper,
        kolId,
        sinceDate,
        symbolPattern,
        symbolPattern,
        symbolPattern,
        namePattern,
        namePattern,
        namePattern,
        Math.max(1, Math.min(limit, 1000)));
  }

  public Map<String, MessageSummary> summaryByKolIds(List<String> kolIds) {
    List<String> safeKolIds = kolIds == null ? List.of() : kolIds.stream()
        .filter(item -> item != null && !item.isBlank())
        .map(String::trim)
        .distinct()
        .toList();
    if (safeKolIds.isEmpty()) {
      return Map.of();
    }
    String placeholders = safeKolIds.stream().map(item -> "?").collect(Collectors.joining(", "));
    return jdbc.query("""
        SELECT kol_id,
               COUNT(*) total_count,
               SUM(CASE WHEN status = 'IMPORTED' THEN 1 ELSE 0 END) imported_count,
               SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) failed_count,
               MAX(message_time) latest_message_time
        FROM wxpusher_messages
        WHERE kol_id IN (%s)
        GROUP BY kol_id
        """.formatted(placeholders), rs -> {
      Map<String, MessageSummary> result = new LinkedHashMap<>();
      while (rs.next()) {
        result.put(rs.getString("kol_id"), new MessageSummary(
            rs.getString("kol_id"),
            rs.getInt("total_count"),
            rs.getInt("imported_count"),
            rs.getInt("failed_count"),
            rs.getString("latest_message_time")));
      }
      return result;
    }, safeKolIds.toArray());
  }

  public List<String> recentImportedSymbols(String kolId, String beforeMessageTime, int limit) {
    if (kolId == null || kolId.isBlank()) {
      return List.of();
    }
    String time = beforeMessageTime == null || beforeMessageTime.isBlank() ? "9999-12-31" : beforeMessageTime;
    return jdbc.queryForList("""
        SELECT i.symbol
        FROM wxpusher_messages m
        JOIN opinions o ON o.session_id = m.session_id
        JOIN instruments i ON i.id = o.instrument_id
        WHERE m.kol_id = ? AND m.status = 'IMPORTED' AND m.message_time <= ?
        GROUP BY i.symbol
        ORDER BY MAX(m.message_time) DESC, COUNT(*) DESC
        LIMIT ?
        """, String.class, kolId.trim(), time, Math.max(1, Math.min(limit, 3)));
  }

  private void updateState(
      String id,
      String status,
      String errorMessage,
      String detailText,
      String llmOutputJson,
      String sessionId) {
    jdbc.update("""
        UPDATE wxpusher_messages
        SET status = ?, error_message = ?, detail_text = COALESCE(?, detail_text),
            llm_output_json = COALESCE(?, llm_output_json), session_id = COALESCE(?, session_id),
            updated_at = ?
        WHERE id = ?
        """,
        status,
        blank(errorMessage),
        detailText,
        llmOutputJson,
        sessionId,
        JdbcSupport.now(),
        id);
  }

  private static String blank(String input) {
    return input == null ? "" : input.trim();
  }

  public record PendingMessage(
      String messageKey,
      String kolId,
      String bloggerName,
      String title,
      String summary,
      String detailUrl,
      String sourceUrl,
      String messageTime,
      String rawPayloadJson) {
  }

  public record SaveResult(WxPusherMessage message, boolean created) {
  }

  public record WxPusherMessage(
      String id,
      String messageKey,
      String kolId,
      String bloggerName,
      String title,
      String summary,
      String detailUrl,
      String sourceUrl,
      String messageTime,
      String rawPayloadJson,
      String detailText,
      String llmOutputJson,
      String status,
      String errorMessage,
      String sessionId,
      String createdAt,
      String updatedAt) {
  }

  public record MessageSummary(
      String kolId,
      int totalCount,
      int importedCount,
      int failedCount,
      String latestMessageTime) {
  }
}
