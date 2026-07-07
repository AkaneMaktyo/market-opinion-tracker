package com.personal.tracker.service.wxpusher;

import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.WxPusherBlogger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class WxPusherBloggerMatcher {
  private static final Pattern BRACKET_SOURCE = Pattern.compile("\\[[^\\]\\n]*?｜\\s*([^\\]\\n:]{2,40})\\]");
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

  private static int score(WxPusherClient.IncomingMessage incoming, WxPusherBlogger blogger) {
    int source = 0;
    for (String candidate : sourceCandidates(incoming)) {
      source = Math.max(source, score(candidate, blogger));
    }
    if (source > 0) {
      return source + 3;
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
    collect(values, BRACKET_SOURCE, safe);
    collect(values, LINE_SOURCE, safe);
    return values;
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

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private record ScoredMatch(WxPusherBlogger blogger, int score) {
  }
}
