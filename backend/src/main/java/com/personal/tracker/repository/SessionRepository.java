package com.personal.tracker.repository;

import com.personal.tracker.domain.LiveSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SessionRepository {
  private final JdbcTemplate jdbc;
  private final RowMapper<LiveSession> mapper = (rs, rowNum) -> new LiveSession(
      rs.getString("id"),
      rs.getString("kol_id"),
      rs.getString("session_date"),
      rs.getString("title"),
      rs.getString("source"),
      rs.getString("raw_text"),
      rs.getString("created_at"));

  public SessionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public LiveSession create(
      String kolId,
      String sessionDate,
      String title,
      String source,
      String rawText) {
    LiveSession item = new LiveSession(
        JdbcSupport.id(),
        blank(kolId, KolRepository.DEFAULT_ID),
        blank(sessionDate, LocalDate.now().toString()),
        blank(title, "美股直播"),
        source,
        blank(rawText, ""),
        JdbcSupport.now());
    jdbc.update("""
        INSERT INTO live_sessions(id, kol_id, session_date, title, source, raw_text, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """, item.id(), item.kolId(), item.sessionDate(), item.title(), item.source(),
        item.rawText(), item.createdAt());
    return item;
  }

  public List<LiveSession> findRecent(String kolId, int limit) {
    return jdbc.query("""
        SELECT * FROM live_sessions
        WHERE kol_id = ?
        ORDER BY session_date DESC, created_at DESC
        LIMIT ?
        """, mapper, blank(kolId, KolRepository.DEFAULT_ID), Math.max(1, Math.min(limit, 100)));
  }

  public Optional<LiveSession> findById(String id) {
    List<LiveSession> rows = jdbc.query(
        "SELECT * FROM live_sessions WHERE id = ?",
        mapper,
        id);
    return rows.stream().findFirst();
  }

  private static String blank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
