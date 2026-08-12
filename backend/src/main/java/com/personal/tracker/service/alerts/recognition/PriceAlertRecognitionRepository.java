package com.personal.tracker.service.alerts.recognition;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.service.alerts.recognition.PriceAlertRecognitionModels.Candidate;
import com.personal.tracker.service.alerts.recognition.PriceAlertRecognitionModels.Result;
import com.personal.tracker.service.alerts.recognition.PriceAlertRecognitionModels.Summary;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PriceAlertRecognitionRepository {
  private static final TypeReference<List<Candidate>> CANDIDATES = new TypeReference<>() { };
  private static final TypeReference<List<String>> WARNINGS = new TypeReference<>() { };
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public PriceAlertRecognitionRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @PostConstruct
  public void initialize() {
    jdbc.execute("""
        CREATE TABLE IF NOT EXISTS message_price_alert_recognitions (
          id VARCHAR(64) PRIMARY KEY,
          message_id VARCHAR(64) NOT NULL,
          status VARCHAR(32) NOT NULL,
          ocr_text LONGTEXT,
          candidates_json LONGTEXT,
          warnings_json TEXT,
          error_message TEXT,
          created_at VARCHAR(64) NOT NULL,
          updated_at VARCHAR(64) NOT NULL,
          UNIQUE KEY uq_price_alert_recognition_message(message_id),
          INDEX idx_price_alert_recognition_status(status, updated_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """);
  }

  public Optional<Result> find(String messageId) {
    return jdbc.query("""
        SELECT * FROM message_price_alert_recognitions WHERE message_id = ?
        """, (rs, rowNum) -> new Result(
        rs.getString("id"), rs.getString("message_id"), rs.getString("status"),
        read(rs.getString("candidates_json"), CANDIDATES),
        read(rs.getString("warnings_json"), WARNINGS), rs.getString("error_message"),
        rs.getString("updated_at")), messageId).stream().findFirst();
  }

  public Summary summary(String messageId) {
    return find(messageId)
        .map(item -> new Summary(item.status(), item.candidates().size(), item.recognitionId()))
        .orElse(new Summary("NOT_STARTED", 0, ""));
  }

  public boolean claim(String messageId) {
    String now = JdbcSupport.now();
    try {
      jdbc.update("""
          INSERT INTO message_price_alert_recognitions(
            id, message_id, status, ocr_text, candidates_json, warnings_json,
            error_message, created_at, updated_at
          ) VALUES (?, ?, 'PROCESSING', '', '[]', '[]', '', ?, ?)
          """, JdbcSupport.id(), messageId, now, now);
      return true;
    } catch (DuplicateKeyException ignored) {
      String staleBefore = Instant.now().minusSeconds(300).toString();
      return jdbc.update("""
          UPDATE message_price_alert_recognitions
          SET status = 'PROCESSING', ocr_text = '', candidates_json = '[]',
              warnings_json = '[]', error_message = '', updated_at = ?
          WHERE message_id = ? AND (status = 'FAILED' OR (status = 'PROCESSING' AND updated_at < ?))
          """, now, messageId, staleBefore) == 1;
    }
  }

  public Result complete(
      String messageId,
      String ocrText,
      List<Candidate> candidates,
      List<String> warnings) {
    String status = candidates.isEmpty() ? "EMPTY" : "SUCCESS";
    jdbc.update("""
        UPDATE message_price_alert_recognitions
        SET status = ?, ocr_text = ?, candidates_json = ?, warnings_json = ?,
            error_message = '', updated_at = ?
        WHERE message_id = ?
        """, status, text(ocrText), write(candidates), write(warnings), JdbcSupport.now(), messageId);
    return find(messageId).orElseThrow();
  }

  public Result fail(
      String messageId, String ocrText, String errorMessage, List<String> warnings) {
    jdbc.update("""
        UPDATE message_price_alert_recognitions
        SET status = 'FAILED', ocr_text = ?, warnings_json = ?, error_message = ?, updated_at = ?
        WHERE message_id = ?
        """, text(ocrText), write(warnings), text(errorMessage), JdbcSupport.now(), messageId);
    return find(messageId).orElseThrow();
  }

  public Result requireById(String recognitionId) {
    return jdbc.query("""
        SELECT * FROM message_price_alert_recognitions WHERE id = ?
        """, (rs, rowNum) -> new Result(
        rs.getString("id"), rs.getString("message_id"), rs.getString("status"),
        read(rs.getString("candidates_json"), CANDIDATES),
        read(rs.getString("warnings_json"), WARNINGS), rs.getString("error_message"),
        rs.getString("updated_at")), recognitionId).stream().findFirst()
        .orElseThrow(() -> new IllegalArgumentException("智能识别记录不存在"));
  }

  private <T> T read(String json, TypeReference<T> type) {
    try {
      return mapper.readValue(json == null || json.isBlank() ? "[]" : json, type);
    } catch (Exception error) {
      throw new IllegalStateException("智能识别结果损坏", error);
    }
  }

  private String write(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception error) {
      throw new IllegalStateException("智能识别结果无法保存", error);
    }
  }

  private String text(String value) {
    return value == null ? "" : value.trim();
  }
}
