package com.personal.tracker.repository.trading;

import com.personal.tracker.repository.JdbcSupport;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PositionCostOverrideRepository {
  private final JdbcTemplate jdbc;

  public PositionCostOverrideRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void initialize() {
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS position_cost_overrides (
          provider VARCHAR(32) NOT NULL,
          symbol VARCHAR(64) NOT NULL,
          average_cost DECIMAL(24, 8) NOT NULL,
          updated_at VARCHAR(64) NOT NULL,
          PRIMARY KEY (provider, symbol)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """);
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS position_cost_anchors (
          provider VARCHAR(32) NOT NULL,
          symbol VARCHAR(64) NOT NULL,
          basis_quantity DECIMAL(32, 12) NOT NULL,
          basis_cost DECIMAL(24, 8) NOT NULL,
          trade_quantity DECIMAL(32, 12) NOT NULL DEFAULT 0,
          trade_quote DECIMAL(24, 8) NOT NULL DEFAULT 0,
          updated_at VARCHAR(64) NOT NULL,
          PRIMARY KEY (provider, symbol)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """);
  }

  public List<PositionCostOverride> findAll() {
    return jdbc.query("""
        SELECT o.provider, o.symbol, o.average_cost, o.updated_at,
               a.basis_quantity, a.basis_cost, a.trade_quantity, a.trade_quote
        FROM position_cost_overrides o
        LEFT JOIN position_cost_anchors a
          ON a.provider = o.provider AND a.symbol = o.symbol
        """, (rs, rowNum) ->
        new PositionCostOverride(
            rs.getString("provider"),
            rs.getString("symbol"),
            rs.getBigDecimal("average_cost"),
            rs.getBigDecimal("basis_quantity") == null ? null : new CostAnchor(
                rs.getBigDecimal("basis_quantity"),
                rs.getBigDecimal("basis_cost"),
                rs.getBigDecimal("trade_quantity"),
                rs.getBigDecimal("trade_quote")),
            rs.getString("updated_at")));
  }

  @Transactional
  public void upsert(
      String provider, String symbol, BigDecimal averageCost, CostAnchor anchor) {
    String cleanProvider = clean(provider);
    String cleanSymbol = clean(symbol);
    String now = JdbcSupport.now();
    jdbc.update("""
        INSERT INTO position_cost_overrides(provider, symbol, average_cost, updated_at)
        VALUES (?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE average_cost = VALUES(average_cost), updated_at = VALUES(updated_at)
        """, cleanProvider, cleanSymbol, averageCost, now);
    saveAnchor(cleanProvider, cleanSymbol, anchor, now);
  }

  public void anchorIfAbsent(String provider, String symbol, CostAnchor anchor) {
    jdbc.update("""
        INSERT IGNORE INTO position_cost_anchors(
          provider, symbol, basis_quantity, basis_cost,
          trade_quantity, trade_quote, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """, clean(provider), clean(symbol), anchor.basisQuantity(), anchor.basisCost(),
        anchor.tradeQuantity(), anchor.tradeQuote(), JdbcSupport.now());
  }

  public void updateAnchor(String provider, String symbol, CostAnchor anchor) {
    saveAnchor(clean(provider), clean(symbol), anchor, JdbcSupport.now());
  }

  @Transactional
  public void delete(String provider, String symbol) {
    String cleanProvider = clean(provider);
    String cleanSymbol = clean(symbol);
    jdbc.update("DELETE FROM position_cost_anchors WHERE provider = ? AND symbol = ?",
        cleanProvider, cleanSymbol);
    jdbc.update("DELETE FROM position_cost_overrides WHERE provider = ? AND symbol = ?",
        cleanProvider, cleanSymbol);
  }

  public static String key(String provider, String symbol) {
    return clean(provider) + ":" + clean(symbol);
  }

  private void saveAnchor(
      String provider, String symbol, CostAnchor anchor, String updatedAt) {
    jdbc.update("""
        INSERT INTO position_cost_anchors(
          provider, symbol, basis_quantity, basis_cost,
          trade_quantity, trade_quote, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          basis_quantity = VALUES(basis_quantity),
          basis_cost = VALUES(basis_cost),
          trade_quantity = VALUES(trade_quantity),
          trade_quote = VALUES(trade_quote),
          updated_at = VALUES(updated_at)
        """, provider, symbol, anchor.basisQuantity(), anchor.basisCost(),
        anchor.tradeQuantity(), anchor.tradeQuote(), updatedAt);
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  public record CostAnchor(
      BigDecimal basisQuantity,
      BigDecimal basisCost,
      BigDecimal tradeQuantity,
      BigDecimal tradeQuote) {
  }

  public record PositionCostOverride(
      String provider,
      String symbol,
      BigDecimal averageCost,
      CostAnchor anchor,
      String updatedAt) {
  }
}
