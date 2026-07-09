package com.personal.tracker.service.wxpusher.instruments;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class MessageInstrumentExtractor {
  private static final Pattern SYMBOL = Pattern.compile(
      "(?<![A-Za-z0-9])\\$?([A-Za-z][A-Za-z.]{1,4})(?![A-Za-z0-9])");
  private static final Pattern CRYPTO_SHORT = Pattern.compile(
      "(?i)(?<![A-Za-z])(?:\\d+(?:\\.\\d+)?X\\s*)?([BE])\\s*[，,]?\\s*\\d{3,}(?:\\.\\d+)?");
  private static final Set<String> NOISE = Set.of(
      "ADP", "ADR", "AGI", "AI", "ALL", "AND", "API", "APP", "AR", "ARR", "ASCI", "AWS", "BOJ",
      "ASIC", "B.E", "BRC", "BUT", "BUY", "CALL", "CAPE", "CEO", "CFO", "CIA", "CMP", "CODEX", "COM", "CPI", "CPO", "CPU",
      "CCL", "CF", "DATA", "DAYS", "DIY", "DISK", "EA", "ECB", "EMS", "EPS", "ETF", "EUV",
      "FAA", "FCF", "FOMC", "FROM", "GCP", "GDP", "GDR", "GI", "GOOD",
      "GPT", "GPU", "GTC", "GW", "HBM", "HDD", "HIGH", "HL", "HTTP",
      "HVDC", "HY", "HYJSJ", "IDC", "IDM", "IN", "IPO", "IS", "ISM", "KDJ", "LET", "LIMIT",
      "LLM", "LNG", "LOI", "LONG", "LOT", "LOW", "LPS", "MKS", "ML",
      "MORE", "MOST", "MR", "MVIS", "NAND", "NEWS", "NFP", "NOW", "NPO", "NV",
      "OAI", "ODM", "OEM", "OPEC", "OS", "OUT", "PCB", "PC", "PCE", "PE", "PIPS", "PLAN", "PM",
      "PMI", "PPI", "PRE", "PREM", "PREMI", "PUT", "QQ", "QOQ", "RISK",
      "ROI", "RSI", "SAAS", "SEC", "SELL", "SHIFT", "SK", "SL", "SMALL", "SOF",
      "SOS", "SOW", "SOX", "SOP", "STOP", "SUV", "TACO", "TEAM", "THE", "TIPS", "TM",
      "TODAY", "TP", "TPU", "TSMC", "URL", "USD", "US", "VERA", "VIP", "VR",
      "VVIX", "WFE", "WTI", "XAI", "XEON", "YOY", "YZX");
  private static final Map<String, String> SYMBOL_ALIASES = Map.of(
      "XAU", "GOLD");
  private static final Map<String, String> ALIASES = Map.ofEntries(
      Map.entry("美光", "MU"),
      Map.entry("英伟达", "NVDA"),
      Map.entry("苹果", "AAPL"),
      Map.entry("台积电", "TSM"),
      Map.entry("微软", "MSFT"),
      Map.entry("特斯拉", "TSLA"),
      Map.entry("博通", "AVGO"),
      Map.entry("高通", "QCOM"),
      Map.entry("甲骨文", "ORCL"),
      Map.entry("谷歌", "GOOGL"),
      Map.entry("亚马逊", "AMZN"),
      Map.entry("奈飞", "NFLX"),
      Map.entry("英特尔", "INTC"),
      Map.entry("闪迪", "SNDK"),
      Map.entry("希捷", "STX"),
      Map.entry("西部数据", "WDC"),
      Map.entry("海力士", "SKHYNIX"),
      Map.entry("三星", "SAMSUNG"),
      Map.entry("比特币", "BTC"),
      Map.entry("以太坊", "ETH"),
      Map.entry("黄金", "GOLD"),
      Map.entry("白银", "XAG"),
      Map.entry("原油", "OIL"),
      Map.entry("纳指", "QQQ"),
      Map.entry("标普", "SPY"));

  private static final Map<String, String> CHINESE_ALIASES = Map.ofEntries(
      Map.entry("\u7f8e\u5149", "MU"),
      Map.entry("\u82f1\u4f1f\u8fbe", "NVDA"),
      Map.entry("\u82f9\u679c", "AAPL"),
      Map.entry("\u53f0\u79ef\u7535", "TSM"),
      Map.entry("\u5fae\u8f6f", "MSFT"),
      Map.entry("\u7279\u65af\u62c9", "TSLA"),
      Map.entry("\u535a\u901a", "AVGO"),
      Map.entry("\u9ad8\u901a", "QCOM"),
      Map.entry("\u7532\u9aa8\u6587", "ORCL"),
      Map.entry("\u8c37\u6b4c", "GOOGL"),
      Map.entry("\u4e9a\u9a6c\u900a", "AMZN"),
      Map.entry("\u5948\u98de", "NFLX"),
      Map.entry("\u82f1\u7279\u5c14", "INTC"),
      Map.entry("\u95ea\u8fea", "SNDK"),
      Map.entry("\u5e0c\u6377", "STX"),
      Map.entry("\u897f\u90e8\u6570\u636e", "WDC"),
      Map.entry("\u6d77\u529b\u58eb", "SKHYNIX"),
      Map.entry("\u4e09\u661f", "SAMSUNG"),
      Map.entry("\u6bd4\u7279\u5e01", "BTC"),
      Map.entry("\u4ee5\u592a\u574a", "ETH"),
      Map.entry("\u9ec4\u91d1", "GOLD"),
      Map.entry("\u767d\u94f6", "XAG"),
      Map.entry("\u539f\u6cb9", "OIL"),
      Map.entry("\u7eb3\u6307", "QQQ"),
      Map.entry("\u6807\u666e", "SPY"));

  private MessageInstrumentExtractor() {
  }

  public static List<String> extract(String text) {
    String safe = text == null ? "" : text;
    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (String line : safe.split("\\R")) {
      if (!usefulLine(line)) {
        continue;
      }
      addAliases(result, line);
      addCryptoShorthand(result, line);
      var matcher = SYMBOL.matcher(line);
      while (matcher.find()) {
        String raw = matcher.group(1);
        String symbol = normalize(raw);
        if (candidate(symbol, raw)) {
          result.add(canonical(symbol));
        }
      }
    }
    return List.copyOf(result);
  }

  private static void addAliases(Set<String> result, String line) {
    ALIASES.forEach((alias, symbol) -> {
      if (line.contains(alias)) {
        result.add(symbol);
      }
    });
    CHINESE_ALIASES.forEach((alias, symbol) -> {
      if (line.contains(alias)) {
        result.add(symbol);
      }
    });
  }

  private static void addCryptoShorthand(Set<String> result, String line) {
    var matcher = CRYPTO_SHORT.matcher(line);
    while (matcher.find()) {
      result.add("B".equalsIgnoreCase(matcher.group(1)) ? "BTC" : "ETH");
    }
  }

  private static boolean candidate(String symbol, String raw) {
    return symbol.length() >= 2
        && symbol.length() <= 8
        && (symbol.equals(raw) || symbol.length() <= 4)
        && !symbol.matches("\\d+")
        && !NOISE.contains(symbol)
        && !looksLikeTruncatedWord(symbol);
  }

  private static boolean looksLikeTruncatedWord(String symbol) {
    return Set.of(
        "AW", "BU", "DA", "GO", "GU", "IT", "LD", "OB", "ON", "OR", "OW",
        "PR", "PU", "PY", "RM", "SM", "SN", "TH", "TO", "UD", "UM", "UP",
        "VI", "VL").contains(symbol);
  }

  private static boolean usefulLine(String line) {
    String value = line == null ? "" : line;
    return (containsCjk(value) && (hasMarketContext(value) || hasChineseMarketContext(value)))
        || (containsCjk(value) && hasSymbolAndNumber(value))
        || value.contains("$")
        || value.matches(".*\\d+(\\.\\d+)?X.*")
        || value.matches(".*[+-]\\d+(\\.\\d+)?%.*")
        || hasEnglishTradingContext(value);
  }

  private static boolean hasSymbolAndNumber(String value) {
    if (!value.matches(".*\\d.*")) {
      return false;
    }
    var matcher = SYMBOL.matcher(value);
    while (matcher.find()) {
      if (candidate(normalize(matcher.group(1)), matcher.group(1))) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasEnglishTradingContext(String value) {
    String upper = value == null ? "" : value.toUpperCase(Locale.ROOT);
    return upper.matches(".*\\b(BUY|SELL|LONG|SHORT|TP|SL|GOLD|XAU)\\b.*");
  }

  private static boolean hasMarketContext(String value) {
    return List.of(
        "买", "卖", "多", "空", "仓", "正股", "支撑", "止损", "止盈", "目标", "反弹",
        "上涨", "下跌", "大涨", "大跌", "走高", "回落", "回调", "突破", "跌破", "止跌",
        "加仓", "减仓", "入场", "现价", "均价", "压力", "利好", "利空", "评级", "上调",
        "下调", "财报", "业绩", "前瞻", "影响", "需求", "关注", "观察", "提到", "分析")
        .stream()
        .anyMatch(value::contains);
  }

  private static boolean hasChineseMarketContext(String value) {
    return List.of(
        "\u4e70", "\u5356", "\u591a", "\u7a7a", "\u4ed3", "\u6b63\u80a1",
        "\u652f\u6491", "\u6b62\u635f", "\u6b62\u76c8", "\u76ee\u6807", "\u53cd\u5f39",
        "\u4e0a\u6da8", "\u4e0b\u8dcc", "\u5927\u6da8", "\u5927\u8dcc", "\u8d70\u9ad8",
        "\u56de\u843d", "\u56de\u8c03", "\u7a81\u7834", "\u8dcc\u7834", "\u6b62\u8dcc",
        "\u52a0\u4ed3", "\u51cf\u4ed3", "\u5165\u573a", "\u73b0\u4ef7", "\u5747\u4ef7",
        "\u538b\u529b", "\u5229\u597d", "\u5229\u7a7a", "\u8bc4\u7ea7", "\u4e0a\u8c03",
        "\u4e0b\u8c03", "\u8d22\u62a5", "\u4e1a\u7ee9", "\u524d\u77bb", "\u5f71\u54cd",
        "\u9700\u6c42", "\u5173\u6ce8", "\u89c2\u5bdf", "\u63d0\u5230", "\u5206\u6790",
        "\u534a\u5bfc\u4f53", "\u82af\u7247", "\u6307\u6570", "\u6301\u4ed3", "\u73b0\u91d1",
        "\u62a5\u544a", "\u652f\u51fa", "\u751f\u4ea7", "\u53cd\u8f6c")
        .stream()
        .anyMatch(value::contains);
  }

  private static boolean containsCjk(String value) {
    return value.codePoints().anyMatch(code -> Character.UnicodeScript.of(code)
        == Character.UnicodeScript.HAN);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().replaceAll("\\.+$", "").toUpperCase(Locale.ROOT);
  }

  private static String canonical(String symbol) {
    return SYMBOL_ALIASES.getOrDefault(symbol, symbol);
  }
}
