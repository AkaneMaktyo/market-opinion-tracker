package com.personal.tracker.repository.trading;

import com.personal.tracker.repository.JdbcSupport;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
  }

  public List<PositionCostOverride> findAll() {
    return jdbc.query("SELECT * FROM position_cost_overrides", (rs, rowNum) ->
        new PositionCostOverride(
            rs.getString("provider"),
            rs.getString("symbol"),
            rs.getBigDecimal("average_cost"),
            rs.getString("updated_at")));
  }

  public void upsert(String provider, String symbol, BigDecimal averageCost) {
    jdbc.update("""
        INSERT INTO position_cost_overrides(provider, symbol, average_cost, updated_at)
        VALUES (?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE average_cost = VALUES(average_cost), updated_at = VALUES(updated_at)
        """, clean(provider), clean(symbol), averageCost, JdbcSupport.now());
  }

  public void delete(String provider, String symbol) {
    jdbc.update("DELETE FROM position_cost_overrides WHERE provider = ? AND symbol = ?",
        clean(provider), clean(symbol));
  }

  public static String key(String provider, String symbol) {
    return clean(provider) + ":" + clean(symbol);
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  public record PositionCostOverride(
      String provider, String symbol, BigDecimal averageCost, String updatedAt) {
  }
}
