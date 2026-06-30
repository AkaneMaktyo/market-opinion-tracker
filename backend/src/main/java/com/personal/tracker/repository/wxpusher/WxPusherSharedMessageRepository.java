package com.personal.tracker.repository.wxpusher;

import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.service.wxpusher.WxPusherClient.IncomingMessage;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class WxPusherSharedMessageRepository {
  private final JdbcTemplate jdbc;
  private final RowMapper<IncomingMessage> mapper = (rs, rowNum) -> new IncomingMessage(
      rs.getString("channel"),
      rs.getString("message_key"),
      bloggerName(rs.getString("source_name"), rs.getString("title")),
      rs.getString("title"),
      rs.getString("summary"),
      rs.getString("detail_url"),
      rs.getString("source_url"),
      rs.getString("message_time"),
      rs.getString("message_key"),
      rs.getString("raw_payload_json"),
      sortValue(rs.getString("message_time")));

  public WxPusherSharedMessageRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<IncomingMessage> listPending(String consumerName, int limit) {
    return jdbc.query("""
        SELECT m.* FROM wxpusher_raw_messages m
        LEFT JOIN wxpusher_consumer_state s
          ON s.consumer_name = ? AND s.message_key = m.message_key
        WHERE s.message_key IS NULL OR s.status NOT IN ('IMPORTED', 'IGNORED', 'SKIPPED')
        ORDER BY m.message_time ASC, m.updated_at ASC
        LIMIT ?
        """, mapper, consumerName, bounded(limit));
  }

  public List<IncomingMessage> listRecent(int limit) {
    return jdbc.query("""
        SELECT * FROM wxpusher_raw_messages
        ORDER BY message_time DESC, updated_at DESC
        LIMIT ?
        """, mapper, bounded(limit));
  }

  public void saveState(
      String consumerName,
      String messageKey,
      String status,
      String errorMessage,
      String derivedId) {
    String now = JdbcSupport.now();
    jdbc.update("""
        INSERT INTO wxpusher_consumer_state(
          id, consumer_name, message_key, status, error_message, derived_id, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          status = VALUES(status),
          error_message = VALUES(error_message),
          derived_id = VALUES(derived_id),
          updated_at = VALUES(updated_at)
        """,
        JdbcSupport.id(),
        consumerName,
        blank(messageKey),
        blank(status),
        blank(errorMessage),
        blank(derivedId),
        now,
        now);
  }

  private static int bounded(int limit) {
    return Math.max(1, Math.min(limit, 1000));
  }

  private static long sortValue(String messageTime) {
    try {
      return Instant.parse(messageTime).toEpochMilli();
    } catch (Exception error) {
      return 0L;
    }
  }

  private static String bloggerName(String sourceName, String title) {
    if (sourceName != null && !sourceName.isBlank()) {
      return sourceName.trim();
    }
    if (title != null && title.startsWith("您订阅的【") && title.endsWith("】有新的消息")) {
      return title.substring(5, title.length() - 6);
    }
    return "WxPusher";
  }

  private static String blank(String value) {
    return value == null ? "" : value.trim();
  }
}
