package com.personal.tracker.service.wxpusher.ocr;

import com.personal.tracker.config.WxPusherOcrProperties;
import com.personal.tracker.service.wxpusher.article.WxPusherArticleParser;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class WxPusherImageOcrService {
  private static final Pattern IMAGE = Pattern.compile(
      "(?m)^" + WxPusherArticleParser.IMAGE_PREFIX
          + "((?:https?://|data:image/)[^\\r\\n]+)$");
  private static final Pattern OCR_BLOCK = Pattern.compile(
      "(?ms)^\\[图片转文字 \\d+]\\s*.*?^\\[/图片转文字]\\s*");
  private final WxPusherOcrProperties properties;
  private final ImageOcrClient client;

  public WxPusherImageOcrService(
      WxPusherOcrProperties properties,
      ImageOcrClient client) {
    this.properties = properties;
    this.client = client;
  }

  public String convert(String bloggerName, String detailText) {
    String text = deduplicateOcrBlocks(detailText == null ? "" : detailText);
    if (containsOcrText(text)) {
      return text;
    }
    Matcher matcher = IMAGE.matcher(text);
    List<String> imageUrls = new ArrayList<>();
    while (matcher.find()) {
      imageUrls.add(matcher.group(1));
    }
    if (imageUrls.isEmpty()) {
      return text;
    }
    if (!isTargetGroup(bloggerName, text)) {
      return text;
    }
    if (!properties.enabled()) {
      throw new IllegalStateException("顺哥vip小群图片 OCR 已禁用");
    }
    if (imageUrls.size() > properties.maxImages()) {
      throw new IllegalStateException(
          "单条消息图片数量 " + imageUrls.size() + " 超过 OCR 上限 " + properties.maxImages());
    }
    Matcher replacer = IMAGE.matcher(text);
    StringBuffer converted = new StringBuffer();
    int index = 0;
    while (replacer.find()) {
      String source = replacer.group();
      String ocrText = normalize(client.recognize(replacer.group(1)));
      String block = source + "\n[图片转文字 " + (++index) + "]\n"
          + ocrText + "\n[/图片转文字]";
      replacer.appendReplacement(converted, Matcher.quoteReplacement(block));
    }
    replacer.appendTail(converted);
    return converted.toString();
  }

  public boolean requiresSourceRefresh(String bloggerName, String detailText) {
    String text = detailText == null ? "" : detailText;
    return isTargetGroup(bloggerName, text)
        && (text.contains("[图片]") || text.contains("[图片转文字 "))
        && !IMAGE.matcher(text).find();
  }

  public boolean containsOcrText(String detailText) {
    return detailText != null && detailText.contains("[图片转文字 ");
  }

  String deduplicateOcrBlocks(String detailText) {
    Matcher matcher = OCR_BLOCK.matcher(detailText == null ? "" : detailText);
    var seen = new LinkedHashSet<String>();
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      String block = matcher.group().trim();
      matcher.appendReplacement(result, Matcher.quoteReplacement(seen.add(block) ? block + "\n" : ""));
    }
    matcher.appendTail(result);
    return result.toString().replaceAll("\\n{3,}", "\n\n").trim();
  }

  private boolean isTargetGroup(String bloggerName, String detailText) {
    String target = normalizeGroup(properties.groupName());
    return normalizeGroup(bloggerName).equals(target)
        || normalizeGroup(detailText).contains(target);
  }

  private static String normalize(String text) {
    return String.join("\n", text.lines()
        .map(String::trim)
        .filter(line -> !line.isBlank())
        .toList());
  }

  private static String normalizeGroup(String name) {
    return (name == null ? "" : name)
        .replaceAll("\\s+", "")
        .toLowerCase(Locale.ROOT);
  }
}
