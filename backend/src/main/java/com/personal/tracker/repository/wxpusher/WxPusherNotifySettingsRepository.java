package com.personal.tracker.repository.wxpusher;

import com.personal.tracker.repository.JdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class WxPusherNotifySettingsRepository {
  private static final String DEFAULT_ID = "default";
  private final JdbcTemplate jdbc;
  private final RowMapper<WxPusherNotifySettings> mapper = (rs, rowNum) -> new WxPusherNotifySettings(
      rs.getString("id"),
      rs.getString("spt"),
      rs.getString("app_token"),
      rs.getString("uids"),
      rs.getString("topic_ids"),
      rs.getString("created_at"),
      rs.getString("updated_at"));

  public WxPusherNotifySettingsRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public WxPusherNotifySettings get() {
    ensureSchema();
    ensureDefault();
    return jdbc.query("SELECT * FROM wxpusher_notify_settings WHERE id = ?", mapper, DEFAULT_ID)
        .stream()
        .findFirst()
        .orElseThrow();
  }

  public WxPusherNotifySettings update(UpdateCommand command) {
    ensureSchema();
    ensureDefault();
    String now = JdbcSupport.now();
    jdbc.update("""
        UPDATE wxpusher_notify_settings
        SET spt = ?, app_token = ?, uids = ?, topic_ids = ?, updated_at = ?
        WHERE id = ?
        """,
        blank(command.spt()),
        blank(command.appToken()),
        blank(command.uids()),
        blank(command.topicIds()),
        now,
        DEFAULT_ID);
    return get();
  }

  private void ensureSchema() {
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS wxpusher_notify_settings (
          id VARCHAR(64) PRIMARY KEY,
          spt VARCHAR(255) NOT NULL,
          app_token VARCHAR(255) NOT NULL,
          uids TEXT NOT NULL,
          topic_ids VARCHAR(255) NOT NULL,
          created_at VARCHAR(64) NOT NULL,
          updated_at VARCHAR(64) NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """);
  }

  private void ensureDefault() {
    String now = JdbcSupport.now();
    jdbc.update("""
        INSERT IGNORE INTO wxpusher_notify_settings(
          id, spt, app_token, uids, topic_ids, created_at, updated_at
        ) VALUES (?, '', '', '', '', ?, ?)
        """, DEFAULT_ID, now, now);
  }

  private static String blank(String value) {
    return value == null ? "" : value.trim();
  }

  public record UpdateCommand(
      String spt,
      String appToken,
      String uids,
      String topicIds) {
  }

  public record WxPusherNotifySettings(
      String id,
      String spt,
      String appToken,
      String uids,
      String topicIds,
      String createdAt,
      String updatedAt) {
  }
}
