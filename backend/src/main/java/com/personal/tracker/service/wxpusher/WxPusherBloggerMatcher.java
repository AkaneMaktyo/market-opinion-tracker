package com.personal.tracker.service.wxpusher;

import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.WxPusherBlogger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class WxPusherBloggerMatcher {
  private static final Pattern BRACKET_SOURCE = Pattern.compile("\\[[^\\]\\n]*?｜\\s*([^\\]\\n:]{2,40})\\]");
  private static final Pattern LEADING_BRACKET_SOURCE = Pattern.compile("^\\s*\\[([^\\]\\n:]{2,60})\\]");
  private static final Pattern LEADING_COMPACT_SOURCE = Pattern.compile("^\\s*([^\\s:\\n]{2,80})\\s*(?:\\R|$)");
  private static final Pattern LINE_BRACKET_SOURCE = Pattern.compile("(?m)^\\s*\\[([^\\]\\n:]{2,80})\\]");
  private static final Pattern LINE_SOURCE = Pattern.compile("(?m)^\\s*[^\\p{L}\\p{N}\\n]{0,6}｜\\s*([^\\n:]{2,40})\\s*$");
  private static final Pattern BRACKET_HANDLE = Pattern.compile("\\]\\s*([^:\\n]{2,40})\\s*:");
  private static final Pattern AT_HANDLE = Pattern.compile("@([\\p{L}\\p{N}_.-]{2,40})");

  private WxPusherBloggerMatcher() {
  }

  public static Optional<WxPusherBlogger> match(String sourceName, List<WxPusherBlogger> bloggers) {
    return bloggers.stream()
        .filter(WxPusherBlogger::enabled)
        .map(blogger -> new ScoredMatch(blogger, score(sourceName, blogger)))
        .filter(match -> match.score() > 0)
        .sorted((left, right) -> Integer.compare(right.score(), left.score()))
        .map(ScoredMatch::blogger)
        .findFirst();
  }

  public static Optional<WxPusherBlogger> match(
      WxPusherClient.IncomingMessage incoming,
      List<WxPusherBlogger> bloggers) {
    return bloggers.stream()
        .filter(WxPusherBlogger::enabled)
        .map(blogger -> new ScoredMatch(blogger, score(incoming, blogger)))
        .filter(match -> match.score() > 0)
        .sorted((left, right) -> Integer.compare(right.score(), left.score()))
        .map(ScoredMatch::blogger)
        .findFirst();
  }

  public static boolean matches(String sourceName, WxPusherBlogger blogger) {
    return score(sourceName, blogger) > 0;
  }

  public static boolean matches(WxPusherClient.IncomingMessage incoming, WxPusherBlogger blogger) {
    return score(incoming, blogger) > 0;
  }

  public static boolean hasExplicitSource(WxPusherClient.IncomingMessage incoming) {
    return !sourceCandidates(incoming).isEmpty();
  }

  private static int score(WxPusherClient.IncomingMessage incoming, WxPusherBlogger blogger) {
    int source = 0;
    List<String> sources = sourceCandidates(incoming);
    for (String candidate : sources) {
      source = Math.max(source, score(candidate, blogger));
    }
    if (source > 0) {
      return source + 3;
    }
    if (!sources.isEmpty()) {
      return 0;
    }
    int best = 0;
    for (String candidate : authorCandidates(incoming)) {
      best = Math.max(best, score(candidate, blogger));
    }
    if (best > 0) {
      return best;
    }
    return Math.max(scoreText(incoming.title(), blogger), scoreText(incoming.summary(), blogger));
  }

  private static int score(String sourceName, WxPusherBlogger blogger) {
    String normalized = normalize(sourceName);
    if (normalized.isBlank()) {
      return 0;
    }
    int direct = scoreValue(normalized, blogger.bloggerName());
    if (direct > 0) {
      return direct;
    }
    return blogger.aliases().stream()
        .mapToInt(alias -> scoreValue(normalized, alias))
        .max()
        .orElse(0);
  }

  private static int scoreValue(String sourceName, String candidate) {
    String normalizedCandidate = normalize(candidate);
    if (normalizedCandidate.isBlank()) {
      return 0;
    }
    if (sourceName.equals(normalizedCandidate)) {
      return 3;
    }
    if (sourceName.contains(normalizedCandidate) || normalizedCandidate.contains(sourceName)) {
      return 2;
    }
    return 0;
  }

  private static int scoreText(String text, WxPusherBlogger blogger) {
    String normalized = normalize(text);
    if (normalized.isBlank()) {
      return 0;
    }
    int direct = scoreTextValue(normalized, blogger.bloggerName());
    if (direct > 0) {
      return direct;
    }
    return blogger.aliases().stream()
        .mapToInt(alias -> scoreTextValue(normalized, alias))
        .max()
        .orElse(0);
  }

  private static int scoreTextValue(String text, String candidate) {
    String normalizedCandidate = normalize(candidate);
    if (normalizedCandidate.isBlank()) {
      return 0;
    }
    return text.contains(normalizedCandidate) ? 1 : 0;
  }

  private static List<String> authorCandidates(WxPusherClient.IncomingMessage incoming) {
    List<String> values = new ArrayList<>();
    values.add(incoming.bloggerName());
    values.addAll(extractHandles(incoming.title()));
    values.addAll(extractHandles(incoming.summary()));
    return values.stream()
        .map(WxPusherBloggerMatcher::normalize)
        .filter(value -> !value.isBlank())
        .distinct()
        .toList();
  }

  private static List<String> sourceCandidates(WxPusherClient.IncomingMessage incoming) {
    List<String> values = new ArrayList<>();
    values.addAll(extractSources(incoming.summary()));
    values.addAll(extractSources(incoming.title()));
    return values.stream()
        .map(WxPusherBloggerMatcher::normalize)
        .filter(value -> !value.isBlank())
        .distinct()
        .toList();
  }

  private static List<String> extractSources(String text) {
    String safe = text == null ? "" : text;
    List<String> values = new ArrayList<>();
    collectDecoratedFirstLine(values, safe);
    collect(values, BRACKET_SOURCE, safe);
    if (!hasSpecificHandle(safe)) {
      collect(values, LEADING_BRACKET_SOURCE, safe);
      collectLineBracketSources(values, safe);
      collectCompactSource(values, safe);
    }
    collect(values, LINE_SOURCE, safe);
    return values;
  }

  private static boolean hasSpecificHandle(String text) {
    return extractHandles(text).stream().anyMatch(value -> !genericHandle(value));
  }

  private static boolean genericHandle(String value) {
    String normalized = normalize(value);
    return normalized.equals("premium signals")
        || normalized.equals("feed")
        || normalized.equals("reply")
        || normalized.equals("embeds");
  }

  private static List<String> extractHandles(String text) {
    String safe = text == null ? "" : text;
    List<String> values = new ArrayList<>();
    collect(values, BRACKET_HANDLE, safe);
    collect(values, AT_HANDLE, safe);
    return values;
  }

  private static void collect(List<String> values, Pattern pattern, String text) {
    var matcher = pattern.matcher(text);
    while (matcher.find()) {
      values.add(matcher.group(1));
    }
  }

  private static void collectLineBracketSources(List<String> values, String text) {
    var matcher = LINE_BRACKET_SOURCE.matcher(text);
    while (matcher.find()) {
      String value = matcher.group(1);
      if (!genericDecoratedLine("[" + value + "]")) {
        values.add(value);
      }
    }
  }

  private static void collectDecoratedFirstLine(List<String> values, String text) {
    for (String line : text.split("\\R")) {
      String value = line.trim();
      if (value.isBlank()) {
        continue;
      }
      if (startsDecorated(value) && hasLetter(value) && !genericDecoratedLine(value)) {
        values.add(value);
      }
      return;
    }
  }

  private static boolean genericDecoratedLine(String value) {
    String normalized = normalize(value);
    return normalized.length() <= 12 && normalized.startsWith("[") && normalized.endsWith("]");
  }

  private static boolean hasLetter(String value) {
    return value.codePoints().anyMatch(Character::isLetter);
  }

  private static void collectCompactSource(List<String> values, String text) {
    var matcher = LEADING_COMPACT_SOURCE.matcher(text);
    if (!matcher.find()) {
      return;
    }
    String value = matcher.group(1);
    if (value.contains("｜") || !startsDecorated(value)) {
      return;
    }
    values.add(value);
  }

  private static boolean startsDecorated(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    int first = value.codePointAt(0);
    return !Character.isLetterOrDigit(first);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private record ScoredMatch(WxPusherBlogger blogger, int score) {
  }
}
