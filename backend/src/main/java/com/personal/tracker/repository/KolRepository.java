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
    String safeName = name == null || name.isBlank() ? "未命名KOL" : name.trim();
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
        """, item.id(), item.name(), item.description(), item.createdAt());
    return item;
  }

  private void ensureDefault() {
    jdbc.update("""
        INSERT OR IGNORE INTO kols(id, name, description, created_at)
        VALUES (?, ?, ?, ?)
        """, DEFAULT_ID, "默认KOL", "系统自动创建的默认来源", JdbcSupport.now());
  }
}
