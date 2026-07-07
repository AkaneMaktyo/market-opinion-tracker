package com.personal.tracker.repository.resonance;

import com.personal.tracker.repository.JdbcSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ResonanceRepository {
  private final JdbcTemplate jdbc;
  private final RowMapper<OpinionSignal> signalMapper = (rs, rowNum) -> new OpinionSignal(
      rs.getString("opinion_id"), rs.getString("instrument_id"), rs.getString("symbol"),
      rs.getString("source_name"), rs.getString("direction"), rs.getString("horizon"),
      rs.getString("thesis"), rs.getString("trigger_condition"), rs.getString("invalidation"),
      (Integer) rs.getObject("confidence"), rs.getString("source_quote"),
      rs.getString("risks_text"), rs.getString("catalysts_text"),
      rs.getString("price_notes_text"), rs.getString("opinion_time"));
  private final RowMapper<ClusterRecord> clusterMapper = (rs, rowNum) -> new ClusterRecord(
      rs.getString("id"), rs.getString("instrument_id"), rs.getString("symbol"),
      rs.getString("bucket_date"), rs.getString("direction"), rs.getString("horizon"),
      rs.getInt("score"), rs.getString("grade"), rs.getString("action"),
      rs.getString("summary"), rs.getString("trigger_text"), rs.getString("invalidation_text"),
      rs.getString("risk_text"), rs.getString("catalyst_text"), rs.getInt("source_count"),
      rs.getInt("opinion_count"), rs.getInt("support_count"), rs.getInt("conflict_count"),
      rs.getString("source_names"), rs.getString("last_opinion_at"), rs.getString("status"),
      rs.getString("alert_status"), rs.getString("alert_error"), rs.getString("last_alert_at"),
      rs.getString("created_at"), rs.getString("updated_at"));
  private final RowMapper<ClusterItem> itemMapper = (rs, rowNum) -> new ClusterItem(
      rs.getString("opinion_id"), rs.getString("role"), rs.getString("source_name"),
      rs.getString("direction"), rs.getString("horizon"), rs.getString("thesis"),
      rs.getString("source_quote"), rs.getString("opinion_time"));

  public ResonanceRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<OpinionSignal> recentSignals(String symbol, int limit) {
    ensureSchema();
    return jdbc.query("""
        SELECT o.id AS opinion_id, o.instrument_id, i.symbol, k.name AS source_name,
          o.direction, o.horizon, o.thesis, o.trigger_condition, o.invalidation,
          o.confidence, o.source_quote, o.risks_text, o.catalysts_text,
          o.price_notes_text, o.opinion_time
        FROM opinions o
        JOIN instruments i ON i.id = o.instrument_id
        JOIN live_sessions s ON s.id = o.session_id
        JOIN kols k ON k.id = s.kol_id
        WHERE i.symbol = ? AND o.status = 'ACTIVE'
        ORDER BY o.opinion_time DESC
        LIMIT ?
        """, signalMapper, JdbcSupport.symbol(symbol), Math.max(20, Math.min(limit, 300)));
  }

  public List<ClusterRecord> list(String symbol, String since, int limit) {
    ensureSchema();
    List<Object> args = new ArrayList<>();
    StringBuilder sql = new StringBuilder("""
        SELECT * FROM resonance_clusters
        WHERE status = 'ACTIVE'
        """);
    if (symbol != null && !symbol.isBlank()) {
      sql.append(" AND symbol = ?");
      args.add(JdbcSupport.symbol(symbol));
    }
    if (since != null && !since.isBlank()) {
      sql.append(" AND last_opinion_at >= ?");
      args.add(since.trim());
    }
    sql.append(" ORDER BY score DESC, last_opinion_at DESC LIMIT ?");
    args.add(Math.max(1, Math.min(limit, 100)));
    return jdbc.query(sql.toString(), clusterMapper, args.toArray());
  }

  public List<RecentMessage> recentMessages(String since, int limit) {
    ensureSchema();
    return jdbc.query("""
        SELECT id, blogger_name, title, summary, detail_text, message_time
        FROM wxpusher_messages
        WHERE message_time >= ?
          AND status NOT IN ('PENDING', 'PROCESSING')
        ORDER BY message_time DESC, updated_at DESC
        LIMIT ?
        """, (rs, rowNum) -> new RecentMessage(
        rs.getString("id"),
        rs.getString("blogger_name"),
        rs.getString("title"),
        rs.getString("summary"),
        rs.getString("detail_text"),
        rs.getString("message_time")),
        since == null ? "" : since.trim(),
        Math.max(1, Math.min(limit, 1000)));
  }

  public List<ClusterItem> items(String clusterId) {
    ensureSchema();
    return jdbc.query("""
        SELECT * FROM resonance_cluster_items
        WHERE cluster_id = ?
        ORDER BY role DESC, opinion_time DESC
        """, itemMapper, clusterId);
  }

  public ClusterRecord cluster(String id) {
    ensureSchema();
    return findById(id).orElseThrow(() -> new IllegalArgumentException("共振簇不存在"));
  }

  @Transactional
  public ClusterRecord save(ClusterDraft draft) {
    ensureSchema();
    Optional<ClusterRecord> existing = findByKey(
        draft.instrumentId(), draft.bucketDate(), draft.direction(), draft.horizon());
    if (existing.isPresent()) {
      update(existing.get().id(), draft);
      return findById(existing.get().id()).orElseThrow();
    }
    String id = JdbcSupport.id();
    String now = JdbcSupport.now();
    jdbc.update("""
        INSERT INTO resonance_clusters(
          id, instrument_id, symbol, bucket_date, direction, horizon, score, grade,
          action, summary, trigger_text, invalidation_text, risk_text, catalyst_text,
          source_count, opinion_count, support_count, conflict_count, source_names,
          last_opinion_at, status, alert_status, alert_error, last_alert_at, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', 'PENDING', '', '', ?, ?)
        """, id, draft.instrumentId(), draft.symbol(), draft.bucketDate(), draft.direction(),
        draft.horizon(), draft.score(), draft.grade(), draft.action(), draft.summary(),
        draft.triggerText(), draft.invalidationText(), draft.riskText(), draft.catalystText(),
        draft.sourceCount(), draft.opinionCount(), draft.supportCount(), draft.conflictCount(),
        draft.sourceNames(), draft.lastOpinionAt(), now, now);
    return findById(id).orElseThrow();
  }

  public void replaceItems(String clusterId, List<ItemDraft> items) {
    ensureSchema();
    jdbc.update("DELETE FROM resonance_cluster_items WHERE cluster_id = ?", clusterId);
    jdbc.batchUpdate("""
        INSERT INTO resonance_cluster_items(
          id, cluster_id, opinion_id, role, source_name, direction, horizon,
          thesis, source_quote, opinion_time, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, items, 30, (ps, item) -> {
      ps.setString(1, JdbcSupport.id());
      ps.setString(2, clusterId);
      ps.setString(3, item.opinionId());
      ps.setString(4, item.role());
      ps.setString(5, item.sourceName());
      ps.setString(6, item.direction());
      ps.setString(7, item.horizon());
      ps.setString(8, item.thesis());
      ps.setString(9, item.sourceQuote());
      ps.setString(10, item.opinionTime());
      ps.setString(11, JdbcSupport.now());
    });
  }

  public void createAlert(AlertDraft draft) {
    ensureSchema();
    jdbc.update("""
        INSERT INTO resonance_alerts(id, cluster_id, title, content, status, error_message, sent_at, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, JdbcSupport.id(), draft.clusterId(), draft.title(), draft.content(),
        draft.status(), draft.errorMessage(), draft.sentAt(), JdbcSupport.now());
  }

  public void markAlert(String clusterId, String status, String error, String sentAt) {
    ensureSchema();
    jdbc.update("""
        UPDATE resonance_clusters
        SET alert_status = ?, alert_error = ?, last_alert_at = ?, updated_at = ?
        WHERE id = ?
        """, status, error == null ? "" : error, sentAt == null ? "" : sentAt,
        JdbcSupport.now(), clusterId);
  }

  private Optional<ClusterRecord> findByKey(
      String instrumentId,
      String bucketDate,
      String direction,
      String horizon) {
    return jdbc.query("""
        SELECT * FROM resonance_clusters
        WHERE instrument_id = ? AND bucket_date = ? AND direction = ? AND horizon = ?
        """, clusterMapper, instrumentId, bucketDate, direction, horizon).stream().findFirst();
  }

  private Optional<ClusterRecord> findById(String id) {
    return jdbc.query("SELECT * FROM resonance_clusters WHERE id = ?", clusterMapper, id)
        .stream()
        .findFirst();
  }

  private void update(String id, ClusterDraft draft) {
    jdbc.update("""
        UPDATE resonance_clusters
        SET score = ?, grade = ?, action = ?, summary = ?, trigger_text = ?,
            invalidation_text = ?, risk_text = ?, catalyst_text = ?, source_count = ?,
            opinion_count = ?, support_count = ?, conflict_count = ?, source_names = ?,
            last_opinion_at = ?, status = 'ACTIVE', updated_at = ?
        WHERE id = ?
        """, draft.score(), draft.grade(), draft.action(), draft.summary(), draft.triggerText(),
        draft.invalidationText(), draft.riskText(), draft.catalystText(), draft.sourceCount(),
        draft.opinionCount(), draft.supportCount(), draft.conflictCount(), draft.sourceNames(),
        draft.lastOpinionAt(), JdbcSupport.now(), id);
  }

  private void ensureSchema() {
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS resonance_clusters (
          id VARCHAR(64) PRIMARY KEY,
          instrument_id VARCHAR(64) NOT NULL,
          symbol VARCHAR(64) NOT NULL,
          bucket_date VARCHAR(32) NOT NULL,
          direction VARCHAR(32) NOT NULL,
          horizon VARCHAR(64) NOT NULL,
          score INT NOT NULL,
          grade VARCHAR(32) NOT NULL,
          action VARCHAR(64) NOT NULL,
          summary TEXT,
          trigger_text TEXT,
          invalidation_text TEXT,
          risk_text TEXT,
          catalyst_text TEXT,
          source_count INT NOT NULL,
          opinion_count INT NOT NULL,
          support_count INT NOT NULL,
          conflict_count INT NOT NULL,
          source_names TEXT,
          last_opinion_at VARCHAR(64) NOT NULL,
          status VARCHAR(32) NOT NULL,
          alert_status VARCHAR(32) NOT NULL,
          alert_error TEXT,
          last_alert_at VARCHAR(64),
          created_at VARCHAR(64) NOT NULL,
          updated_at VARCHAR(64) NOT NULL,
          UNIQUE KEY uq_resonance_key(instrument_id, bucket_date, direction, horizon),
          INDEX idx_resonance_symbol(symbol, score, last_opinion_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """);
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS resonance_cluster_items (
          id VARCHAR(64) PRIMARY KEY,
          cluster_id VARCHAR(64) NOT NULL,
          opinion_id VARCHAR(64) NOT NULL,
          role VARCHAR(32) NOT NULL,
          source_name VARCHAR(255) NOT NULL,
          direction VARCHAR(32) NOT NULL,
          horizon VARCHAR(64) NOT NULL,
          thesis TEXT,
          source_quote TEXT,
          opinion_time VARCHAR(64) NOT NULL,
          created_at VARCHAR(64) NOT NULL,
          UNIQUE KEY uq_resonance_item(cluster_id, opinion_id),
          INDEX idx_resonance_item_cluster(cluster_id, role)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """);
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS resonance_alerts (
          id VARCHAR(64) PRIMARY KEY,
          cluster_id VARCHAR(64) NOT NULL,
          title VARCHAR(255) NOT NULL,
          content TEXT NOT NULL,
          status VARCHAR(32) NOT NULL,
          error_message TEXT,
          sent_at VARCHAR(64),
          created_at VARCHAR(64) NOT NULL,
          INDEX idx_resonance_alert_cluster(cluster_id, created_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """);
  }

  public record OpinionSignal(
      String opinionId,
      String instrumentId,
      String symbol,
      String sourceName,
      String direction,
      String horizon,
      String thesis,
      String triggerCondition,
      String invalidation,
      Integer confidence,
      String sourceQuote,
      String risksText,
      String catalystsText,
      String priceNotesText,
      String opinionTime) {
  }

  public record ClusterRecord(
      String id,
      String instrumentId,
      String symbol,
      String bucketDate,
      String direction,
      String horizon,
      int score,
      String grade,
      String action,
      String summary,
      String triggerText,
      String invalidationText,
      String riskText,
      String catalystText,
      int sourceCount,
      int opinionCount,
      int supportCount,
      int conflictCount,
      String sourceNames,
      String lastOpinionAt,
      String status,
      String alertStatus,
      String alertError,
      String lastAlertAt,
      String createdAt,
      String updatedAt) {
  }

  public record ClusterItem(
      String opinionId,
      String role,
      String sourceName,
      String direction,
      String horizon,
      String thesis,
      String sourceQuote,
      String opinionTime) {
  }

  public record RecentMessage(
      String id,
      String bloggerName,
      String title,
      String summary,
      String detailText,
      String messageTime) {
  }

  public record ClusterDraft(
      String instrumentId,
      String symbol,
      String bucketDate,
      String direction,
      String horizon,
      int score,
      String grade,
      String action,
      String summary,
      String triggerText,
      String invalidationText,
      String riskText,
      String catalystText,
      int sourceCount,
      int opinionCount,
      int supportCount,
      int conflictCount,
      String sourceNames,
      String lastOpinionAt) {
  }

  public record ItemDraft(
      String opinionId,
      String role,
      String sourceName,
      String direction,
      String horizon,
      String thesis,
      String sourceQuote,
      String opinionTime) {
  }

  public record AlertDraft(
      String clusterId,
      String title,
      String content,
      String status,
      String errorMessage,
      String sentAt) {
  }
}
