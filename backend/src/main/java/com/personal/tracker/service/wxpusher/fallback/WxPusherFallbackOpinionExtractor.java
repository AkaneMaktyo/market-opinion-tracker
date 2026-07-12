package com.personal.tracker.service.wxpusher.fallback;

import com.personal.tracker.domain.PriceLevel;
import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.service.ImportService.ImportCandidate;
import com.personal.tracker.service.wxpusher.instruments.MessageInstrumentExtractor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class WxPusherFallbackOpinionExtractor {
  private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");
  private static final Pattern URL = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
  private static final Pattern LABELED_LEVEL = Pattern.compile(
      "(TP|SL|@|止损|止盈|目标|支撑|压力)\\s*[:：@]?\\s*(\\d+(?:\\.\\d+)?)",
      Pattern.CASE_INSENSITIVE);
  private static final List<String> BULLISH_WORDS = List.of(
      "BUY", "LONG", "做多", "看多", "买", "开回", "开仓", "加仓", "进", "抄底", "拿好");
  private static final List<String> BEARISH_WORDS = List.of(
      "SELL", "SHORT", "做空", "看空", "卖", "清仓", "甩卖", "止盈", "止损", "减仓");
  private static final List<String> OPEN_WORDS = List.of(
      "BUY", "LONG", "做多", "买", "开回", "开仓", "加仓", "进", "抄底");
  private static final List<String> CLOSE_WORDS = List.of(
      "清仓", "甩卖", "止盈", "止损", "减仓", "全部卖出", "全部减仓");

  private static final List<String> CHINESE_BULLISH_WORDS = List.of(
      "\u4e70", "\u505a\u591a", "\u770b\u591a", "\u5f00\u4ed3", "\u52a0\u4ed3",
      "\u5165\u573a", "\u6284\u5e95", "\u62ff\u597d");
  private static final List<String> CHINESE_BEARISH_WORDS = List.of(
      "\u5356", "\u505a\u7a7a", "\u770b\u7a7a", "\u6e05\u4ed3", "\u7529\u5356",
      "\u6b62\u76c8", "\u6b62\u635f", "\u51cf\u4ed3");
  private static final List<String> CHINESE_OPEN_WORDS = List.of(
      "\u4e70", "\u505a\u591a", "\u5f00\u4ed3", "\u52a0\u4ed3", "\u5165\u573a", "\u6284\u5e95");
  private static final List<String> CHINESE_CLOSE_WORDS = List.of(
      "\u6e05\u4ed3", "\u7529\u5356", "\u6b62\u76c8", "\u6b62\u635f", "\u51cf\u4ed3",
      "\u5168\u90e8\u5356\u51fa", "\u5168\u90e8\u51cf\u4ed3");

  private WxPusherFallbackOpinionExtractor() {
  }

  public static List<ImportCandidate> extract(String text) {
    String safeText = safe(text);
    return MessageInstrumentExtractor.extract(safeText).stream()
        .map(symbol -> candidate(symbol, safeText))
        .toList();
  }

  public static List<ImportCandidate> extract(String text, List<String> contextSymbols) {
    List<ImportCandidate> direct = extract(text);
    if (!direct.isEmpty() || !hasOpinionContext(text)) {
      return direct;
    }
    return (contextSymbols == null ? List.<String>of() : contextSymbols).stream()
        .filter(symbol -> symbol != null && !symbol.isBlank())
        .map(symbol -> symbol.trim().toUpperCase(Locale.ROOT))
        .distinct()
        .limit(1)
        .map(symbol -> candidate(symbol, safe(text)))
        .toList();
  }

  private static ImportCandidate candidate(String symbol, String text) {
    String thesis = thesis(text, symbol);
    String priceNotes = priceNotes(text, symbol);
    String direction = direction(thesis + "\n" + text);
    return new ImportCandidate(
        true,
        symbol,
        symbol,
        JdbcSupport.market("", symbol),
        direction,
        "关键词兜底",
        positionAction(direction, thesis),
        horizon(thesis),
        thesis,
        "",
        "",
        "",
        priceNotes,
        abbreviate(text, 900),
        "{\"fallback\":\"keyword\",\"symbol\":\"%s\"}".formatted(symbol),
        priceLevels(priceNotes));
  }

  private static String direction(String text) {
    String value = normalize(text);
    if (containsAny(value, BEARISH_WORDS) || containsAny(value, CHINESE_BEARISH_WORDS)) {
      return "BEARISH";
    }
    if (containsAny(value, BULLISH_WORDS) || containsAny(value, CHINESE_BULLISH_WORDS)) {
      return "BULLISH";
    }
    return "WATCH";
  }

  private static String positionAction(String direction, String text) {
    String value = normalize(text);
    if (containsAny(value, CLOSE_WORDS) || containsAny(value, CHINESE_CLOSE_WORDS)) {
      return "CLOSE";
    }
    if ("BULLISH".equals(direction)
        && (containsAny(value, OPEN_WORDS) || containsAny(value, CHINESE_OPEN_WORDS))) {
      return "OPEN";
    }
    return "IGNORE";
  }

  private static String horizon(String text) {
    String value = safe(text);
    if (value.contains("日内") || value.contains("短线") || value.toUpperCase(Locale.ROOT).contains("NOW")) {
      return "短线";
    }
    return "消息";
  }

  private static String thesis(String text, String symbol) {
    String upper = symbol.toUpperCase(Locale.ROOT);
    for (String line : safe(text).split("\\R")) {
      String value = line.trim();
      if (!value.isBlank() && value.toUpperCase(Locale.ROOT).contains(upper)) {
        return abbreviate(value, 180);
      }
    }
    return abbreviate(text, 180);
  }

  private static String priceNotes(String text, String symbol) {
    return safe(text).lines()
        .map(WxPusherFallbackOpinionExtractor::stripUrls)
        .map(String::trim)
        .filter(line -> !line.isBlank())
        .filter(WxPusherFallbackOpinionExtractor::priceContext)
        .filter(line -> NUMBER.matcher(line).find())
        .limit(3)
        .reduce((left, right) -> left + "\n" + right)
        .orElse("");
  }

  private static List<PriceLevel> priceLevels(String text) {
    return safe(text).lines()
        .flatMap(line -> levelsFromLine(line).stream())
        .limit(6)
        .toList();
  }

  private static List<PriceLevel> levelsFromLine(String line) {
    var matcher = LABELED_LEVEL.matcher(line);
    List<PriceLevel> levels = matcher.results()
        .map(match -> new PriceLevel(null, null, levelType(match.group(1)), new BigDecimal(match.group(2)), line))
        .toList();
    if (!levels.isEmpty()) {
      return levels;
    }
    return NUMBER.matcher(line).results()
        .limit(2)
        .map(match -> new PriceLevel(null, null, "NOTE", new BigDecimal(match.group()), line))
        .toList();
  }

  private static String levelType(String label) {
    String value = normalize(label);
    if (value.contains("SL") || value.contains("止损")) {
      return "STOP";
    }
    if (value.contains("TP") || value.contains("目标") || value.contains("止盈")) {
      return "TARGET";
    }
    return "NOTE";
  }

  private static boolean priceContext(String line) {
    String value = normalize(line);
    if (value.contains("\u652f\u6491") || value.contains("\u538b\u529b")
        || value.contains("\u76ee\u6807") || value.contains("\u6b62\u635f")
        || value.contains("\u6b62\u76c8") || value.contains("\u73b0\u4ef7")) {
      return true;
    }
    return value.contains("@") || value.contains("TP") || value.contains("SL")
        || value.contains("支撑") || value.contains("压力") || value.contains("目标");
  }

  private static boolean hasOpinionContext(String text) {
    String value = normalize(text);
    return priceContext(value)
        || containsAny(value, BULLISH_WORDS)
        || containsAny(value, BEARISH_WORDS)
        || containsAny(value, OPEN_WORDS)
        || containsAny(value, CLOSE_WORDS)
        || containsAny(value, CHINESE_BULLISH_WORDS)
        || containsAny(value, CHINESE_BEARISH_WORDS)
        || containsAny(value, CHINESE_OPEN_WORDS)
        || containsAny(value, CHINESE_CLOSE_WORDS);
  }

  private static boolean containsAny(String text, List<String> words) {
    return words.stream().map(WxPusherFallbackOpinionExtractor::normalize).anyMatch(text::contains);
  }

  private static String normalize(String value) {
    return safe(value).toUpperCase(Locale.ROOT);
  }

  private static String abbreviate(String value, int maxLength) {
    String text = safe(value);
    return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }

  private static String stripUrls(String value) {
    return URL.matcher(safe(value)).replaceAll("");
  }
}
