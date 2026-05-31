package com.personal.tracker.service.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.domain.PriceLevel;
import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.service.ImportService.ImportCandidate;
import com.personal.tracker.service.ImportService.ImportPreview;
import com.personal.tracker.service.ImportService.SkippedItem;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class FlexibleJsonOpinionParser implements JsonOpinionParser {
  private static final Pattern TOKEN = Pattern.compile("[A-Za-z]{2,8}|[0-9]{3,6}");
  private static final List<String> ITEM_KEYS = List.of("品种", "symbol", "ticker", "代码", "标的", "name");
  private static final List<String> NON_TRADE = List.of(
      "港股打新", "OpenAI", "Codex", "Claude", "港卡", "开户", "CRS");
  private final ObjectMapper mapper;

  public FlexibleJsonOpinionParser(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public ImportPreview parse(String rawJson) throws Exception {
    JsonNode root = mapper.readTree(rawJson);
    List<ImportCandidate> candidates = new ArrayList<>();
    List<SkippedItem> skipped = new ArrayList<>();
    for (JsonNode item : candidateItems(root)) {
      parseItem(item, candidates, skipped);
    }
    return new ImportPreview(summary(root), mappingNotes(root), candidates, skipped);
  }

  private void parseItem(
      JsonNode item,
      List<ImportCandidate> candidates,
      List<SkippedItem> skipped) throws Exception {
    String displayName = firstText(item, "品种", "标的", "name");
    String symbolText = firstText(item, "代码", "symbol", "ticker", "标准代码");
    String lookupName = value(symbolText, displayName);
    if (isNonTrade(displayName)) {
      skipped.add(new SkippedItem(displayName, "非明确交易标的"));
      return;
    }
    String symbol = extractSymbol(lookupName);
    if (symbol.isBlank()) {
      skipped.add(new SkippedItem(displayName, "未识别到明确交易代码"));
      return;
    }
    String rawDirection = firstText(item, "方向", "观点方向", "sentiment", "view");
    String priceText = joinFirst(item, "关键价位", "价位", "支撑压力", "目标价", "priceLevels");
    String market = JdbcSupport.market(firstText(item, "市场", "market"), symbol);
    candidates.add(new ImportCandidate(
        true,
        symbol,
        value(displayName, symbol),
        market,
        mapDirection(rawDirection),
        rawDirection,
        firstText(item, "周期", "时间周期", "horizon", "timeframe"),
        firstText(item, "关键判断", "核心观点", "观点", "逻辑", "thesis", "summary"),
        joinFirst(item, "催化", "催化因素", "trigger", "catalysts"),
        firstText(item, "触发条件", "替代策略", "定位"),
        joinFirst(item, "风险", "风险提示", "risks"),
        priceText,
        firstText(item, "原文摘录", "source_quote", "sourceQuote"),
        mapper.writeValueAsString(item),
        priceLines(priceText)));
  }

  private List<JsonNode> candidateItems(JsonNode root) {
    JsonNode preferred = root.path("按具体品种划分");
    if (preferred.isArray()) {
      List<JsonNode> items = new ArrayList<>();
      preferred.forEach(items::add);
      return items;
    }
    List<JsonNode> items = new ArrayList<>();
    collectCandidates(root, items);
    return items;
  }

  private void collectCandidates(JsonNode node, List<JsonNode> items) {
    if (node == null || node.isNull()) {
      return;
    }
    if (node.isObject() && ITEM_KEYS.stream().anyMatch(node::has)) {
      items.add(node);
      return;
    }
    if (node.isArray() || node.isObject()) {
      node.forEach(child -> collectCandidates(child, items));
    }
  }

  private String extractSymbol(String name) {
    if (name == null || name.isBlank()) {
      return "";
    }
    Map<String, String> aliases = Map.ofEntries(
        Map.entry("三星", "SAMSUNG"),
        Map.entry("黄金", "GOLD"),
        Map.entry("小米", "1810"),
        Map.entry("诺基亚", "NOK"),
        Map.entry("海力士", "SKHYNIX"),
        Map.entry("美光", "MU"),
        Map.entry("英伟达", "NVDA"),
        Map.entry("谷歌", "GOOGL"),
        Map.entry("闪迪", "SNDK"),
        Map.entry("台积电", "TSMC"),
        Map.entry("高通", "QCOM"),
        Map.entry("博通", "AVGO"),
        Map.entry("微软", "MSFT"),
        Map.entry("苹果", "AAPL"),
        Map.entry("康宁", "GLW"),
        Map.entry("希捷", "STX"),
        Map.entry("google", "GOOGL"),
        Map.entry("coinbase", "COIN"),
        Map.entry("bitcoin", "BTC"),
        Map.entry("比特币", "BTC"),
        Map.entry("特斯拉", "TSLA"),
        Map.entry("亚马逊", "AMZN"));
    String normalizedName = name.toLowerCase(Locale.ROOT);
    for (Entry<String, String> entry : aliases.entrySet()) {
      if (normalizedName.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
        return entry.getValue();
      }
    }
    var matcher = TOKEN.matcher(name);
    while (matcher.find()) {
      String token = matcher.group().toUpperCase(Locale.ROOT);
      if (!List.of("ETF", "HDD", "CPU").contains(token)) {
        return token;
      }
    }
    return "";
  }

  private boolean isNonTrade(String name) {
    if (name == null || name.isBlank()) {
      return false;
    }
    return NON_TRADE.stream().anyMatch(name::contains);
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
    for (String line : value(text, "").split("[\\r\\n,，；;]+")) {
      if (isQuantityNote(line)) {
        continue;
      }
      var matcher = Pattern.compile("\\d+(?:\\.\\d+)?").matcher(line);
      while (matcher.find() && levels.size() < 4) {
        levels.add(new PriceLevel(null, null, "NOTE", new BigDecimal(matcher.group()), text));
      }
    }
    return levels;
  }

  private boolean isQuantityNote(String text) {
    return text.matches(".*(亿|盎司|缺口|需求).*")
        && !text.matches(".*(价|支撑|压力|目标|止损|突破|跌破|上方|下方|附近).*");
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

  private String joinFirst(JsonNode node, String... keys) {
    for (String key : keys) {
      String value = join(node == null ? null : node.get(key));
      if (!value.isBlank()) {
        return value;
      }
    }
    return "";
  }

  private String value(String input, String fallback) {
    return input == null || input.isBlank() ? fallback : input.trim();
  }
}
