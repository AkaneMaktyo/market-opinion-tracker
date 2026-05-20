package com.personal.tracker.repository;

import com.personal.tracker.domain.MarketBar;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

  public BarCoverage coverage(String instrumentId, String timeframe) {
    return jdbc.queryForObject("""
        SELECT COUNT(*) bars_count, MIN(bar_time) first_bar, MAX(bar_time) last_bar
        FROM market_bars
        WHERE instrument_id = ? AND timeframe = ?
        """, (rs, rowNum) -> new BarCoverage(
        rs.getInt("bars_count"),
        rs.getString("first_bar"),
        rs.getString("last_bar")), instrumentId, timeframe);
  }

  public void saveAll(List<MarketBar> bars) {
    if (bars == null || bars.isEmpty()) {
      return;
    }
    jdbc.batchUpdate("""
        INSERT INTO market_bars(
          id, instrument_id, timeframe, bar_time, open, high, low, close, volume
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          open = VALUES(open),
          high = VALUES(high),
          low = VALUES(low),
          close = VALUES(close),
          volume = VALUES(volume)
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

  public Map<String, DailySnapshot> latestDailySnapshots(
      List<String> instrumentIds,
      String currentDate) {
    if (instrumentIds == null || instrumentIds.isEmpty()) {
      return Map.of();
    }
    String placeholders = instrumentIds.stream().map(item -> "?").collect(Collectors.joining(", "));
    String sql = """
        SELECT instrument_id,
               MAX(CASE WHEN rn = 1 THEN bar_time END) latest_bar_time,
               MAX(CASE WHEN rn = 1 THEN close END) latest_close,
               MAX(CASE WHEN rn = 2 THEN close END) previous_close
        FROM (
          SELECT instrument_id, bar_time, close,
                 ROW_NUMBER() OVER (
                   PARTITION BY instrument_id
                   ORDER BY bar_time DESC
                 ) rn
          FROM market_bars
          WHERE timeframe = '1D'
            AND bar_time <= ?
            AND instrument_id IN (%s)
        ) ranked
        WHERE rn <= 2
        GROUP BY instrument_id
        """.formatted(placeholders);
    List<Object> args = new java.util.ArrayList<>();
    args.add(currentDate);
    args.addAll(instrumentIds);
    return jdbc.query(sql, rs -> {
      Map<String, DailySnapshot> items = new java.util.HashMap<>();
      while (rs.next()) {
        items.put(rs.getString("instrument_id"), new DailySnapshot(
            rs.getString("latest_bar_time"),
            rs.getBigDecimal("latest_close"),
            rs.getBigDecimal("previous_close")));
      }
      return items;
    }, args.toArray());
  }

  public record BarCoverage(int count, String firstBarTime, String lastBarTime) {
  }

  public record DailySnapshot(
      String barTime,
      BigDecimal close,
      BigDecimal previousClose) {
  }
}
