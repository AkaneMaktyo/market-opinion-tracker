package com.personal.tracker.service.youtube;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Component
public class YouTubeClient {
  private static final Pattern DIRECT_CHANNEL = Pattern.compile("/channel/(UC[\\w-]+)");
  private final HttpClient client = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();

  public ChannelMetadata resolveChannel(String source) {
    String normalized = source == null ? "" : source.trim();
    String directId = directChannelId(normalized);
    String url = normalized.startsWith("http")
        ? normalized
        : "https://www.youtube.com/channel/" + (directId.isBlank() ? normalized : directId);
    String html = fetch(url, "text/html,application/xhtml+xml");
    String channelId = firstNonBlank(
        directId,
        search(html, "\"channelId\":\"(UC[\\w-]+)\""),
        search(html, "\"externalId\":\"(UC[\\w-]+)\""),
        search(html, "\"browseId\":\"(UC[\\w-]+)\""),
        search(html, "<meta itemprop=\"channelId\" content=\"(UC[\\w-]+)\""));
    if (channelId.isBlank()) {
      throw new IllegalArgumentException(
          "无法识别 YouTube 频道，请直接填写频道链接、@handle 或 UC 开头的频道 ID");
    }
    return new ChannelMetadata(
        channelId,
        firstNonBlank(search(html, "<meta property=\"og:title\" content=\"([^\"]+)\""), channelId),
        search(html, "\"canonicalBaseUrl\":\"(/@[^\\\"]+)\""),
        normalized);
  }

  public List<VideoMetadata> listVideos(String channelId, int limit) {
    String xml = fetch("https://www.youtube.com/feeds/videos.xml?channel_id=" + channelId, "application/xml,text/xml");
    return parseFeed(xml, limit);
  }

  static List<VideoMetadata> parseFeed(String xml, int limit) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setNamespaceAware(true);
      Document document = factory.newDocumentBuilder()
          .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      NodeList entries = document.getElementsByTagNameNS("http://www.w3.org/2005/Atom", "entry");
      List<VideoMetadata> videos = new ArrayList<>();
      for (int i = 0; i < entries.getLength() && videos.size() < Math.max(1, limit); i++) {
        Element entry = (Element) entries.item(i);
        String videoId = text(entry, "http://www.youtube.com/xml/schemas/2015", "videoId");
        videos.add(new VideoMetadata(
            videoId,
            text(entry, "http://www.w3.org/2005/Atom", "title"),
            "https://www.youtube.com/watch?v=" + videoId,
            text(entry, "http://www.w3.org/2005/Atom", "published")));
      }
      return videos;
    } catch (Exception error) {
      throw new IllegalArgumentException("读取 YouTube 视频列表失败: " + error.getMessage(), error);
    }
  }

  static String directChannelId(String source) {
    if (source == null || source.isBlank()) {
      return "";
    }
    if (source.startsWith("UC")) {
      return source.trim();
    }
    Matcher matcher = DIRECT_CHANNEL.matcher(source);
    return matcher.find() ? matcher.group(1) : "";
  }

  static String search(String text, String pattern) {
    Matcher matcher = Pattern.compile(pattern).matcher(text == null ? "" : text);
    return matcher.find() ? matcher.group(1) : "";
  }

  private String fetch(String url, String accept) {
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url))
          .header("User-Agent", "Mozilla/5.0")
          .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
          .header("Accept", accept)
          .GET()
          .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() >= 400) {
        throw new IllegalArgumentException("YouTube 请求失败: " + response.statusCode());
      }
      return response.body();
    } catch (IllegalArgumentException error) {
      throw error;
    } catch (Exception error) {
      throw new IllegalArgumentException("请求 YouTube 失败: " + error.getMessage(), error);
    }
  }

  private static String text(Element parent, String namespace, String localName) {
    NodeList nodes = parent.getElementsByTagNameNS(namespace, localName);
    return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent();
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return "";
  }

  public record ChannelMetadata(String channelId, String title, String handle, String sourceUrl) {
  }

  public record VideoMetadata(String videoId, String title, String videoUrl, String publishedAt) {
  }
}
