package com.personal.tracker.repository;

import com.personal.tracker.domain.Instrument;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class InstrumentRepository {
  private final JdbcTemplate jdbc;
  private final RowMapper<Instrument> mapper = (rs, rowNum) -> new Instrument(
      rs.getString("id"),
      rs.getString("symbol"),
      rs.getString("name"),
      rs.getString("market"),
      rs.getString("sector"),
      rs.getString("created_at"));

  public InstrumentRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Instrument> findAll(String query) {
    if (query == null || query.isBlank()) {
      return jdbc.query("SELECT * FROM instruments ORDER BY symbol", mapper);
    }
    String like = "%" + query.trim().toUpperCase() + "%";
    return jdbc.query("""
        SELECT * FROM instruments
        WHERE symbol LIKE ? OR UPPER(COALESCE(name, '')) LIKE ?
        ORDER BY symbol
        """, mapper, like, like);
  }

  public List<Instrument> findByKol(String kolId, String query) {
    List<Object> args = new java.util.ArrayList<>();
    StringBuilder sql = new StringBuilder("""
        SELECT DISTINCT i.* FROM instruments i
        JOIN opinions o ON o.instrument_id = i.id
        JOIN live_sessions s ON s.id = o.session_id
        WHERE s.kol_id = ?
        """);
    args.add(kolId == null || kolId.isBlank() ? KolRepository.DEFAULT_ID : kolId.trim());
    if (query != null && !query.isBlank()) {
      String like = "%" + query.trim().toUpperCase() + "%";
      sql.append(" AND (i.symbol LIKE ? OR UPPER(COALESCE(i.name, '')) LIKE ?)");
      args.add(like);
      args.add(like);
    }
    sql.append(" ORDER BY i.symbol");
    return jdbc.query(sql.toString(), mapper, args.toArray());
  }

  public Optional<Instrument> findBySymbol(String symbol) {
    List<Instrument> rows = jdbc.query(
        "SELECT * FROM instruments WHERE symbol = ?",
        mapper,
        JdbcSupport.symbol(symbol));
    return rows.stream().findFirst();
  }

  public Instrument saveIfAbsent(String symbol, String name, String market, String sector) {
    String normalized = JdbcSupport.symbol(symbol);
    return findBySymbol(normalized).orElseGet(() -> create(normalized, name, market, sector));
  }

  public Instrument create(String symbol, String name, String market, String sector) {
    Instrument item = new Instrument(
        JdbcSupport.id(),
        JdbcSupport.symbol(symbol),
        name,
        market == null || market.isBlank() ? "US" : market,
        sector,
        JdbcSupport.now());
    jdbc.update("""
        INSERT INTO instruments(id, symbol, name, market, sector, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """, item.id(), item.symbol(), item.name(), item.market(), item.sector(), item.createdAt());
    return item;
  }
}
