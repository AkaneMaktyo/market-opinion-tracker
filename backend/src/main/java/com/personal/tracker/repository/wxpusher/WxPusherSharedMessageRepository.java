package com.personal.tracker.repository.wxpusher;

import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.service.wxpusher.WxPusherClient.IncomingMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class WxPusherSharedMessageRepository {
  private static final String RECENT_FEED_SELECT = """
      SELECT r.id, r.message_key, r.source_name, r.title, r.summary,
             r.detail_url, r.source_url, r.message_time,
             COALESCE(p.blogger_name, '') processed_blogger_name,
             COALESCE(p.kol_id, '') kol_id,
             COALESCE(p.detail_text, '') detail_text,
             COALESCE(p.status, 'RECEIVED') status,
             COALESCE(a.status, 'NOT_STARTED') recognition_status,
             COALESCE(a.id, '') recognition_id,
             CASE WHEN JSON_VALID(a.candidates_json)
                  THEN JSON_LENGTH(a.candidates_json) ELSE 0 END recognition_candidate_count
      FROM wxpusher_raw_messages r
      LEFT JOIN wxpusher_messages p ON p.message_key = r.message_key
      LEFT JOIN message_price_alert_recognitions a ON a.message_id = r.id
      """;
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
  private final RowMapper<RecentMessage> recentMapper = (rs, rowNum) -> new RecentMessage(
      rs.getString("id"),
      rs.getString("message_key"),
      rs.getString("source_name"),
      rs.getString("processed_blogger_name"),
      rs.getString("kol_id"),
      rs.getString("title"),
      rs.getString("summary"),
      rs.getString("detail_url"),
      rs.getString("source_url"),
      rs.getString("message_time"),
      rs.getString("detail_text"),
      rs.getString("status"),
      rs.getString("recognition_status"),
      rs.getString("recognition_id"),
      rs.getInt("recognition_candidate_count"));

  public WxPusherSharedMessageRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<IncomingMessage> listPending(String consumerName, int limit) {
    return jdbc.query("""
        SELECT m.* FROM wxpusher_raw_messages m
        LEFT JOIN wxpusher_consumer_state s
          ON s.consumer_name = ? AND s.message_key = m.message_key
        WHERE s.message_key IS NULL OR s.status = 'PROCESSING'
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

  public List<RecentMessage> listRecentFeed(int limit) {
    return jdbc.query(RECENT_FEED_SELECT + """
        WHERE r.source_name <> 'WxPusher官方-极简推送'
        ORDER BY r.message_time DESC, r.updated_at DESC
        LIMIT ?
        """, recentMapper, bounded(limit));
  }

  public Optional<RecentMessage> findRecentFeedById(String id) {
    return jdbc.query(RECENT_FEED_SELECT + " WHERE r.id = ?", recentMapper, id)
        .stream()
        .findFirst();
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

  public record RecentMessage(
      String id,
      String messageKey,
      String sourceName,
      String processedBloggerName,
      String kolId,
      String title,
      String summary,
      String detailUrl,
      String sourceUrl,
      String messageTime,
      String detailText,
      String status,
      String recognitionStatus,
      String recognitionId,
      int recognitionCandidateCount) {
  }
}
