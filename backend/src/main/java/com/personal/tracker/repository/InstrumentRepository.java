package com.personal.tracker.repository;

import com.personal.tracker.domain.Instrument;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class InstrumentRepository {
  private final JdbcTemplate jdbc;
  private final RowMapper<Instrument> mapper = (rs, rowNum) -> new Instrument(
      rs.getString("id"),
      rs.getString("symbol"),
      rs.getString("name"),
      rs.getString("market"),
      rs.getString("sector"),
      rs.getString("group_name"),
      rs.getString("logo_url"),
      rs.getString("market_data_provider"),
      rs.getString("bitget_category"),
      rs.getString("bitget_symbol"),
      rs.getString("bitget_status"),
      rs.getString("bitget_checked_at"),
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

  public List<Instrument> findOpinionHistoryByKol(String kolId, String query) {
    List<Object> args = new java.util.ArrayList<>();
    StringBuilder sql = new StringBuilder("""
        SELECT i.*, MAX(COALESCE(wm.message_time, o.opinion_time, s.created_at)) latest_opinion_at
        FROM instruments i
        JOIN opinions o ON o.instrument_id = i.id
        JOIN live_sessions s ON s.id = o.session_id AND s.kol_id = ?
        LEFT JOIN wxpusher_messages wm ON wm.session_id = s.id
        WHERE 1 = 1
        """);
    args.add(kolId == null || kolId.isBlank() ? KolRepository.DEFAULT_ID : kolId.trim());
    if (query != null && !query.isBlank()) {
      String like = "%" + query.trim().toUpperCase() + "%";
      sql.append(" AND (i.symbol LIKE ? OR UPPER(COALESCE(i.name, '')) LIKE ?)");
      args.add(like);
      args.add(like);
    }
    sql.append(" GROUP BY i.id ORDER BY latest_opinion_at DESC, i.symbol");
    return jdbc.query(sql.toString(), mapper, args.toArray());
  }

  public List<Instrument> applyGroups(String kolId, List<Instrument> items) {
    if (kolId == null || kolId.isBlank() || items.isEmpty()) {
      return items;
    }
    Map<String, String> groups = new HashMap<>();
    jdbc.query("SELECT instrument_id, group_name FROM kol_instrument_groups WHERE kol_id = ?",
        (rs, rowNum) -> groups.put(rs.getString("instrument_id"), rs.getString("group_name")),
        kolId.trim());
    return items.stream().map(item -> withGroup(item, groups.get(item.id()))).toList();
  }
  public List<Instrument> findCurrentByKol(String kolId, String query) {
    List<Object> args = new java.util.ArrayList<>();
    StringBuilder sql = new StringBuilder("""
        SELECT i.*, COALESCE(oa.latest_opinion_at, p.updated_at, p.created_at) latest_activity_at
        FROM instruments i
        JOIN kol_positions p ON p.instrument_id = i.id
        LEFT JOIN (
          SELECT o.instrument_id,
                 MAX(COALESCE(wm.message_time, o.opinion_time, s.created_at)) latest_opinion_at
          FROM opinions o
          JOIN live_sessions s ON s.id = o.session_id
          LEFT JOIN wxpusher_messages wm ON wm.session_id = s.id
          WHERE s.kol_id = ?
          GROUP BY o.instrument_id
        ) oa ON oa.instrument_id = i.id
        WHERE p.kol_id = ? AND p.status = 'ACTIVE'
        """);
    String safeKol = kolId == null || kolId.isBlank() ? KolRepository.DEFAULT_ID : kolId.trim();
    args.add(safeKol);
    args.add(safeKol);
    if (query != null && !query.isBlank()) {
      String like = "%" + query.trim().toUpperCase() + "%";
      sql.append(" AND (i.symbol LIKE ? OR UPPER(COALESCE(i.name, '')) LIKE ?)");
      args.add(like);
      args.add(like);
    }
    sql.append(" ORDER BY latest_activity_at DESC, i.symbol");
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
    return findBySymbol(normalized)
        .map(item -> refresh(item, name, market, sector))
        .orElseGet(() -> create(normalized, name, market, sector));
  }

  public Instrument create(String symbol, String name, String market, String sector) {
    return create(symbol, name, market, sector, null, null);
  }

  public Instrument create(String symbol, String name, String market, String sector, String groupName) {
    return create(symbol, name, market, sector, groupName, null);
  }

  public Instrument create(
      String symbol,
      String name,
      String market,
      String sector,
      String groupName,
      String logoUrl) {
    Instrument item = new Instrument(
        JdbcSupport.id(),
        JdbcSupport.symbol(symbol),
        name,
        market == null || market.isBlank() ? "US" : market,
        sector,
        groupName,
        normalizeLogoUrl(logoUrl),
        null,
        null,
        null,
        null,
        null,
        JdbcSupport.now());
    jdbc.update("""
        INSERT INTO instruments(
          id, symbol, name, market, sector, group_name, logo_url, market_data_provider, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, item.id(), item.symbol(), item.name(), item.market(), item.sector(),
        item.groupName(), item.logoUrl(), item.marketDataProvider(), item.createdAt());
    return item;
  }

  public void saveBitgetMapping(String instrumentId, String category, String symbol) {
    jdbc.update("""
        UPDATE instruments
        SET bitget_category = ?, bitget_symbol = ?, bitget_status = ?, bitget_checked_at = ?
        WHERE id = ?
        """, category, symbol, "MAPPED", JdbcSupport.now(), instrumentId);
  }

  public void markBitgetUnavailable(String instrumentId) {
    jdbc.update("""
        UPDATE instruments
        SET bitget_category = NULL, bitget_symbol = NULL,
            bitget_status = ?, bitget_checked_at = ?
        WHERE id = ?
        """, "UNAVAILABLE", JdbcSupport.now(), instrumentId);
  }

  private Instrument refresh(Instrument item, String name, String market, String sector) {
    String nextName = better(item.name(), name, item.symbol());
    String nextMarket = better(item.market(), market, "");
    String nextSector = better(item.sector(), sector, "");
    if (same(item.name(), nextName) && same(item.market(), nextMarket)
        && same(item.sector(), nextSector)) {
      return item;
    }
    jdbc.update("""
        UPDATE instruments
        SET name = ?, market = ?, sector = ?
        WHERE id = ?
        """, nextName, nextMarket, nextSector, item.id());
    return new Instrument(
        item.id(),
        item.symbol(),
        nextName,
        nextMarket,
        nextSector,
        item.groupName(),
        item.logoUrl(),
        item.marketDataProvider(),
        item.bitgetCategory(),
        item.bitgetSymbol(),
        item.bitgetStatus(),
        item.bitgetCheckedAt(),
        item.createdAt());
  }

  @Transactional
  public Optional<Instrument> rename(
      String instrumentId,
      String newSymbol,
      String newName,
      String logoUrl) {
    String normalized = JdbcSupport.symbol(newSymbol);
    findBySymbol(normalized).ifPresent(existing -> {
      if (!existing.id().equals(instrumentId)) {
        throw new IllegalArgumentException("\u54c1\u79cd\u5df2\u5b58\u5728: " + normalized);
      }
    });
    Instrument current = findById(instrumentId).orElse(null);
    if (current == null) {
      return Optional.empty();
    }
    String nextName = newName == null || newName.isBlank() ? current.name() : newName.trim();
    jdbc.update("""
        UPDATE instruments
        SET symbol = ?, name = ?, logo_url = ?
        WHERE id = ?
        """, normalized, nextName, normalizeLogoUrl(logoUrl), instrumentId);
    return findById(instrumentId);
  }

  @Transactional
  public void merge(String sourceId, String targetId) {
    if (sourceId.equals(targetId)) {
      throw new IllegalArgumentException("\u4e0d\u80fd\u5f52\u5e76\u5230\u81ea\u8eab");
    }
    jdbc.update("""
        INSERT IGNORE INTO kol_instrument_groups(kol_id, instrument_id, group_name)
        SELECT kol_id, ?, group_name FROM kol_instrument_groups WHERE instrument_id = ?
        """, targetId, sourceId);
    jdbc.update("DELETE FROM kol_instrument_groups WHERE instrument_id = ?", sourceId);
    jdbc.update("UPDATE opinions SET instrument_id = ? WHERE instrument_id = ?", targetId, sourceId);
    jdbc.update("""
        DELETE mb1 FROM market_bars mb1
        INNER JOIN market_bars mb2 ON mb2.instrument_id = ? AND mb2.timeframe = mb1.timeframe AND mb2.bar_time = mb1.bar_time
        WHERE mb1.instrument_id = ?
        """, targetId, sourceId);
    jdbc.update("UPDATE market_bars SET instrument_id = ? WHERE instrument_id = ?", targetId, sourceId);
    jdbc.update("DELETE FROM instruments WHERE id = ?", sourceId);
  }

  @Transactional
  public boolean delete(String instrumentId) {
    if (findById(instrumentId).isEmpty()) {
      return false;
    }
    deleteResonanceData(instrumentId);
    deleteOpinionData(instrumentId);
    jdbc.update("DELETE FROM kol_instrument_groups WHERE instrument_id = ?", instrumentId);
    jdbc.update("DELETE FROM kol_positions WHERE instrument_id = ?", instrumentId);
    jdbc.update("DELETE FROM market_bars WHERE instrument_id = ?", instrumentId);
    return jdbc.update("DELETE FROM instruments WHERE id = ?", instrumentId) > 0;
  }

  public void updateGroup(String kolId, String instrumentId, String groupName) {
    String nextGroup = groupName == null || groupName.isBlank() ? null : groupName.trim();
    String safeKol = kolId == null || kolId.isBlank() ? KolRepository.DEFAULT_ID : kolId.trim();
    if (nextGroup == null) {
      jdbc.update("DELETE FROM kol_instrument_groups WHERE kol_id = ? AND instrument_id = ?", safeKol, instrumentId);
      return;
    }
    jdbc.update("""
        INSERT INTO kol_instrument_groups(kol_id, instrument_id, group_name)
        VALUES (?, ?, ?)
        ON DUPLICATE KEY UPDATE group_name = VALUES(group_name)
        """, safeKol, instrumentId, nextGroup);
  }

  public void updateMarketDataProvider(String instrumentId, String provider) {
    String nextProvider = provider == null || provider.isBlank() || "auto".equalsIgnoreCase(provider)
        ? null
        : provider.trim().toLowerCase();
    jdbc.update(
        "UPDATE instruments SET market_data_provider = ? WHERE id = ?",
        nextProvider,
        instrumentId);
  }

  public Optional<Instrument> findById(String instrumentId) {
    return jdbc.query("SELECT * FROM instruments WHERE id = ?", mapper, instrumentId)
        .stream()
        .findFirst();
  }

  public List<String> findAllGroups(String kolId) {
    return jdbc.queryForList(
        "SELECT DISTINCT group_name FROM kol_instrument_groups WHERE kol_id = ? ORDER BY group_name",
        String.class, kolId == null || kolId.isBlank() ? KolRepository.DEFAULT_ID : kolId.trim());
  }

  private static Instrument withGroup(Instrument item, String groupName) {
    return new Instrument(item.id(), item.symbol(), item.name(), item.market(), item.sector(), groupName,
        item.logoUrl(), item.marketDataProvider(), item.bitgetCategory(), item.bitgetSymbol(),
        item.bitgetStatus(), item.bitgetCheckedAt(), item.createdAt());
  }

  private void deleteResonanceData(String instrumentId) {
    jdbc.update("""
        DELETE a FROM resonance_alerts a
        JOIN resonance_clusters c ON c.id = a.cluster_id
        WHERE c.instrument_id = ?
        """, instrumentId);
    jdbc.update("""
        DELETE i FROM resonance_cluster_items i
        JOIN resonance_clusters c ON c.id = i.cluster_id
        WHERE c.instrument_id = ?
        """, instrumentId);
    jdbc.update("DELETE FROM resonance_clusters WHERE instrument_id = ?", instrumentId);
  }

  private void deleteOpinionData(String instrumentId) {
    jdbc.update("""
        DELETE r FROM reviews r
        JOIN opinions o ON o.id = r.opinion_id
        WHERE o.instrument_id = ?
        """, instrumentId);
    jdbc.update("""
        DELETE p FROM price_levels p
        JOIN opinions o ON o.id = p.opinion_id
        WHERE o.instrument_id = ?
        """, instrumentId);
    jdbc.update("DELETE FROM opinions WHERE instrument_id = ?", instrumentId);
  }

  private static String better(String current, String candidate, String weakValue) {
    if (candidate == null || candidate.isBlank()) {
      return current;
    }
    if (current == null || current.isBlank() || current.equalsIgnoreCase(weakValue)) {
      return candidate.trim();
    }
    return current;
  }

  private static String normalizeLogoUrl(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static boolean same(String left, String right) {
    return (left == null ? "" : left).equals(right == null ? "" : right);
  }

}
