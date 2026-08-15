package com.personal.tracker.repository.alerts;

import com.personal.tracker.repository.JdbcSupport;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PriceAlertRepository {
  private final JdbcTemplate jdbc;
  private volatile boolean schemaReady;
  private final RowMapper<PriceAlertView> viewMapper = (rs, rowNum) -> new PriceAlertView(
      rs.getString("id"),
      rs.getString("instrument_id"),
      rs.getString("symbol"),
      rs.getString("name"),
      rs.getString("market"),
      rs.getString("alert_type"),
      rs.getString("trigger_direction"),
      rs.getBigDecimal("lower_price"),
      rs.getBigDecimal("upper_price"),
      rs.getBigDecimal("target_price"),
      rs.getString("status"),
      rs.getBigDecimal("last_price"),
      rs.getString("last_checked_at"),
      rs.getString("triggered_at"),
      rs.getString("notify_status"),
      rs.getString("error_message"),
      rs.getString("source_recognition_id"),
      rs.getString("source_candidate_id"),
      rs.getString("source_message_id"),
      rs.getString("created_at"),
      rs.getString("updated_at"));
  private final RowMapper<ActiveAlert> activeMapper = (rs, rowNum) -> new ActiveAlert(
      rs.getString("id"),
      rs.getString("instrument_id"),
      rs.getString("symbol"),
      rs.getString("name"),
      rs.getString("alert_type"),
      rs.getString("trigger_direction"),
      rs.getBigDecimal("lower_price"),
      rs.getBigDecimal("upper_price"),
      rs.getBigDecimal("target_price"),
      rs.getBigDecimal("last_price"),
      rs.getString("bitget_category"),
      rs.getString("bitget_symbol"));

  public PriceAlertRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void initialize() {
    ensureSchema();
  }

  public List<PriceAlertView> list() {
    ensureSchema();
    return jdbc.query("""
        SELECT a.*, i.symbol, i.name, i.market, r.message_id source_message_id
        FROM price_signal_alerts a
        JOIN instruments i ON i.id = a.instrument_id
        LEFT JOIN message_price_alert_recognitions r ON r.id = a.source_recognition_id
        ORDER BY FIELD(a.status, 'ACTIVE', 'ERROR', 'TRIGGERED', 'PAUSED'), a.updated_at DESC
        """, viewMapper);
  }

  public List<ActiveAlert> active() {
    ensureSchema();
    return jdbc.query("""
        SELECT a.id, a.instrument_id, i.symbol, i.name, a.alert_type, a.trigger_direction,
               a.lower_price, a.upper_price, a.target_price, a.last_price,
               i.bitget_category, i.bitget_symbol
        FROM price_signal_alerts a
        JOIN instruments i ON i.id = a.instrument_id
        WHERE a.status = 'ACTIVE'
          AND i.bitget_category IS NOT NULL AND i.bitget_category <> ''
          AND i.bitget_symbol IS NOT NULL AND i.bitget_symbol <> ''
        ORDER BY i.bitget_category, i.bitget_symbol, a.created_at
        """, activeMapper);
  }

  public void recoverStaleDeliveries() {
    ensureSchema();
    jdbc.update("""
        UPDATE price_signal_alerts
        SET status = 'ACTIVE', notify_status = 'WAITING',
            error_message = '上次发送过程被中断，已自动恢复监控', updated_at = ?
        WHERE status = 'DELIVERING' AND updated_at < ?
        """, JdbcSupport.now(), Instant.now().minusSeconds(120).toString());
  }

  public void recoverMissingPushTargets() {
    ensureSchema();
    jdbc.update("""
        UPDATE price_signal_alerts
        SET status = 'ACTIVE', notify_status = 'WAITING',
            error_message = NULL, updated_at = ?
        WHERE status = 'ERROR' AND error_message = 'WxPusher 推送目标未配置'
        """, JdbcSupport.now());
  }

  public PriceAlertView create(
      String instrumentId,
      String alertType,
      String triggerDirection,
      BigDecimal lower,
      BigDecimal upper,
      BigDecimal target,
      String sourceRecognitionId,
      String sourceCandidateId) {
    ensureSchema();
    String id = JdbcSupport.id();
    String now = JdbcSupport.now();
    jdbc.update("""
        INSERT INTO price_signal_alerts(
          id, instrument_id, alert_type, trigger_direction,
          lower_price, upper_price, target_price, source_recognition_id, source_candidate_id,
          status, notify_status, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 'WAITING', ?, ?)
        """, id, instrumentId, alertType, triggerDirection, lower, upper, target,
        nullable(sourceRecognitionId), nullable(sourceCandidateId), now, now);
    return find(id).orElseThrow();
  }

  public Optional<PriceAlertView> update(
      String id,
      String instrumentId,
      String alertType,
      String triggerDirection,
      BigDecimal lower,
      BigDecimal upper,
      BigDecimal target) {
    ensureSchema();
    jdbc.update("""
        UPDATE price_signal_alerts
        SET instrument_id = ?, alert_type = ?, trigger_direction = ?, lower_price = ?, upper_price = ?,
            target_price = ?, status = 'ACTIVE', last_price = NULL,
            last_checked_at = NULL, triggered_at = NULL, notify_status = 'WAITING',
            error_message = NULL, updated_at = ?
        WHERE id = ?
        """, instrumentId, alertType, triggerDirection, lower, upper, target, JdbcSupport.now(), id);
    return find(id);
  }

  public Optional<PriceAlertView> setEnabled(String id, boolean enabled) {
    ensureSchema();
    String status = enabled ? "ACTIVE" : "PAUSED";
    String notifyStatus = enabled ? "WAITING" : "";
    jdbc.update("""
        UPDATE price_signal_alerts
        SET status = ?, notify_status = ?, error_message = NULL,
            triggered_at = NULL,
            last_price = CASE WHEN ? THEN NULL ELSE last_price END,
            last_checked_at = CASE WHEN ? THEN NULL ELSE last_checked_at END,
            updated_at = ?
        WHERE id = ?
        """, status, notifyStatus, enabled, enabled, JdbcSupport.now(), id);
    return find(id);
  }

  public boolean delete(String id) {
    ensureSchema();
    return jdbc.update("DELETE FROM price_signal_alerts WHERE id = ?", id) > 0;
  }

  public Optional<PriceAlertView> findBySource(String recognitionId, String candidateId) {
    ensureSchema();
    if (recognitionId == null || recognitionId.isBlank() || candidateId == null || candidateId.isBlank()) {
      return Optional.empty();
    }
    return jdbc.query("""
        SELECT a.*, i.symbol, i.name, i.market, r.message_id source_message_id
        FROM price_signal_alerts a JOIN instruments i ON i.id = a.instrument_id
        LEFT JOIN message_price_alert_recognitions r ON r.id = a.source_recognition_id
        WHERE a.source_recognition_id = ? AND a.source_candidate_id = ?
        """, viewMapper, recognitionId.trim(), candidateId.trim()).stream().findFirst();
  }

  public Optional<PriceAlertView> findById(String id) {
    ensureSchema();
    return find(id);
  }

  public Optional<PriceAlertView> findEquivalent(
      String instrumentId,
      String alertType,
      String triggerDirection,
      BigDecimal lower,
      BigDecimal upper,
      BigDecimal target) {
    ensureSchema();
    return jdbc.query("""
        SELECT a.*, i.symbol, i.name, i.market, r.message_id source_message_id
        FROM price_signal_alerts a JOIN instruments i ON i.id = a.instrument_id
        LEFT JOIN message_price_alert_recognitions r ON r.id = a.source_recognition_id
        WHERE a.instrument_id = ? AND a.alert_type = ? AND a.trigger_direction = ?
          AND a.lower_price = ? AND a.upper_price = ?
          AND (a.target_price = ? OR (a.target_price IS NULL AND ? IS NULL))
        LIMIT 1
        """, viewMapper, instrumentId, alertType, triggerDirection, lower, upper, target, target)
        .stream().findFirst();
  }

  public PriceAlertView linkSourceIfMissing(
      String id, String recognitionId, String candidateId) {
    ensureSchema();
    jdbc.update("""
        UPDATE price_signal_alerts
        SET source_recognition_id = ?, source_candidate_id = ?, updated_at = ?
        WHERE id = ? AND source_recognition_id IS NULL
        """, nullable(recognitionId), nullable(candidateId), JdbcSupport.now(), id);
    return find(id).orElseThrow(() -> new IllegalArgumentException("价格提醒不存在"));
  }

  public boolean claim(String id, BigDecimal price, String checkedAt) {
    ensureSchema();
    return jdbc.update("""
        UPDATE price_signal_alerts
        SET status = 'DELIVERING', last_price = ?, last_checked_at = ?, updated_at = ?
        WHERE id = ? AND status = 'ACTIVE'
        """, price, checkedAt, JdbcSupport.now(), id) > 0;
  }

  public void observe(String id, BigDecimal price, String checkedAt) {
    ensureSchema();
    jdbc.update("""
        UPDATE price_signal_alerts
        SET last_price = ?, last_checked_at = ?, updated_at = ?
        WHERE id = ? AND status = 'ACTIVE'
        """, price, checkedAt, JdbcSupport.now(), id);
  }

  public void markSent(String id, BigDecimal price, String triggeredAt) {
    jdbc.update("""
        UPDATE price_signal_alerts
        SET status = 'TRIGGERED', last_price = ?, last_checked_at = ?,
            triggered_at = ?, notify_status = 'SENT', error_message = NULL, updated_at = ?
        WHERE id = ? AND status = 'DELIVERING'
        """, price, triggeredAt, triggeredAt, JdbcSupport.now(), id);
  }

  public void markError(String id, BigDecimal price, String checkedAt, String error) {
    jdbc.update("""
        UPDATE price_signal_alerts
        SET status = 'ERROR', last_price = ?, last_checked_at = ?,
            notify_status = 'FAILED', error_message = ?, updated_at = ?
        WHERE id = ? AND status = 'DELIVERING'
        """, price, checkedAt, error, JdbcSupport.now(), id);
  }

  private Optional<PriceAlertView> find(String id) {
    return jdbc.query("""
        SELECT a.*, i.symbol, i.name, i.market, r.message_id source_message_id
        FROM price_signal_alerts a
        JOIN instruments i ON i.id = a.instrument_id
        LEFT JOIN message_price_alert_recognitions r ON r.id = a.source_recognition_id
        WHERE a.id = ?
        """, viewMapper, id).stream().findFirst();
  }

  private void ensureSchema() {
    if (schemaReady) {
      return;
    }
    synchronized (this) {
      if (schemaReady) {
        return;
      }
      jdbc.execute("""
          CREATE TABLE IF NOT EXISTS price_signal_alerts (
            id VARCHAR(64) PRIMARY KEY,
            instrument_id VARCHAR(64) NOT NULL,
            alert_type VARCHAR(16) NOT NULL DEFAULT 'RANGE',
            trigger_direction VARCHAR(16) NOT NULL DEFAULT 'ANY',
            lower_price DECIMAL(24, 8) NOT NULL,
            upper_price DECIMAL(24, 8) NOT NULL,
            target_price DECIMAL(24, 8),
            status VARCHAR(32) NOT NULL,
            last_price DECIMAL(24, 8),
            last_checked_at VARCHAR(64),
            triggered_at VARCHAR(64),
            notify_status VARCHAR(32) NOT NULL,
            error_message TEXT,
            source_recognition_id VARCHAR(64),
            source_candidate_id VARCHAR(64),
            created_at VARCHAR(64) NOT NULL,
            updated_at VARCHAR(64) NOT NULL,
            INDEX idx_price_signal_active(status, instrument_id, updated_at)
          ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
          """);
      ensureColumn("alert_type", "ALTER TABLE price_signal_alerts "
          + "ADD COLUMN alert_type VARCHAR(16) NOT NULL DEFAULT 'RANGE' AFTER instrument_id");
      ensureColumn("target_price", "ALTER TABLE price_signal_alerts "
          + "ADD COLUMN target_price DECIMAL(24, 8) AFTER upper_price");
      ensureColumn("trigger_direction", "ALTER TABLE price_signal_alerts "
          + "ADD COLUMN trigger_direction VARCHAR(16) NOT NULL DEFAULT 'ANY' AFTER alert_type");
      ensureColumn("source_recognition_id", "ALTER TABLE price_signal_alerts "
          + "ADD COLUMN source_recognition_id VARCHAR(64) AFTER error_message");
      ensureColumn("source_candidate_id", "ALTER TABLE price_signal_alerts "
          + "ADD COLUMN source_candidate_id VARCHAR(64) AFTER source_recognition_id");
      ensureUniqueIndex();
      schemaReady = true;
    }
  }

  private void ensureColumn(String column, String alterSql) {
    Integer count = jdbc.queryForObject("""
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'price_signal_alerts'
          AND column_name = ?
        """, Integer.class, column);
    if (count == null || count == 0) {
      jdbc.execute(alterSql);
    }
  }

  private void ensureUniqueIndex() {
    Integer count = jdbc.queryForObject("""
        SELECT COUNT(*) FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'price_signal_alerts'
          AND index_name = 'uq_price_signal_source'
        """, Integer.class);
    if (count == null || count == 0) {
      jdbc.execute("CREATE UNIQUE INDEX uq_price_signal_source "
          + "ON price_signal_alerts(source_recognition_id, source_candidate_id)");
    }
  }

  private String nullable(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public record PriceAlertView(
      String id,
      String instrumentId,
      String symbol,
      String name,
      String market,
      String alertType,
      String triggerDirection,
      BigDecimal lowerPrice,
      BigDecimal upperPrice,
      BigDecimal targetPrice,
      String status,
      BigDecimal lastPrice,
      String lastCheckedAt,
      String triggeredAt,
      String notifyStatus,
      String errorMessage,
      String sourceRecognitionId,
      String sourceCandidateId,
      String sourceMessageId,
      String createdAt,
      String updatedAt) {
  }

  public record ActiveAlert(
      String id,
      String instrumentId,
      String symbol,
      String name,
      String alertType,
      String triggerDirection,
      BigDecimal lowerPrice,
      BigDecimal upperPrice,
      BigDecimal targetPrice,
      BigDecimal lastPrice,
      String bitgetCategory,
      String bitgetSymbol) {
  }
}
