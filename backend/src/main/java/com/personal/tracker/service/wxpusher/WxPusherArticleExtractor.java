package com.personal.tracker.service.wxpusher;

import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository.WxPusherSettings;
import com.personal.tracker.service.wxpusher.article.WxPusherArticleParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class WxPusherArticleExtractor {
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
      return WxPusherArticleParser.parse(response.body(), url);
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

}
