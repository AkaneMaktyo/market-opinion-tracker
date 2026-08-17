package com.personal.tracker.repository;

import com.personal.tracker.domain.Opinion;
import com.personal.tracker.domain.PriceLevel;
import com.personal.tracker.domain.Review;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OpinionRepository {
  private final JdbcTemplate jdbc;
  private final RowMapper<Opinion> opinionMapper = (rs, rowNum) -> new Opinion(
      rs.getString("id"), rs.getString("session_id"), rs.getString("instrument_id"),
      rs.getString("symbol"), rs.getString("direction"), rs.getString("horizon"),
      rs.getString("thesis"), rs.getString("trigger_condition"),
      rs.getString("invalidation"), (Integer) rs.getObject("confidence"),
      rs.getString("source_quote"), rs.getBigDecimal("reference_price"),
      rs.getString("raw_direction"), rs.getString("risks_text"),
      rs.getString("catalysts_text"), rs.getString("price_notes_text"),
      rs.getString("raw_item_json"),
      rs.getString("opinion_time"), rs.getString("status"), rs.getString("created_at"));
  private final RowMapper<PriceLevel> levelMapper = (rs, rowNum) -> new PriceLevel(
      rs.getString("id"), rs.getString("opinion_id"), rs.getString("level_type"),
      rs.getBigDecimal("price"), rs.getString("note"));
  private final RowMapper<Review> reviewMapper = (rs, rowNum) -> new Review(
      rs.getString("id"), rs.getString("opinion_id"), rs.getString("outcome"),
      rs.getString("notes"), rs.getBigDecimal("result_price"),
      rs.getString("review_date"), rs.getString("created_at"));

  public OpinionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Opinion create(Opinion input) {
    Opinion item = new Opinion(
        JdbcSupport.id(), input.sessionId(), input.instrumentId(), input.symbol(),
        input.direction(), input.horizon(), input.thesis(), input.triggerCondition(),
        input.invalidation(), input.confidence(), input.sourceQuote(),
        input.referencePrice(), input.rawDirection(), input.risksText(),
        input.catalystsText(), input.priceNotesText(), input.rawItemJson(),
        input.opinionTime(), input.status(), JdbcSupport.now());
    jdbc.update("""
        INSERT INTO opinions(
          id, session_id, instrument_id, direction, horizon, thesis,
          trigger_condition, invalidation, confidence, source_quote,
          reference_price, raw_direction, risks_text, catalysts_text,
          price_notes_text, raw_item_json, opinion_time, status, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, item.id(), item.sessionId(), item.instrumentId(), item.direction(),
        item.horizon(), item.thesis(), item.triggerCondition(), item.invalidation(),
        item.confidence(), item.sourceQuote(), item.referencePrice(), item.rawDirection(),
        item.risksText(), item.catalystsText(), item.priceNotesText(), item.rawItemJson(),
        item.opinionTime(), item.status(), item.createdAt());
    return item;
  }

  public Opinion upsertMessage(String id, Opinion input) {
    Opinion item = new Opinion(
        id, input.sessionId(), input.instrumentId(), input.symbol(),
        input.direction(), input.horizon(), input.thesis(), input.triggerCondition(),
        input.invalidation(), input.confidence(), input.sourceQuote(),
        input.referencePrice(), input.rawDirection(), input.risksText(),
        input.catalystsText(), input.priceNotesText(), input.rawItemJson(),
        input.opinionTime(), "MESSAGE", JdbcSupport.now());
    jdbc.update("""
        INSERT INTO opinions(
          id, session_id, instrument_id, direction, horizon, thesis,
          trigger_condition, invalidation, confidence, source_quote,
          reference_price, raw_direction, risks_text, catalysts_text,
          price_notes_text, raw_item_json, opinion_time, status, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          session_id = VALUES(session_id),
          instrument_id = VALUES(instrument_id),
          direction = VALUES(direction),
          horizon = VALUES(horizon),
          thesis = VALUES(thesis),
          source_quote = VALUES(source_quote),
          raw_direction = VALUES(raw_direction),
          raw_item_json = VALUES(raw_item_json),
          opinion_time = VALUES(opinion_time),
          status = 'MESSAGE'
        """, item.id(), item.sessionId(), item.instrumentId(), item.direction(),
        item.horizon(), item.thesis(), item.triggerCondition(), item.invalidation(),
        item.confidence(), item.sourceQuote(), item.referencePrice(), item.rawDirection(),
        item.risksText(), item.catalystsText(), item.priceNotesText(), item.rawItemJson(),
        item.opinionTime(), item.status(), item.createdAt());
    return item;
  }

  public void deleteMessageFallbacks(String sessionId) {
    jdbc.update(
        "DELETE FROM opinions WHERE session_id = ? AND status = 'MESSAGE'",
        sessionId);
  }

  public void updateSourceQuoteBySession(String sessionId, String sourceQuote) {
    jdbc.update(
        "UPDATE opinions SET source_quote = ? WHERE session_id = ?",
        sourceQuote,
        sessionId);
  }

  public List<Opinion> find(String kolId, String symbol, String status, int limit) {
    List<Object> args = new ArrayList<>();
    StringBuilder sql = new StringBuilder("""
        SELECT o.*, i.symbol FROM opinions o
        JOIN instruments i ON i.id = o.instrument_id
        JOIN live_sessions s ON s.id = o.session_id
        WHERE 1 = 1
        """);
    if (kolId != null && !kolId.isBlank()) {
      sql.append(" AND s.kol_id = ?");
      args.add(kolId.trim());
    }
    if (symbol != null && !symbol.isBlank()) {
      sql.append(" AND i.symbol = ?");
      args.add(JdbcSupport.symbol(symbol));
    }
    if (status != null && !status.isBlank()) {
      sql.append(" AND o.status = ?");
      args.add(status.trim().toUpperCase());
    }
    sql.append(" ORDER BY o.opinion_time DESC LIMIT ?");
    args.add(Math.max(1, Math.min(limit, 300)));
    return jdbc.query(sql.toString(), opinionMapper, args.toArray());
  }

  public List<Opinion> findAllByKol(String kolId, String sourceInclude) {
    boolean filterSource = sourceInclude != null && !sourceInclude.isBlank();
    String sourceFilter = filterSource
        ? " AND s.raw_text LIKE CONCAT('%', ?, '%')\n"
        : "";
    List<Object> args = new ArrayList<>();
    args.add(kolId.trim());
    if (filterSource) {
      args.add(sourceInclude.trim());
    }
    return jdbc.query("""
        SELECT o.*, i.symbol FROM opinions o
        JOIN instruments i ON i.id = o.instrument_id
        JOIN live_sessions s ON s.id = o.session_id
        WHERE s.kol_id = ?
        """ + sourceFilter + """
        AND o.status <> 'MESSAGE'
        ORDER BY o.opinion_time, o.created_at
        """, opinionMapper, args.toArray());
  }

  public Optional<Opinion> findById(String id) {
    List<Opinion> rows = jdbc.query("""
        SELECT o.*, i.symbol FROM opinions o
        JOIN instruments i ON i.id = o.instrument_id
        WHERE o.id = ?
        """, opinionMapper, id);
    return rows.stream().findFirst();
  }

  public List<PriceLevel> findLevels(String opinionId) {
    return jdbc.query(
        "SELECT * FROM price_levels WHERE opinion_id = ? ORDER BY level_type",
        levelMapper,
        opinionId);
  }

  public Map<String, List<PriceLevel>> findLevelsByOpinionIds(List<String> opinionIds) {
    if (opinionIds.isEmpty()) {
      return Map.of();
    }
    String sql = "SELECT * FROM price_levels WHERE opinion_id IN (%s) ORDER BY level_type"
        .formatted(placeholders(opinionIds.size()));
    return jdbc.query(sql, levelMapper, opinionIds.toArray()).stream()
        .collect(Collectors.groupingBy(PriceLevel::opinionId));
  }

  public void replaceLevels(String opinionId, List<PriceLevel> levels) {
    jdbc.update("DELETE FROM price_levels WHERE opinion_id = ?", opinionId);
    if (levels == null || levels.isEmpty()) {
      return;
    }
    jdbc.batchUpdate("""
        INSERT INTO price_levels(id, opinion_id, level_type, price, note)
        VALUES (?, ?, ?, ?, ?)
        """, levels, 20, (ps, item) -> {
      ps.setString(1, JdbcSupport.id());
      ps.setString(2, opinionId);
      ps.setString(3, item.levelType());
      ps.setBigDecimal(4, item.price());
      ps.setString(5, item.note());
    });
  }

  public Optional<Review> findReview(String opinionId) {
    List<Review> rows = jdbc.query(
        "SELECT * FROM reviews WHERE opinion_id = ?",
        reviewMapper,
        opinionId);
    return rows.stream().findFirst();
  }

  public Map<String, Review> findReviewsByOpinionIds(List<String> opinionIds) {
    if (opinionIds.isEmpty()) {
      return Map.of();
    }
    String sql = "SELECT * FROM reviews WHERE opinion_id IN (%s)"
        .formatted(placeholders(opinionIds.size()));
    return jdbc.query(sql, reviewMapper, opinionIds.toArray()).stream()
        .collect(Collectors.toMap(Review::opinionId, Function.identity()));
  }

  public Review saveReview(Review input) {
    Review item = new Review(
        JdbcSupport.id(), input.opinionId(), input.outcome(), input.notes(),
        input.resultPrice(), input.reviewDate(), JdbcSupport.now());
    jdbc.update("""
        INSERT INTO reviews(id, opinion_id, outcome, notes, result_price, review_date, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          outcome = VALUES(outcome),
          notes = VALUES(notes),
          result_price = VALUES(result_price),
          review_date = VALUES(review_date)
        """, item.id(), item.opinionId(), item.outcome(), item.notes(),
        item.resultPrice(), item.reviewDate(), item.createdAt());
    return findReview(input.opinionId()).orElse(item);
  }

  private static String placeholders(int count) {
    return String.join(", ", java.util.Collections.nCopies(count, "?"));
  }
}
