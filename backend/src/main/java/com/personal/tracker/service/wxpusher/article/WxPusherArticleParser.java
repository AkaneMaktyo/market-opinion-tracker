package com.personal.tracker.service.wxpusher.article;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

public final class WxPusherArticleParser {
  public static final String IMAGE_PREFIX = "WXPUSHER_IMAGE_URL=";
  private static final Pattern MAIN_BLOCK = Pattern.compile(
      "(?is)<main[^>]*class=[\"'][^\"']*article-content[^\"']*[\"'][^>]*>(.*?)</main>");
  private static final Pattern SCRIPT_BLOCK = Pattern.compile("(?is)<(script|style|svg).*?</\\1>");
  private static final Pattern IMAGE_BLOCK = Pattern.compile("(?is)<img\\b[^>]*>");
  private static final Pattern ATTRIBUTE = Pattern.compile(
      "(?is)(data-src|data-original|data-url|src)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
  private static final Pattern TAG_BLOCK = Pattern.compile("(?is)<[^>]+>");
  private static final List<String> IMAGE_ATTRIBUTES = List.of("data-src", "data-original", "data-url", "src");

  private WxPusherArticleParser() {
  }

  public static String parse(String html, String articleUrl) {
    Matcher main = MAIN_BLOCK.matcher(html == null ? "" : html);
    String body = main.find() ? main.group(1) : value(html);
    body = SCRIPT_BLOCK.matcher(body).replaceAll(" ");
    body = replaceImages(body, articleUrl);
    body = body.replaceAll("(?is)<br\\s*/?>", "\n");
    body = body.replaceAll("(?is)</?(p|div|li|section|article|main|h[1-6])[^>]*>", "\n");
    body = TAG_BLOCK.matcher(body).replaceAll(" ");
    return normalize(HtmlUtils.htmlUnescape(body));
  }

  private static String replaceImages(String body, String articleUrl) {
    Matcher images = IMAGE_BLOCK.matcher(body);
    StringBuffer result = new StringBuffer();
    while (images.find()) {
      String imageUrl = imageUrl(images.group(), articleUrl);
      String replacement = imageUrl.isBlank()
          ? "\n[图片]\n"
          : "\n" + IMAGE_PREFIX + imageUrl + "\n";
      images.appendReplacement(result, Matcher.quoteReplacement(replacement));
    }
    images.appendTail(result);
    return result.toString();
  }

  private static String imageUrl(String tag, String articleUrl) {
    Map<String, String> attributes = new LinkedHashMap<>();
    Matcher matcher = ATTRIBUTE.matcher(tag);
    while (matcher.find()) {
      String raw = first(matcher.group(2), matcher.group(3), matcher.group(4));
      attributes.putIfAbsent(matcher.group(1).toLowerCase(), HtmlUtils.htmlUnescape(raw).trim());
    }
    for (String name : IMAGE_ATTRIBUTES) {
      String resolved = resolve(attributes.get(name), articleUrl);
      if (!resolved.isBlank()) {
        return resolved;
      }
    }
    return "";
  }

  private static String resolve(String imageUrl, String articleUrl) {
    if (imageUrl == null || imageUrl.isBlank()) {
      return "";
    }
    String value = imageUrl.trim();
    if (value.matches("(?is)^data:image/[^;,]+;base64,.+$")) {
      return value;
    }
    try {
      URI resolved = URI.create(value(articleUrl)).resolve(value);
      String scheme = resolved.getScheme();
      return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)
          ? resolved.toString()
          : "";
    } catch (IllegalArgumentException error) {
      return "";
    }
  }

  private static String normalize(String text) {
    return String.join("\n", value(text).lines()
        .map(String::trim)
        .filter(line -> !line.isBlank())
        .filter(line -> !isNoise(line))
        .limit(300)
        .toList());
  }

  private static boolean isNoise(String line) {
    if (List.of("Discord -> WxPusher", "PREMIUM SIGNALS", "打开 Discord 原消息", "文本内容").contains(line)) {
      return true;
    }
    if (line.startsWith("Crypto") && line.contains("CIA")) {
      return true;
    }
    return line.matches("^\\d{4}年\\d{1,2}月\\d{1,2}日\\s+\\d{1,2}:\\d{2}:\\d{2}$");
  }

  private static String first(String... values) {
    for (String item : values) {
      if (item != null) {
        return item;
      }
    }
    return "";
  }

  private static String value(String input) {
    return input == null ? "" : input;
  }
}
