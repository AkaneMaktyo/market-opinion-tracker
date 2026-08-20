package com.personal.tracker.repository.celebrity;

import com.personal.tracker.domain.celebrity.CelebrityFiling;
import com.personal.tracker.domain.celebrity.CelebrityHolding;
import com.personal.tracker.domain.celebrity.CelebrityInvestor;
import com.personal.tracker.domain.celebrity.CelebritySyncStatus;
import com.personal.tracker.domain.celebrity.alerts.CelebrityAlertSettings;
import com.personal.tracker.repository.JdbcSupport;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CelebrityPortfolioRepository {
  private static final String SYNC_ID = "main";
  private final JdbcTemplate jdbc;
  private final RowMapper<CelebrityInvestor> investorMapper = (rs, rowNum) -> new CelebrityInvestor(
      rs.getString("id"), rs.getString("slug"), rs.getString("display_name"),
      rs.getString("manager_name"), rs.getString("source_type"), rs.getString("cik"),
      rs.getString("source_url"), rs.getBoolean("enabled"), rs.getString("created_at"),
      rs.getString("updated_at"));
  private final RowMapper<CelebrityFiling> filingMapper = (rs, rowNum) -> new CelebrityFiling(
      rs.getString("id"), rs.getString("investor_id"), rs.getString("source_type"),
      rs.getString("external_id"), rs.getString("form_type"), rs.getString("report_date"),
      rs.getString("filed_at"), rs.getString("source_url"), rs.getBoolean("is_amendment"),
      rs.getString("fetched_at"));
  private final RowMapper<CelebrityHolding> holdingMapper = (rs, rowNum) -> new CelebrityHolding(
      rs.getString("id"), rs.getString("filing_id"), rs.getString("investor_id"),
      rs.getString("holding_key"), rs.getString("symbol"), rs.getString("symbol_confidence"),
      rs.getString("cusip"),
      rs.getString("issuer_name"), rs.getString("title_class"), rs.getString("put_call"),
      rs.getBigDecimal("shares"), rs.getBigDecimal("reported_value"),
      rs.getBigDecimal("reported_weight"), rs.getBigDecimal("reported_unit_value"));

  public CelebrityPortfolioRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @PostConstruct
  public void initialize() {
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS celebrity_investors (
          id VARCHAR(64) PRIMARY KEY,
          slug VARCHAR(80) NOT NULL,
          display_name VARCHAR(160) NOT NULL,
          manager_name VARCHAR(255) NOT NULL,
          source_type VARCHAR(32) NOT NULL,
          cik VARCHAR(16),
          source_url VARCHAR(1000) NOT NULL,
          enabled BOOLEAN NOT NULL DEFAULT TRUE,
          created_at VARCHAR(64) NOT NULL,
          updated_at VARCHAR(64) NOT NULL,
          UNIQUE KEY uq_celebrity_investor_slug(slug),
          INDEX idx_celebrity_investor_source(enabled, source_type)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """);
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS celebrity_filings (
          id VARCHAR(64) PRIMARY KEY,
          investor_id VARCHAR(64) NOT NULL,
          source_type VARCHAR(32) NOT NULL,
          external_id VARCHAR(160) NOT NULL,
          form_type VARCHAR(32) NOT NULL,
          report_date VARCHAR(32) NOT NULL,
          filed_at VARCHAR(64),
          source_url VARCHAR(1000) NOT NULL,
          is_amendment BOOLEAN NOT NULL DEFAULT FALSE,
          fetched_at VARCHAR(64) NOT NULL,
          UNIQUE KEY uq_celebrity_filing_external(investor_id, external_id),
          INDEX idx_celebrity_filing_latest(investor_id, report_date, filed_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """);
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS celebrity_holdings (
          id VARCHAR(64) PRIMARY KEY,
          filing_id VARCHAR(64) NOT NULL,
          investor_id VARCHAR(64) NOT NULL,
          holding_key VARCHAR(360) NOT NULL,
          symbol VARCHAR(32),
          cusip VARCHAR(32),
          issuer_name VARCHAR(500) NOT NULL,
          title_class VARCHAR(160),
          put_call VARCHAR(16),
          shares DECIMAL(28, 6) NOT NULL,
          reported_value DECIMAL(30, 2) NOT NULL,
          reported_weight DECIMAL(14, 8),
          reported_unit_value DECIMAL(24, 8),
          UNIQUE KEY uq_celebrity_holding(filing_id, holding_key),
          INDEX idx_celebrity_holding_filing(filing_id, reported_value),
          INDEX idx_celebrity_holding_cusip(cusip)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """);
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS celebrity_symbol_mappings (
          cusip VARCHAR(32) PRIMARY KEY,
          symbol VARCHAR(32) NOT NULL,
          source VARCHAR(32) NOT NULL,
          confidence VARCHAR(16) NOT NULL,
          updated_at VARCHAR(64) NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """);
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS celebrity_sync_state (
          id VARCHAR(32) PRIMARY KEY,
          running BOOLEAN NOT NULL DEFAULT FALSE,
          last_started_at VARCHAR(64),
          last_completed_at VARCHAR(64),
          last_outcome VARCHAR(32),
          last_error TEXT,
          investors_synced INT NOT NULL DEFAULT 0,
          filings_synced INT NOT NULL DEFAULT 0,
          holdings_synced INT NOT NULL DEFAULT 0,
          updated_at VARCHAR(64) NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """);
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS celebrity_alert_settings (
          id VARCHAR(32) PRIMARY KEY,
          enabled BOOLEAN NOT NULL DEFAULT FALSE,
          investor_slugs TEXT NOT NULL,
          minimum_reported_weight DECIMAL(14, 8) NOT NULL DEFAULT 0.02,
          updated_at VARCHAR(64) NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """);
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS celebrity_disclosure_alerts (
          id VARCHAR(64) PRIMARY KEY,
          investor_id VARCHAR(64) NOT NULL,
          filing_id VARCHAR(64) NOT NULL,
          holding_key VARCHAR(360) NOT NULL,
          action VARCHAR(16) NOT NULL,
          status VARCHAR(32) NOT NULL,
          error_message TEXT,
          sent_at VARCHAR(64),
          created_at VARCHAR(64) NOT NULL,
          updated_at VARCHAR(64) NOT NULL,
          UNIQUE KEY uq_celebrity_alert_event(investor_id, filing_id, holding_key, action),
          INDEX idx_celebrity_alert_status(status, updated_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """);
    seedInvestors();
    ensureAlertSettings();
  }

  public List<CelebrityInvestor> findAllInvestors() {
    return jdbc.query("SELECT * FROM celebrity_investors ORDER BY source_type, display_name", investorMapper);
  }

  public List<CelebrityInvestor> findEnabledInvestors() {
    return jdbc.query("SELECT * FROM celebrity_investors WHERE enabled = TRUE ORDER BY source_type, display_name",
        investorMapper);
  }

  public Optional<CelebrityInvestor> findInvestor(String slug) {
    return jdbc.query("SELECT * FROM celebrity_investors WHERE slug = ?", investorMapper, slug).stream()
        .findFirst();
  }

  public CelebrityFiling saveFiling(CelebrityFiling filing) {
    String id = findFiling(filing.investorId(), filing.externalId())
        .map(CelebrityFiling::id)
        .orElseGet(JdbcSupport::id);
    jdbc.update("""
        INSERT INTO celebrity_filings(
          id, investor_id, source_type, external_id, form_type, report_date, filed_at,
          source_url, is_amendment, fetched_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          form_type = VALUES(form_type), report_date = VALUES(report_date), filed_at = VALUES(filed_at),
          source_url = VALUES(source_url), is_amendment = VALUES(is_amendment), fetched_at = VALUES(fetched_at)
        """, id, filing.investorId(), filing.sourceType(), filing.externalId(), filing.formType(),
        filing.reportDate(), filing.filedAt(), filing.sourceUrl(), filing.amendment(), filing.fetchedAt());
    return findFiling(filing.investorId(), filing.externalId()).orElseThrow();
  }

  public void replaceHoldings(CelebrityFiling filing, List<CelebrityHolding> holdings) {
    jdbc.update("DELETE FROM celebrity_holdings WHERE filing_id = ?", filing.id());
    if (holdings == null || holdings.isEmpty()) {
      return;
    }
    jdbc.batchUpdate("""
        INSERT INTO celebrity_holdings(
          id, filing_id, investor_id, holding_key, symbol, cusip, issuer_name, title_class, put_call,
          shares, reported_value, reported_weight, reported_unit_value
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, holdings, 200, (statement, holding) -> {
      statement.setString(1, holding.id());
      statement.setString(2, filing.id());
      statement.setString(3, filing.investorId());
      statement.setString(4, holding.holdingKey());
      statement.setString(5, blankToNull(holding.symbol()));
      statement.setString(6, blankToNull(holding.cusip()));
      statement.setString(7, holding.issuerName());
      statement.setString(8, blankToNull(holding.titleClass()));
      statement.setString(9, blankToNull(holding.putCall()));
      statement.setBigDecimal(10, holding.shares());
      statement.setBigDecimal(11, holding.reportedValue());
      statement.setBigDecimal(12, holding.reportedWeight());
      statement.setBigDecimal(13, holding.reportedUnitValue());
    });
  }

  public Optional<CelebrityFiling> latestFiling(String investorId) {
    return jdbc.query("""
        SELECT * FROM celebrity_filings
        WHERE investor_id = ?
        ORDER BY report_date DESC, is_amendment DESC, filed_at DESC, fetched_at DESC
        LIMIT 1
        """, filingMapper, investorId).stream().findFirst();
  }

  public List<CelebrityFiling> recentFilings(String investorId, int limit) {
    return jdbc.query("""
        SELECT * FROM celebrity_filings
        WHERE investor_id = ?
        ORDER BY report_date DESC, is_amendment DESC, filed_at DESC, fetched_at DESC
        LIMIT ?
        """, filingMapper, investorId, Math.max(1, Math.min(limit, 16)));
  }

  public List<CelebrityHolding> holdingsForFiling(String filingId) {
    return jdbc.query("""
        SELECT h.id, h.filing_id, h.investor_id, h.holding_key,
               COALESCE(NULLIF(h.symbol, ''), m.symbol) symbol,
               CASE WHEN h.symbol IS NOT NULL AND h.symbol <> '' THEN 'HIGH'
                    ELSE COALESCE(m.confidence, 'UNKNOWN') END symbol_confidence,
               h.cusip, h.issuer_name, h.title_class, h.put_call, h.shares,
               h.reported_value, h.reported_weight, h.reported_unit_value
        FROM celebrity_holdings h
        LEFT JOIN celebrity_symbol_mappings m ON m.cusip = h.cusip
        WHERE h.filing_id = ?
        ORDER BY h.reported_value DESC, h.issuer_name
        """, holdingMapper, filingId);
  }

  public Map<String, List<CelebrityHolding>> holdingHistories(String investorId, int filingLimit) {
    List<CelebrityFiling> filings = new ArrayList<>(recentFilings(investorId, filingLimit));
    java.util.Collections.reverse(filings);
    Map<String, List<CelebrityHolding>> result = new LinkedHashMap<>();
    for (CelebrityFiling filing : filings) {
      for (CelebrityHolding holding : holdingsForFiling(filing.id())) {
        result.computeIfAbsent(holding.holdingKey(), ignored -> new ArrayList<>()).add(holding);
      }
    }
    return result;
  }

  public List<CelebrityHolding> latestUnmappedHoldings(int limit) {
    return jdbc.query("""
        SELECT h.*, 'UNKNOWN' symbol_confidence
        FROM celebrity_holdings h
        JOIN celebrity_filings f ON f.id = h.filing_id
        LEFT JOIN celebrity_symbol_mappings m ON m.cusip = h.cusip
        WHERE (h.symbol IS NULL OR h.symbol = '')
          AND h.cusip IS NOT NULL AND h.cusip <> ''
          AND m.cusip IS NULL
          AND h.put_call IS NULL
          AND f.id = (
            SELECT f2.id FROM celebrity_filings f2
            WHERE f2.investor_id = f.investor_id
            ORDER BY f2.report_date DESC, f2.is_amendment DESC, f2.filed_at DESC, f2.fetched_at DESC
            LIMIT 1
          )
        ORDER BY h.reported_value DESC
        LIMIT ?
        """, holdingMapper, Math.max(0, Math.min(limit, 100)));
  }

  public void saveSymbolMapping(String cusip, String symbol, String source, String confidence) {
    if (cusip == null || cusip.isBlank() || symbol == null || symbol.isBlank()) {
      return;
    }
    jdbc.update("""
        INSERT INTO celebrity_symbol_mappings(cusip, symbol, source, confidence, updated_at)
        VALUES (?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE symbol = VALUES(symbol), source = VALUES(source),
          confidence = VALUES(confidence), updated_at = VALUES(updated_at)
        """, cusip.trim().toUpperCase(), symbol.trim().toUpperCase(), source, confidence, JdbcSupport.now());
  }

  public CelebritySyncStatus syncStatus(boolean enabled) {
    return jdbc.query("""
        SELECT * FROM celebrity_sync_state WHERE id = ?
        """, (rs, rowNum) -> new CelebritySyncStatus(
        rs.getBoolean("running"), enabled, rs.getString("last_started_at"),
        rs.getString("last_completed_at"), rs.getString("last_outcome"), rs.getString("last_error"),
        rs.getInt("investors_synced"), rs.getInt("filings_synced"), rs.getInt("holdings_synced")), SYNC_ID)
        .stream().findFirst().orElse(new CelebritySyncStatus(false, enabled, null, null, "PENDING", null, 0, 0, 0));
  }

  public void markSyncStarted() {
    jdbc.update("""
        INSERT INTO celebrity_sync_state(id, running, last_started_at, last_outcome, updated_at)
        VALUES (?, TRUE, ?, 'RUNNING', ?)
        ON DUPLICATE KEY UPDATE running = TRUE, last_started_at = VALUES(last_started_at),
          last_outcome = 'RUNNING', last_error = NULL, updated_at = VALUES(updated_at)
        """, SYNC_ID, JdbcSupport.now(), JdbcSupport.now());
  }

  public void markSyncFinished(
      String outcome,
      String error,
      int investorsSynced,
      int filingsSynced,
      int holdingsSynced) {
    jdbc.update("""
        INSERT INTO celebrity_sync_state(
          id, running, last_completed_at, last_outcome, last_error,
          investors_synced, filings_synced, holdings_synced, updated_at
        ) VALUES (?, FALSE, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE running = FALSE, last_completed_at = VALUES(last_completed_at),
          last_outcome = VALUES(last_outcome), last_error = VALUES(last_error),
          investors_synced = VALUES(investors_synced), filings_synced = VALUES(filings_synced),
          holdings_synced = VALUES(holdings_synced), updated_at = VALUES(updated_at)
        """, SYNC_ID, JdbcSupport.now(), outcome, blankToNull(error), investorsSynced,
        filingsSynced, holdingsSynced, JdbcSupport.now());
  }

  public CelebrityAlertSettings alertSettings() {
    return jdbc.query("""
        SELECT enabled, investor_slugs, minimum_reported_weight, updated_at
        FROM celebrity_alert_settings WHERE id = ?
        """, (rs, rowNum) -> new CelebrityAlertSettings(
        rs.getBoolean("enabled"), splitSlugs(rs.getString("investor_slugs")),
        rs.getBigDecimal("minimum_reported_weight"), rs.getString("updated_at")), SYNC_ID)
        .stream().findFirst().orElseGet(() -> new CelebrityAlertSettings(false, List.of(),
            new BigDecimal("0.02"), null));
  }

  public CelebrityAlertSettings saveAlertSettings(
      boolean enabled,
      List<String> investorSlugs,
      BigDecimal minimumReportedWeight) {
    List<String> validSlugs = investorSlugs == null ? List.of() : investorSlugs.stream()
        .filter(item -> item != null && !item.isBlank()).map(String::trim).distinct().toList();
    BigDecimal weight = minimumReportedWeight == null ? new BigDecimal("0.02") : minimumReportedWeight;
    if (weight.signum() < 0 || weight.compareTo(BigDecimal.ONE) > 0) {
      throw new IllegalArgumentException("提醒最小持仓占比必须在 0% 到 100% 之间");
    }
    String now = JdbcSupport.now();
    jdbc.update("""
        INSERT INTO celebrity_alert_settings(
          id, enabled, investor_slugs, minimum_reported_weight, updated_at
        ) VALUES (?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), investor_slugs = VALUES(investor_slugs),
          minimum_reported_weight = VALUES(minimum_reported_weight), updated_at = VALUES(updated_at)
        """, SYNC_ID, enabled, String.join(",", validSlugs), weight, now);
    return alertSettings();
  }

  public Optional<String> claimDisclosureAlert(
      String investorId,
      String filingId,
      String holdingKey,
      String action) {
    String id = JdbcSupport.id();
    int inserted = jdbc.update("""
        INSERT IGNORE INTO celebrity_disclosure_alerts(
          id, investor_id, filing_id, holding_key, action, status, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?)
        """, id, investorId, filingId, holdingKey, action, JdbcSupport.now(), JdbcSupport.now());
    return inserted == 1 ? Optional.of(id) : Optional.empty();
  }

  public void finishDisclosureAlert(String alertId, String status, String error) {
    jdbc.update("""
        UPDATE celebrity_disclosure_alerts
        SET status = ?, error_message = ?, sent_at = CASE WHEN ? = 'SENT' THEN ? ELSE sent_at END,
            updated_at = ?
        WHERE id = ?
        """, status, blankToNull(error), status, JdbcSupport.now(), JdbcSupport.now(), alertId);
  }

  private Optional<CelebrityFiling> findFiling(String investorId, String externalId) {
    return jdbc.query("""
        SELECT * FROM celebrity_filings WHERE investor_id = ? AND external_id = ?
        """, filingMapper, investorId, externalId).stream().findFirst();
  }

  private void seedInvestors() {
    saveSeed("druckenmiller", "druckenmiller", "德鲁肯米勒", "Duquesne Family Office LLC",
        "SEC_13F", "0001536411", "https://www.sec.gov/edgar/browse/?CIK=0001536411");
    saveSeed("buffett", "buffett", "巴菲特", "Berkshire Hathaway Inc.",
        "SEC_13F", "0001067983", "https://www.sec.gov/edgar/browse/?CIK=0001067983");
    saveSeed("burry", "burry", "迈克尔·伯里", "Scion Asset Management, LLC",
        "SEC_13F", "0001649339", "https://www.sec.gov/edgar/browse/?CIK=0001649339");
    saveSeed("cathie-wood", "cathie-wood", "木头姐", "ARK Invest · ARKK",
        "ARK_DAILY", null, "https://www.ark-funds.com/etfs/arkk");
  }

  private void ensureAlertSettings() {
    jdbc.update("""
        INSERT IGNORE INTO celebrity_alert_settings(
          id, enabled, investor_slugs, minimum_reported_weight, updated_at
        ) VALUES (?, FALSE, '', 0.02, ?)
        """, SYNC_ID, JdbcSupport.now());
  }

  private static List<String> splitSlugs(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(value.split(","))
        .map(String::trim).filter(item -> !item.isBlank()).distinct().toList();
  }

  private void saveSeed(
      String id,
      String slug,
      String displayName,
      String managerName,
      String sourceType,
      String cik,
      String sourceUrl) {
    String now = JdbcSupport.now();
    jdbc.update("""
        INSERT IGNORE INTO celebrity_investors(
          id, slug, display_name, manager_name, source_type, cik, source_url, enabled, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
        """, id, slug, displayName, managerName, sourceType, cik, sourceUrl, now, now);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
