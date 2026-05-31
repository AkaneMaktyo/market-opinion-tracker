package com.personal.tracker.repository;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ExchangeCredentialRepository {
  private final JdbcTemplate jdbc;
  private final RowMapper<ExchangeCredential> mapper = (rs, rowNum) -> new ExchangeCredential(
      rs.getString("id"),
      rs.getString("provider"),
      rs.getString("account_type"),
      rs.getString("environment"),
      rs.getString("api_key"),
      rs.getString("api_secret"),
      rs.getString("passphrase"),
      rs.getString("product_type"),
      rs.getString("margin_coin"),
      rs.getBoolean("enabled"),
      rs.getString("created_at"),
      rs.getString("updated_at"));

  public ExchangeCredentialRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<ExchangeCredential> findActive(
      String provider,
      String accountType,
      String environment) {
    return jdbc.query("""
        SELECT *
        FROM exchange_credentials
        WHERE provider = ?
          AND account_type = ?
          AND environment = ?
          AND enabled = TRUE
        ORDER BY updated_at DESC
        LIMIT 1
        """, mapper, clean(provider), clean(accountType), clean(environment))
        .stream()
        .findFirst();
  }

  public void upsert(ExchangeCredential credential) {
    jdbc.update("""
        INSERT INTO exchange_credentials(
          id, provider, account_type, environment,
          api_key, api_secret, passphrase,
          product_type, margin_coin, enabled, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          api_key = VALUES(api_key),
          api_secret = VALUES(api_secret),
          passphrase = VALUES(passphrase),
          product_type = VALUES(product_type),
          margin_coin = VALUES(margin_coin),
          enabled = VALUES(enabled),
          updated_at = VALUES(updated_at)
        """,
        value(credential.id(), JdbcSupport.id()),
        clean(credential.provider()),
        clean(credential.accountType()),
        clean(credential.environment()),
        value(credential.apiKey(), ""),
        value(credential.apiSecret(), ""),
        value(credential.passphrase(), ""),
        value(credential.productType(), "USDT-FUTURES"),
        value(credential.marginCoin(), "USDT"),
        credential.enabled(),
        value(credential.createdAt(), JdbcSupport.now()),
        JdbcSupport.now());
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private static String value(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  public record ExchangeCredential(
      String id,
      String provider,
      String accountType,
      String environment,
      String apiKey,
      String apiSecret,
      String passphrase,
      String productType,
      String marginCoin,
      boolean enabled,
      String createdAt,
      String updatedAt) {
  }
}
