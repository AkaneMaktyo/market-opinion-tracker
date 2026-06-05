package com.personal.tracker.service.positions;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class PositionActionResolver {
  public static final String OPEN = "OPEN";
  public static final String CLOSE = "CLOSE";
  public static final String IGNORE = "IGNORE";
  private static final List<String> OPEN_WORDS = List.of(
      "买入", "建仓", "开仓", "加仓", "持有", "继续持有");
  private static final List<String> CLOSE_WORDS = List.of(
      "卖出", "清仓", "平仓", "止盈", "止损", "退出", "离场",
      "减仓到零", "减仓为零", "减至零", "全部减仓", "全部卖出");

  public String resolve(
      String explicitAction,
      String rawDirection,
      String thesis,
      String triggerCondition,
      String risksText,
      String priceNotesText,
      String sourceQuote) {
    if (explicitAction != null && !explicitAction.isBlank()) {
      return normalize(explicitAction);
    }
    String actionText = String.join("\n",
        safe(rawDirection),
        safe(thesis),
        safe(triggerCondition),
        safe(sourceQuote));
    if (containsAny(actionText, CLOSE_WORDS)) {
      return CLOSE;
    }
    if (containsAny(actionText, OPEN_WORDS)) {
      return OPEN;
    }
    return IGNORE;
  }

  public String normalize(String value) {
    String input = safe(value);
    String upper = input.toUpperCase(Locale.ROOT);
    if (OPEN.equals(upper) || input.matches(".*(买入|建仓|开仓|加仓|持有|继续持有).*")) {
      return OPEN;
    }
    if (CLOSE.equals(upper) || input.matches(".*(卖出|清仓|平仓|止盈|止损|退出|离场|减仓到零|减仓为零|减至零).*")) {
      return CLOSE;
    }
    return IGNORE;
  }

  private static boolean containsAny(String text, List<String> words) {
    return words.stream().anyMatch(text::contains);
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }
}
