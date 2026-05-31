package com.personal.tracker.repository.wxpusher;

import com.personal.tracker.repository.JdbcSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class WxPusherSettingsRepository {
  private static final String DEFAULT_ID = "default";
  private final JdbcTemplate jdbc;
  private final RowMapper<WxPusherSettings> mapper = (rs, rowNum) -> new WxPusherSettings(
      rs.getString("id"),
      rs.getString("device_token"),
      rs.getString("push_token"),
      rs.getString("device_uuid"),
      rs.getString("platform"),
      rs.getString("version"),
      rs.getInt("poll_interval_seconds"),
      rs.getBoolean("enable_polling"),
      rs.getBoolean("enable_websocket"),
      rs.getString("last_heartbeat_at"),
      rs.getString("last_error"),
      rs.getString("created_at"),
      rs.getString("updated_at"));

  public WxPusherSettingsRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public WxPusherSettings get() {
    ensureDefault();
    return jdbc.query("SELECT * FROM wxpusher_settings WHERE id = ?", mapper, DEFAULT_ID)
        .stream()
        .findFirst()
        .orElseThrow();
  }

  public WxPusherSettings update(UpdateCommand command) {
    ensureDefault();
    String now = JdbcSupport.now();
    jdbc.update("""
        UPDATE wxpusher_settings
        SET device_token = ?, push_token = ?, device_uuid = ?, platform = ?, version = ?,
            poll_interval_seconds = ?, enable_polling = ?, enable_websocket = ?,
            updated_at = ?
        WHERE id = ?
        """,
        blank(command.deviceToken()),
        blank(command.pushToken()),
        blank(command.deviceUuid()),
        value(command.platform(), "Chrome-Windows"),
        value(command.version(), "1.1.1"),
        Math.max(30, command.pollIntervalSeconds()),
        command.enablePolling(),
        command.enableWebsocket(),
        now,
        DEFAULT_ID);
    return get();
  }

  public void updateRuntime(String heartbeatAt, String error) {
    ensureDefault();
    jdbc.update("""
        UPDATE wxpusher_settings
        SET last_heartbeat_at = ?, last_error = ?, updated_at = ?
        WHERE id = ?
        """, blank(heartbeatAt), blank(error), JdbcSupport.now(), DEFAULT_ID);
  }

  private void ensureDefault() {
    String now = JdbcSupport.now();
    jdbc.update("""
        INSERT IGNORE INTO wxpusher_settings(
          id, device_token, push_token, device_uuid, platform, version,
          poll_interval_seconds, enable_polling, enable_websocket,
          last_heartbeat_at, last_error, created_at, updated_at
        ) VALUES (?, '', '', '', 'Chrome-Windows', '1.1.1', 60, false, false, '', '', ?, ?)
        """, DEFAULT_ID, now, now);
  }

  private static String value(String input, String fallback) {
    return input == null || input.isBlank() ? fallback : input.trim();
  }

  private static String blank(String input) {
    return input == null ? "" : input.trim();
  }

  public record UpdateCommand(
      String deviceToken,
      String pushToken,
      String deviceUuid,
      String platform,
      String version,
      int pollIntervalSeconds,
      boolean enablePolling,
      boolean enableWebsocket) {
  }

  public record WxPusherSettings(
      String id,
      String deviceToken,
      String pushToken,
      String deviceUuid,
      String platform,
      String version,
      int pollIntervalSeconds,
      boolean enablePolling,
      boolean enableWebsocket,
      String lastHeartbeatAt,
      String lastError,
      String createdAt,
      String updatedAt) {
    public boolean pollingReady() {
      return enablePolling && deviceToken != null && !deviceToken.isBlank();
    }

    public boolean websocketReady() {
      return enableWebsocket && pushToken != null && !pushToken.isBlank();
    }

    public String configurationIssue() {
      String polling = pollingIssue();
      String websocket = websocketIssue();
      if (!polling.isBlank() && !websocket.isBlank()) {
        return polling + "；" + websocket;
      }
      return polling.isBlank() ? websocket : polling;
    }

    public String pollingIssue() {
      if (!enablePolling) {
        return "";
      }
      return pollingReady() ? "" : "WXPUSHER_DEVICE_TOKEN 未配置";
    }

    public String websocketIssue() {
      if (!enableWebsocket) {
        return "";
      }
      return websocketReady() ? "" : "WXPUSHER_PUSH_TOKEN 未配置";
    }
  }
}
