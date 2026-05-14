package com.personal.tracker.repository;

import com.personal.tracker.domain.MarketBar;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MarketBarRepository {
  private final JdbcTemplate jdbc;
  private final RowMapper<MarketBar> mapper = (rs, rowNum) -> new MarketBar(
      rs.getString("id"),
      rs.getString("instrument_id"),
      rs.getString("timeframe"),
      rs.getString("bar_time"),
      rs.getBigDecimal("open"),
      rs.getBigDecimal("high"),
      rs.getBigDecimal("low"),
      rs.getBigDecimal("close"),
      rs.getBigDecimal("volume"));

  public MarketBarRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<MarketBar> findBars(String instrumentId, String timeframe) {
    return jdbc.query("""
        SELECT * FROM market_bars
        WHERE instrument_id = ? AND timeframe = ?
        ORDER BY bar_time
        """, mapper, instrumentId, timeframe);
  }

  public int count(String instrumentId, String timeframe) {
    Integer value = jdbc.queryForObject("""
        SELECT COUNT(*) FROM market_bars
        WHERE instrument_id = ? AND timeframe = ?
        """, Integer.class, instrumentId, timeframe);
    return value == null ? 0 : value;
  }

  public void saveAll(List<MarketBar> bars) {
    jdbc.batchUpdate("""
        INSERT OR IGNORE INTO market_bars(
          id, instrument_id, timeframe, bar_time, open, high, low, close, volume
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, bars, 200, (ps, item) -> {
      ps.setString(1, item.id());
      ps.setString(2, item.instrumentId());
      ps.setString(3, item.timeframe());
      ps.setString(4, item.barTime());
      ps.setBigDecimal(5, item.open());
      ps.setBigDecimal(6, item.high());
      ps.setBigDecimal(7, item.low());
      ps.setBigDecimal(8, item.close());
      ps.setBigDecimal(9, item.volume());
    });
  }
}
