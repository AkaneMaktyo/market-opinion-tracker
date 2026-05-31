package com.personal.tracker.service.wxpusher;

import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository.WxPusherSettings;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class WxPusherArticleExtractor {
  private static final Pattern MAIN_BLOCK = Pattern.compile(
      "(?is)<main[^>]*class=[\"'][^\"']*article-content[^\"']*[\"'][^>]*>(.*?)</main>");
  private static final Pattern SCRIPT_BLOCK = Pattern.compile("(?is)<(script|style|svg).*?</\\1>");
  private static final Pattern IMAGE_BLOCK = Pattern.compile("(?is)<img[^>]*>");
  private static final Pattern TAG_BLOCK = Pattern.compile("(?is)<[^>]+>");
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(20))
      .build();

  public String fetchText(String url, WxPusherSettings settings) {
    validateUrl(url);
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
          .header("deviceToken", settings.deviceToken())
          .header("version", settings.version())
          .header("platform", settings.platform())
          .header("User-Agent", "Mozilla/5.0")
          .timeout(Duration.ofSeconds(20))
          .GET()
          .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return normalize(extract(response.body()));
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("抓取 WxPusher 正文被中断", error);
    } catch (IOException error) {
      throw new IllegalStateException("抓取 WxPusher 正文失败: " + error.getMessage(), error);
    }
  }

  private void validateUrl(String url) {
    if (url == null || !url.startsWith("https://wxpusher.zjiecode.com/api/message/")) {
      throw new IllegalArgumentException("仅支持 WxPusher 详情链接");
    }
  }

  private String extract(String html) {
    var matcher = MAIN_BLOCK.matcher(html == null ? "" : html);
    String body = matcher.find() ? matcher.group(1) : html;
    body = SCRIPT_BLOCK.matcher(body).replaceAll(" ");
    body = IMAGE_BLOCK.matcher(body).replaceAll("\n[图片]\n");
    body = body.replaceAll("(?is)<br\\s*/?>", "\n");
    body = body.replaceAll("(?is)</?(p|div|li|section|article|main|h[1-6])[^>]*>", "\n");
    body = TAG_BLOCK.matcher(body).replaceAll(" ");
    return HtmlUtils.htmlUnescape(body);
  }

  private String normalize(String text) {
    return String.join("\n", text.lines()
        .map(String::trim)
        .filter(line -> !line.isBlank())
        .filter(line -> !isNoise(line))
        .limit(300)
        .toList());
  }

  private boolean isNoise(String line) {
    if (List.of("Discord -> WxPusher", "PREMIUM SIGNALS", "打开 Discord 原消息", "文本内容").contains(line)) {
      return true;
    }
    if (line.startsWith("Crypto") && line.contains("CIA")) {
      return true;
    }
    return line.matches("^\\d{4}年\\d{1,2}月\\d{1,2}日\\s+\\d{1,2}:\\d{2}:\\d{2}$");
  }
}
