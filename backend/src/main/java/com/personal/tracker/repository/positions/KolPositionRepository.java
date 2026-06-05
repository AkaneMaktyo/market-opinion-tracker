package com.personal.tracker.repository.positions;

import com.personal.tracker.domain.KolPosition;
import com.personal.tracker.repository.JdbcSupport;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class KolPositionRepository {
  private final JdbcTemplate jdbc;
  private final RowMapper<KolPosition> mapper = (rs, rowNum) -> new KolPosition(
      rs.getString("id"),
      rs.getString("kol_id"),
      rs.getString("instrument_id"),
      rs.getString("symbol"),
      rs.getString("instrument_name"),
      rs.getString("status"),
      rs.getString("opened_at"),
      rs.getString("closed_at"),
      rs.getString("last_opinion_id"),
      rs.getString("last_action"),
      rs.getString("created_at"),
      rs.getString("updated_at"));

  public KolPositionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<KolPosition> list(String kolId, boolean includeClosed) {
    String statusFilter = includeClosed ? "" : " AND p.status = 'ACTIVE'";
    return jdbc.query("""
        SELECT p.*, i.symbol, i.name AS instrument_name
        FROM kol_positions p
        JOIN instruments i ON i.id = p.instrument_id
        WHERE p.kol_id = ?
        """ + statusFilter + """

        ORDER BY p.status, p.updated_at DESC, i.symbol
        """, mapper, kolId);
  }

  public KolPosition open(String kolId, String instrumentId, String opinionId, String action) {
    String now = JdbcSupport.now();
    jdbc.update("""
        INSERT INTO kol_positions(
          id, kol_id, instrument_id, status, opened_at, closed_at,
          last_opinion_id, last_action, created_at, updated_at
        ) VALUES (?, ?, ?, 'ACTIVE', ?, NULL, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          status = 'ACTIVE',
          opened_at = IF(kol_positions.status = 'ACTIVE' AND kol_positions.opened_at IS NOT NULL,
              kol_positions.opened_at,
              VALUES(opened_at)),
          closed_at = NULL,
          last_opinion_id = VALUES(last_opinion_id),
          last_action = VALUES(last_action),
          updated_at = VALUES(updated_at)
        """, JdbcSupport.id(), kolId, instrumentId, now, blank(opinionId),
        blank(action), now, now);
    return findByKolAndInstrument(kolId, instrumentId).orElseThrow();
  }

  public KolPosition close(String kolId, String instrumentId, String opinionId, String action) {
    String now = JdbcSupport.now();
    jdbc.update("""
        INSERT INTO kol_positions(
          id, kol_id, instrument_id, status, opened_at, closed_at,
          last_opinion_id, last_action, created_at, updated_at
        ) VALUES (?, ?, ?, 'CLOSED', NULL, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          status = 'CLOSED',
          closed_at = VALUES(closed_at),
          last_opinion_id = VALUES(last_opinion_id),
          last_action = VALUES(last_action),
          updated_at = VALUES(updated_at)
        """, JdbcSupport.id(), kolId, instrumentId, now, blank(opinionId),
        blank(action), now, now);
    return findByKolAndInstrument(kolId, instrumentId).orElseThrow();
  }

  public Optional<KolPosition> closeById(String id) {
    String now = JdbcSupport.now();
    jdbc.update("""
        UPDATE kol_positions
        SET status = 'CLOSED', closed_at = ?, last_action = 'MANUAL_CLOSE', updated_at = ?
        WHERE id = ?
        """, now, now, id);
    return findById(id);
  }

  public Optional<KolPosition> findById(String id) {
    return jdbc.query("""
        SELECT p.*, i.symbol, i.name AS instrument_name
        FROM kol_positions p
        JOIN instruments i ON i.id = p.instrument_id
        WHERE p.id = ?
        """, mapper, id).stream().findFirst();
  }

  private Optional<KolPosition> findByKolAndInstrument(String kolId, String instrumentId) {
    return jdbc.query("""
        SELECT p.*, i.symbol, i.name AS instrument_name
        FROM kol_positions p
        JOIN instruments i ON i.id = p.instrument_id
        WHERE p.kol_id = ? AND p.instrument_id = ?
        """, mapper, kolId, instrumentId).stream().findFirst();
  }

  private static String blank(String value) {
    return value == null ? "" : value.trim();
  }
}
