package com.personal.tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.domain.LiveSession;
import com.personal.tracker.domain.PriceLevel;
import com.personal.tracker.repository.KolRepository;
import com.personal.tracker.repository.SessionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ImportService {
  private static final Pattern TOKEN = Pattern.compile("[A-Za-z]{2,8}|[0-9]{3,6}");
  private final ObjectMapper mapper;
  private final SessionRepository sessions;
  private final OpinionService opinions;

  public ImportService(ObjectMapper mapper, SessionRepository sessions, OpinionService opinions) {
    this.mapper = mapper;
    this.sessions = sessions;
    this.opinions = opinions;
  }

  public ImportPreview preview(ImportPreviewRequest request) {
    try {
      JsonNode root = mapper.readTree(request.rawJson());
      List<ImportCandidate> candidates = new ArrayList<>();
      List<SkippedItem> skipped = new ArrayList<>();
      JsonNode items = root.path("按具体品种划分");
      if (items.isArray()) {
        for (JsonNode item : items) {
          parseItem(item, candidates, skipped);
        }
      }
      return new ImportPreview(summary(root), mappingNotes(root), candidates, skipped);
    } catch (Exception error) {
      throw new IllegalArgumentException("JSON 解析失败: " + error.getMessage());
    }
  }

  public ImportCommitResult commit(ImportCommitRequest request) {
    String date = value(request.sessionDate(), LocalDate.now().toString());
    LiveSession session = sessions.create(
        value(request.kolId(), KolRepository.DEFAULT_ID),
        date,
        value(request.title(), "JSON 导入直播"),
        "JSON_IMPORT",
        request.rawJson());
    int saved = 0;
    for (ImportCandidate item : request.items()) {
      if (!item.selected()) {
        continue;
      }
      opinions.create(new OpinionService.CreateOpinionCommand(
          session.id(), item.symbol(), item.displayName(), null,
          item.direction(), "未指定", item.thesis(), item.triggerCondition(),
          item.risksText(), null, item.sourceQuote(), null,
          item.rawDirection(), item.risksText(), item.catalystsText(),
          item.priceNotesText(), item.rawItemJson(), date + "T09:30:00",
          item.priceLevels()));
      saved++;
    }
    return new ImportCommitResult(session.id(), saved);
  }

  private void parseItem(
      JsonNode item,
      List<ImportCandidate> candidates,
      List<SkippedItem> skipped) throws Exception {
    String name = text(item, "品种");
    String symbol = extractSymbol(name);
    if (symbol.isBlank()) {
      skipped.add(new SkippedItem(name, "未识别到明确交易代码"));
      return;
    }
    String rawDirection = text(item, "方向");
    String priceText = join(item.get("关键价位"));
    candidates.add(new ImportCandidate(
        true, symbol, name, mapDirection(rawDirection),
        rawDirection, firstText(item, "关键判断", "核心观点", "逻辑"),
        join(item.get("催化")), firstText(item, "替代策略", "定位"),
        join(item.get("风险")), priceText, null, mapper.writeValueAsString(item),
        priceLines(priceText)));
  }

  private String extractSymbol(String name) {
    if (name == null || name.isBlank()) {
      return "";
    }
    Map<String, String> aliases = Map.of(
        "三星", "SAMSUNG",
        "黄金", "GOLD",
        "小米", "1810",
        "诺基亚", "NOK",
        "海力士", "SKHYNIX");
    for (var entry : aliases.entrySet()) {
      if (name.contains(entry.getKey())) {
        return entry.getValue();
      }
    }
    var matcher = TOKEN.matcher(name);
    while (matcher.find()) {
      String token = matcher.group().toUpperCase();
      if (!List.of("ETF", "HDD", "CPU", "BTC").contains(token)) {
        return token;
      }
    }
    return "";
  }

  private String mapDirection(String raw) {
    String value = raw == null ? "" : raw;
    if (value.contains("看空")) {
      return "BEARISH";
    }
    if (value.contains("震荡")) {
      return "RANGE";
    }
    if (value.contains("看多") || value.contains("强烈") || value.contains("更优")) {
      return "BULLISH";
    }
    return "WATCH";
  }

  private List<PriceLevel> priceLines(String text) {
    List<PriceLevel> levels = new ArrayList<>();
    var matcher = Pattern.compile("\\d+(?:\\.\\d+)?").matcher(value(text, ""));
    while (matcher.find() && levels.size() < 4) {
      levels.add(new PriceLevel(null, null, "NOTE", new BigDecimal(matcher.group()), text));
    }
    return levels;
  }

  private List<String> summary(JsonNode root) {
    return objectLines(root.path("总体摘要"));
  }

  private List<String> mappingNotes(JsonNode root) {
    return objectLines(root.path("待确认映射"));
  }

  private List<String> objectLines(JsonNode node) {
    List<String> lines = new ArrayList<>();
    if (!node.isObject()) {
      return lines;
    }
    Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
    while (fields.hasNext()) {
      var field = fields.next();
      lines.add(field.getKey() + ": " + field.getValue().asText());
    }
    return lines;
  }

  private String firstText(JsonNode node, String... keys) {
    for (String key : keys) {
      String value = text(node, key);
      if (!value.isBlank()) {
        return value;
      }
    }
    return "";
  }

  private String text(JsonNode node, String key) {
    JsonNode value = node == null ? null : node.get(key);
    return value == null || value.isNull() ? "" : value.asText();
  }

  private String join(JsonNode node) {
    if (node == null || node.isNull()) {
      return "";
    }
    if (!node.isArray()) {
      return node.asText();
    }
    List<String> values = new ArrayList<>();
    node.forEach(item -> values.add(item.asText()));
    return String.join("\n", values);
  }

  private String value(String input, String fallback) {
    return input == null || input.isBlank() ? fallback : input.trim();
  }

  public record ImportPreviewRequest(String kolId, String title, String sessionDate, String rawJson) {
  }

  public record ImportPreview(
      List<String> summary,
      List<String> mappingNotes,
      List<ImportCandidate> candidates,
      List<SkippedItem> skipped) {
  }

  public record ImportCandidate(
      boolean selected,
      String symbol,
      String displayName,
      String direction,
      String rawDirection,
      String thesis,
      String catalystsText,
      String triggerCondition,
      String risksText,
      String priceNotesText,
      String sourceQuote,
      String rawItemJson,
      List<PriceLevel> priceLevels) {
  }

  public record SkippedItem(String name, String reason) {
  }

  public record ImportCommitRequest(
      String kolId,
      String title,
      String sessionDate,
      String rawJson,
      List<ImportCandidate> items) {
    public ImportCommitRequest {
      items = items == null ? List.of() : items;
    }
  }

  public record ImportCommitResult(String sessionId, int savedOpinions) {
  }
}
