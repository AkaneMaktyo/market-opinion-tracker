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
  private static final Set<String> NOISE = Set.of(
      "ADP", "ADR", "AGI", "AI", "API", "APP", "AR", "ARR", "ASCI", "AWS", "BOJ",
      "B.E", "BRC", "BUY", "CALL", "CEO", "CFO", "CIA", "CMP", "CODEX", "CPI", "CPO", "CPU",
      "CCL", "CF", "DATA", "DIY", "DISK", "EA", "ECB", "EMS", "EPS", "ETF",
      "FAA", "FCF", "FOMC", "FROM", "GCP", "GDP", "GDR", "GI", "GOOD",
      "GPT", "GPU", "GTC", "GW", "HBM", "HDD", "HIGH", "HL", "HTTP",
      "HVDC", "HY", "HYJSJ", "IDC", "IDM", "IPO", "IS", "ISM", "LIMIT",
      "LLM", "LNG", "LOI", "LONG", "LOT", "LOW", "LPS", "MKS", "ML",
      "MORE", "MR", "MVIS", "NAND", "NEWS", "NFP", "NOW", "NPO", "NV",
      "OAI", "ODM", "OEM", "OPEC", "OS", "PCB", "PC", "PCE", "PE", "PM",
      "PMI", "PPI", "PRE", "PREM", "PREMI", "PUT", "QQ", "QOQ", "RISK",
      "ROI", "SAAS", "SEC", "SELL", "SHIFT", "SK", "SL", "SMALL", "SOF",
      "SOS", "SOW", "SOX", "SOP", "STOP", "SUV", "TEAM", "THE", "TIPS", "TM",
      "TODAY", "TP", "TPU", "TSMC", "URL", "USD", "US", "VIP", "VR",
      "VVIX", "WFE", "WTI", "XAI", "YOY", "YZX");
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
      var matcher = SYMBOL.matcher(line);
      while (matcher.find()) {
        String raw = matcher.group(1);
        String symbol = normalize(raw);
        if (candidate(symbol, raw)) {
          result.add(symbol);
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
    return (containsCjk(value) && hasMarketContext(value))
        || value.contains("$")
        || value.matches(".*[+-]\\d+(\\.\\d+)?%.*");
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

  private static boolean containsCjk(String value) {
    return value.codePoints().anyMatch(code -> Character.UnicodeScript.of(code)
        == Character.UnicodeScript.HAN);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().replaceAll("\\.+$", "").toUpperCase(Locale.ROOT);
  }
}
