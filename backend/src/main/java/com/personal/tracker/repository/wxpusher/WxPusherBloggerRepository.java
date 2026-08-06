package com.personal.tracker.repository.wxpusher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.repository.JdbcSupport;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class WxPusherBloggerRepository {
  private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
  };
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  private final RowMapper<WxPusherBlogger> rowMapper = (rs, rowNum) -> new WxPusherBlogger(
      rs.getString("id"),
      rs.getString("kol_id"),
      rs.getString("blogger_name"),
      readAliases(rs.getString("aliases_json")),
      rs.getBoolean("enabled"),
      rs.getBoolean("notify_enabled"),
      rs.getString("history_seed_mode"),
      rs.getString("seed_completed_at"),
      rs.getString("created_at"),
      rs.getString("updated_at"));

  public WxPusherBloggerRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public List<WxPusherBlogger> list() {
    return jdbc.query("SELECT * FROM wxpusher_bloggers ORDER BY updated_at DESC, blogger_name", rowMapper);
  }

  public List<WxPusherBlogger> enabledPendingSeed() {
    return jdbc.query("""
        SELECT * FROM wxpusher_bloggers
        WHERE enabled = true AND seed_completed_at IS NULL
        ORDER BY created_at
        """, rowMapper);
  }

  public List<WxPusherBlogger> enabled() {
    return jdbc.query("""
        SELECT * FROM wxpusher_bloggers
        WHERE enabled = true
        ORDER BY blogger_name
        """, rowMapper);
  }

  public WxPusherBlogger create(SaveCommand command) {
    String now = JdbcSupport.now();
    String id = JdbcSupport.id();
    jdbc.update("""
        INSERT INTO wxpusher_bloggers(
          id, kol_id, blogger_name, aliases_json, enabled, notify_enabled,
          history_seed_mode, seed_completed_at, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        id,
        command.kolId(),
        command.bloggerName().trim(),
        writeAliases(command.aliases()),
        command.enabled(),
        command.notifyEnabled(),
        value(command.historySeedMode(), "LAST_30"),
        command.seedCompletedAt(),
        now,
        now);
    return findById(id);
  }

  public WxPusherBlogger update(SaveCommand command) {
    String now = JdbcSupport.now();
    jdbc.update("""
        UPDATE wxpusher_bloggers
        SET kol_id = ?, blogger_name = ?, aliases_json = ?, enabled = ?,
            notify_enabled = ?, history_seed_mode = ?, seed_completed_at = ?, updated_at = ?
        WHERE id = ?
        """,
        command.kolId(),
        command.bloggerName().trim(),
        writeAliases(command.aliases()),
        command.enabled(),
        command.notifyEnabled(),
        value(command.historySeedMode(), "LAST_30"),
        command.seedCompletedAt(),
        now,
        command.id());
    return findById(command.id());
  }

  public void markSeedCompleted(String id) {
    jdbc.update("""
        UPDATE wxpusher_bloggers
        SET seed_completed_at = ?, updated_at = ?
        WHERE id = ?
        """, JdbcSupport.now(), JdbcSupport.now(), id);
  }

  public WxPusherBlogger findById(String id) {
    return jdbc.query("SELECT * FROM wxpusher_bloggers WHERE id = ?", rowMapper, id)
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("博主配置不存在"));
  }

  private List<String> readAliases(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    try {
      return mapper.readValue(value, STRING_LIST);
    } catch (JsonProcessingException error) {
      return List.of();
    }
  }

  private String writeAliases(List<String> value) {
    try {
      return mapper.writeValueAsString(value == null ? List.of() : value.stream()
          .map(String::trim)
          .filter(item -> !item.isBlank())
          .distinct()
          .toList());
    } catch (JsonProcessingException error) {
      throw new IllegalArgumentException("别名列表无法保存: " + error.getMessage(), error);
    }
  }

  private static String value(String input, String fallback) {
    return input == null || input.isBlank() ? fallback : input.trim();
  }

  public record SaveCommand(
      String id,
      String kolId,
      String bloggerName,
      List<String> aliases,
      boolean enabled,
      boolean notifyEnabled,
      String historySeedMode,
      String seedCompletedAt) {
  }

  public record WxPusherBlogger(
      String id,
      String kolId,
      String bloggerName,
      List<String> aliases,
      boolean enabled,
      boolean notifyEnabled,
      String historySeedMode,
      String seedCompletedAt,
      String createdAt,
      String updatedAt) {
  }
}
