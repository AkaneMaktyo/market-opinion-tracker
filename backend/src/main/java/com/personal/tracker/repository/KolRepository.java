package com.personal.tracker.repository;

import com.personal.tracker.domain.Kol;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class KolRepository {
  public static final String DEFAULT_ID = "default";
  private static final String DEFAULT_NAME = "è‡ªé€‰è¡¨";
  private static final String DEFAULT_DESCRIPTION = "æ‰‹åŠ¨ç»´æŠ¤çš„è‡ªé€‰æ ‡çš„åˆ—è¡¨";
  private static final String UNNAMED = "æœªå‘½åKOL";

  private final JdbcTemplate jdbc;
  private final RowMapper<Kol> mapper = (rs, rowNum) -> new Kol(
      rs.getString("id"),
      rs.getString("name"),
      rs.getString("description"),
      rs.getString("created_at"));

  public KolRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Kol> findAll() {
    ensureDefault();
    return jdbc.query("SELECT * FROM kols ORDER BY created_at, name", mapper);
  }

  public Optional<Kol> findById(String id) {
    ensureDefault();
    return jdbc.query("SELECT * FROM kols WHERE id = ?", mapper, normalize(id))
        .stream()
        .findFirst();
  }

  public Kol save(String name, String description) {
    ensureDefault();
    String safeName = safeName(name);
    return findByName(safeName).orElseGet(() -> create(safeName, description));
  }

  public String normalize(String id) {
    return id == null || id.isBlank() ? DEFAULT_ID : id.trim();
  }

  private Optional<Kol> findByName(String name) {
    return jdbc.query("SELECT * FROM kols WHERE name = ?", mapper, name)
        .stream()
        .findFirst();
  }

  private Kol create(String name, String description) {
    Kol item = new Kol(JdbcSupport.id(), name, description, JdbcSupport.now());
    jdbc.update("""
        INSERT INTO kols(id, name, description, created_at)
        VALUES (?, ?, ?, ?)
#];ëİ­¢G§²ÚîÆ­yÓ¢6†'B×7–âã2Æ–æV"–æf–æ—FS²fÆWƒ¢WFó²Ğ ¤¶W–g&ÖW2&6¶f–ÆÂ×VÇ6R°¢R²&6¶w&÷VæB×÷6—F–öã¢RSS²Ğ¢R²&6¶w&÷VæB×÷6—F–öã¢##RSS²Ğ§Ğ