package com.personal.tracker.repository.positions;

import com.personal.tracker.domain.KolPosition;
import com.personal.tracker.domain.KolPositionTrade;
import com.personal.tracker.repository.JdbcSupport;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class KolPositionRepository {
  private final JdbcTemplate jdbc;
  private volatile boolean schemaReady;
  private final RowMapper<KolPosition> mapper = (rs, rowNum) -> new KolPosition(
      rs.getString("id"),
      rs.getString("kol_id"),
      rs.getString("instrument_id"),
      rs.getString("symbol"),
      rs.getString("instrument_name"),
      rs.getString("status"),
      rs.getString("direction"),
      rs.getBigDecimal("entry_price"),
      rs.getBigDecimal("exit_price"),
      rs.getString("exit_reason"),
      rs.getString("opened_at"),
      rs.getString("closed_at"),
      rs.getString("last_opinion_id"),
      rs.getString("last_action"),
      rs.getString("created_at"),
      rs.getString("updated_at"));

  public KolPositionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void ensureSchema() {
    if (schemaReady) {
      return;
    }
    synchronized (this) {
      if (schemaReady) {
        return;
      }
      jdbc.execute("""
          CREATE TABLE IF NOT EXISTS kol_position_trades (
            id VARCHAR(64) PRIMARY KEY,
            kol_id VARCHAR(64) NOT NULL,
            instrument_id VARCHAR(64) NOT NULL,
            symbol VARCHAR(64) NOT NULL,
            direction VARCHAR(16) NOT NULL,
            entry_price DECIMAL(24, 8),
            entry_at VARCHAR(64),
            entry_opinion_id VARCHAR(64),
            exit_price DECIMAL(24, 8),
            exit_at VARCHAR(64),
            exit_opinion_id VARCHAR(64),
            exit_reason VARCHAR(64),
            pnl_pct DECIMAL(12, 4),
            created_at VARCHAR(64) NOT NULL,
            updated_at VARCHAR(64) NOT NULL,
            INDEX idx_kol_position_trade_kol(kol_id, exit_at),
            INDEX idx_kol_position_trade_exit(kol_id, pnl_pct)
          ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
          """);
      ensurePositionColumn("direction", "ALTER TABLE kol_positions "
          + "ADD COLUMN direction VARCHAR(16) AFTER status");
      ensurePositionColumn("entry_price", "ALTER TABLE kol_positions "
          + "ADD COLUMN entry_price DECIMAL(24, 8) AFTER direction");
      ensurePositionColumn("exit_price", "ALTER TABLE kol_positions "
          + "ADD COLUMN exit_price DECIMAL(24, 8) AFTER entry_price");
      ensurePositionColumn("exit_reason", "ALTER TABLE kol_positions "
          + "ADD COLUMN exit_reason VARCHAR(64) AFTER exit_price");
      ensureTradeUniqueIndex();
      schemaReady = true;
    }
  }

  private void ensureTradeUniqueIndex() {
    Integer count = jdbc.queryForObject("""
        SELECT COUNT(*) FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'kol_position_trades'
          AND index_name = 'uq_kol_position_trade_entry'
        """, Integer.class);
    if (count == null || count == 0) {
      jdbc.update("""
          DELETE t1 FROM kol_position_trades t1
          JOIN kol_position_trades t2
            ON t1.entry_opinion_id = t2.entry_opinion_id AND t1.id > t2.id
          WHERE t1.entry_opinion_id IS NOT NULL AND t1.entry_opinion_id <> ''
          """);
      jdbc.execute("ALTER TABLE kol_position_trades "
          + "ADD UNIQUE KEY uq_kol_position_trade_entry(entry_opinion_id)");
    }
  }

  private void ensurePositionColumn(String column, String alterSql) {
    Integer count = jdbc.queryForObject("""
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'kol_positions'
          AND column_name = ?
        """, Integer.class, column);
    if (count == null || count == 0) {
      jdbc.execute(alterSql);
    }
  }

  public List<KolPosition> list(String kolId, boolean includeClosed) {
    ensureSchema();
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

  public List<KolPositionTrade> trades(String kolId, int limit) {
    ensureSchema();
    return jdbc.query("""
        SELECT t.*, i.symbol
        FROM kol_position_trades t
        JOIN instruments i ON i.id = t.instrument_id
        WHERE t.kol_id = ?
        ORDER BY COALESCE(t.exit_at, t.entry_at) DESC, t.created_at DESC
        LIMIT ?
        """, (rs, rowNum) -> new KolPositionTrade(
        rs.getString("id"),
        rs.getString("kol_id"),
        rs.getString("instrument_id"),
        rs.getString("symbol"),
        rs.getString("direction"),
        rs.getBigDecimal("entry_price"),
        rs.getString("entry_at"),
        rs.getString("entry_opinion_id"),
        rs.getBigDecimal("exit_price"),
        rs.getString("exit_at"),
        rs.getString("exit_opinion_id"),
        rs.getString("exit_reason"),
        rs.getBigDecimal("pnl_pct"),
        rs.getString("created_at")), kolId, Math.max(1, Math.min(limit, 500)));
  }

  public KolPosition open(
      String kolId,
      String instrumentId,
      String opinionId,
      String action,
      String direction,
      BigDecimal entryPrice) {
    ensureSchema();
    String now = JdbcSupport.now();
    jdbc.update("""
        INSERT INTO kol_positions(
          id, kol_id, instrument_id, status, direction, entry_price,
          exit_price, exit_reason, opened_at, closed_at,
          last_opinion_id, last_action, created_at, updated_at
        ) VALUES (?, ?, ?, 'ACTIVE', ?, ?, NULL, NULL, ?, NULL, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          status = 'ACTIVE',
          direction = IF(kol_positions.status = 'ACTIVE' AND kol_positions.direction IS NOT NULL,
              kol_positions.direction, VALUES(direction)),
          entry_price = IF(kol_positions.status = 'ACTIVE' AND kol_positions.entry_price IS NOT NULL,
              kol_positions.entry_price, VALUES(entry_price)),
          exit_price = NULL,
          exit_reason = NULL,
          opened_at = IF(kol_positions.status = 'ACTIVE' AND kol_positions.opened_at IS NOT NULL,
              kol_positions.opened_at, VALUES(opened_at)),
          closed_at = NULL,
          last_opinion_id = VALUES(last_opinion_id),
          last_action = VALUES(last_action),
          updated_at = VALUES(updated_at)
        """, JdbcSupport.id(), kolId, instrumentId, direction, entryPrice,
        now, blank(opinionId), blank(action), now, now);
    return findByKolAndInstrument(kolId, instrumentId).orElseThrow();
  }

  public KolPosition close(
      String kolId,
      String instrumentId,
      String opinionId,
      String action,
      BigDecimal exitPrice,
      String exitReason) {
    ensureSchema();
    String now = JdbcSupport.now();
    settleActive(kolId, instrumentId, opinionId, exitPrice, exitReason, now);
    jdbc.update("""
        INSERT INTO kol_positions(
          id, kol_id, instrument_id, status, direction, entry_price,
          exit_price, exit_reason, opened_at, closed_at,
          last_opinion_id, last_action, created_at, updated_at
        ) VALUES (?, ?, ?, 'CLOSED', NULL, NULL, ?, ?, NULL, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          status = 'CLOSED',
          exit_price = VALUES(exit_price),
          exit_reason = VALUES(exit_reason),
          closed_at = VALUES(closed_at),
          last_opinion_id = VALUES(last_opinion_id),
          last_action = VALUES(last_action),
          updated_at = VALUES(updated_at)
        """, JdbcSupport.id(), kolId, instrumentId, exitPrice, trim(exitReason),
        now, blank(opinionId), blank(action), now, now);
    return findByKolAndInstrument(kolId, instrumentId).orElseThrow();
  }

  public Optional<KolPosition> closeById(String id, BigDecimal exitPrice, String exitReason) {
    ensureSchema();
    String now = JdbcSupport.now();
    findById(id).ifPresent(position ->
        settle(position, position.lastOpinionId(), exitPrice, exitReason, now));
    jdbc.update("""
        UPDATE kol_positions
        SET status = 'CLOSED', exit_price = ?, exit_reason = ?,
            closed_at = ?, last_action = 'MANUAL_CLOSE', updated_at = ?
        WHERE id = ?
        """, exitPrice, trim(exitReason), now, now, id);
    return findById(id);
  }

  private void settleActive(
      String kolId,
      String instrumentId,
      String opinionId,
      BigDecimal exitPrice,
      String exitReason,
      String now) {
    findByKolAndInstrument(kolId, instrumentId)
        .filter(position -> "ACTIVE".equals(position.status()))
        .ifPresent(position -> settle(position, opinionId, exitPrice, exitReason, now));
  }

  private void settle(
      KolPosition position,
      String exitOpinionId,
      BigDecimal exitPrice,
      String exitReason,
      String now) {
    if (position.entryPrice() == null) {
      return;
    }
    upsertTrade(new KolPositionTrade(
        JdbcSupport.id(),
        position.kolId(),
        position.instrumentId(),
        position.symbol(),
        position.direction() == null ? "LONG" : position.direction(),
        position.entryPrice(),
        position.openedAt(),
        position.lastOpinionId(),
        exitPrice,
        now,
        exitOpinionId,
        trim(exitReason),
        KolPositionTrade.pnlPct(position.direction(), position.entryPrice(), exitPrice),
        now), now);
  }

  public TradeStats tradeStats(String kolId) {
    ensureSchema();
    return jdbc.queryForObject("""
        SELECT
          COUNT(*) AS total,
          COALESCE(SUM(pnl_pct IS NOT NULL), 0) AS settled,
          COALESCE(SUM(pnl_pct > 0), 0) AS wins,
          COALESCE(SUM(pnl_pct < 0), 0) AS losses,
          AVG(pnl_pct) AS avg_pnl,
          MAX(pnl_pct) AS best_pnl,
          MIN(pnl_pct) AS worst_pnl,
          SUM(pnl_pct) AS total_pnl
        FROM kol_position_trades
        WHERE kol_id = ?
        """, (rs, rowNum) -> new TradeStats(
        rs.getLong("total"),
        rs.getLong("settled"),
        rs.getLong("wins"),
        rs.getLong("losses"),
        rs.getBigDecimal("avg_pnl"),
        rs.getBigDecimal("best_pnl"),
        rs.getBigDecimal("worst_pnl"),
        rs.getBigDecimal("total_pnl")), kolId);
  }

  public int countActive(String kolId) {
    ensureSchema();
    Integer count = jdbc.queryForObject("""
        SELECT COUNT(*) FROM kol_positions
        WHERE kol_id = ? AND status = 'ACTIVE'
        """, Integer.class, kolId);
    return count == null ? 0 : count;
  }

  public record TradeStats(
      long total,
      long settled,
      long wins,
      long losses,
      BigDecimal avgPnlPct,
      BigDecimal bestPnlPct,
      BigDecimal worstPnlPct,
      BigDecimal totalPnlPct) {
  }

  public int deleteTrades(String kolId) {
    ensureSchema();
    return jdbc.update("DELETE FROM kol_position_trades WHERE kol_id = ?", kolId);
  }

  public int backfillActiveEntry(
      String kolId,
      String instrumentId,
      String direction,
      BigDecimal entryPrice,
      String entryOpinionId,
      String openedAt) {
    ensureSchema();
    return jdbc.update("""
        UPDATE kol_positions
        SET direction = ?, entry_price = ?, last_opinion_id = ?,
            opened_at = COALESCE(opened_at, ?), updated_at = ?
        WHERE kol_id = ? AND instrument_id = ? AND status = 'ACTIVE'
          AND (entry_price IS NULL OR direction IS NULL)
        """, direction, entryPrice, blank(entryOpinionId), openedAt,
        JdbcSupport.now(), kolId, instrumentId);
  }

  public void upsertTrade(KolPositionTrade trade, String updatedAt) {
    ensureSchema();
    jdbc.update("""
        INSERT INTO kol_position_trades(
          id, kol_id, instrument_id, symbol, direction,
          entry_price, entry_at, entry_opinion_id,
          exit_price, exit_at, exit_opinion_id, exit_reason,
          pnl_pct, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          exit_price = VALUES(exit_price),
          exit_at = VALUES(exit_at),
          exit_opinion_id = VALUES(exit_opinion_id),
          exit_reason = VALUES(exit_reason),
          pnl_pct = VALUES(pnl_pct),
          updated_at = VALUES(updated_at)
        """,
        JdbcSupport.id(), trade.kolId(), trade.instrumentId(), trade.symbol(),
        trade.direction(), trade.entryPrice(), trade.entryAt(),
        blankToNull(trade.entryOpinionId()),
        trade.exitPrice(), trade.exitAt(),
        blankToNull(trade.exitOpinionId()), trim(trade.exitReason()),
        trade.pnlPct(), trade.createdAt(), updatedAt);
  }

  public int deleteAutoActiveNotIn(String kolId, List<String> keepInstrumentIds) {
    ensureSchema();
    String inClause = keepInstrumentIds.isEmpty()
        ? "''"
        : keepInstrumentIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
    List<Object> args = new java.util.ArrayList<>();
    args.add(kolId);
    args.addAll(keepInstrumentIds);
    return jdbc.update("""
        DELETE FROM kol_positions
        WHERE kol_id = ? AND status = 'ACTIVE'
          AND last_opinion_id IS NOT NULL AND last_opinion_id <> ''
          AND instrument_id NOT IN (
        """ + inClause + ")", args.toArray());
  }

  public void reopenForRebuild(
      String kolId,
      String instrumentId,
      String direction,
      BigDecimal entryPrice,
      String opinionId,
      String openedAt,
      String action) {
    ensureSchema();
    String now = JdbcSupport.now();
    jdbc.update("""
        INSERT INTO kol_positions(
          id, kol_id, instrument_id, status, direction, entry_price,
          exit_price, exit_reason, opened_at, closed_at,
          last_opinion_id, last_action, created_at, updated_at
        ) VALUES (?, ?, ?, 'ACTIVE', ?, ?, NULL, NULL, ?, NULL, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          status = 'ACTIVE',
          direction = VALUES(direction),
          entry_price = VALUES(entry_price),
          exit_price = NULL,
          exit_reason = NULL,
          opened_at = IF(kol_positions.opened_at IS NULL, VALUES(opened_at), kol_positions.opened_at),
          closed_at = NULL,
          last_opinion_id = VALUES(last_opinion_id),
          last_action = VALUES(last_action),
          updated_at = VALUES(updated_at)
        """, JdbcSupport.id(), kolId, instrumentId, direction, entryPrice,
        openedAt, blank(opinionId), blank(action), now, now);
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

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String trim(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String compact = value.trim();
    return compact.length() <= 64 ? compact : compact.substring(0, 64);
  }
}
