package com.personal.tracker.service.wxpusher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository.WxPusherSettings;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WxPusherClient {
  private static final String LIST_URL = "https://wxpusher.zjiecode.com/api/need-login/device/message/list-v2";
  private static final String MAX_MESSAGE_ID = "9223372036854775807";
  private final ObjectMapper mapper;
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(20))
      .build();

  public WxPusherClient(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public String maxCursor() {
    return MAX_MESSAGE_ID;
  }

  public List<IncomingMessage> fetchLatest(WxPusherSettings settings) {
    return fetchPage(settings, MAX_MESSAGE_ID);
  }

  public List<IncomingMessage> fetchPage(WxPusherSettings settings, String cursor) {
    try {
      String query = "messageId=" + encode(cursor) + "&scene=1&key=";
      HttpRequest request = HttpRequest.newBuilder(URI.create(LIST_URL + "?" + query))
          .header("deviceToken", settings.deviceToken())
          .header("version", settings.version())
          .header("platform", settings.platform())
          .header("Content-Type", "application/json;charset=UTF-8")
          .timeout(Duration.ofSeconds(20))
          .GET()
          .build();
      HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      JsonNode root = mapper.readTree(response.body());
      verifyPayload(root);
      List<IncomingMessage> items = new ArrayList<>();
      JsonNode payload = root.has("data") ? root.get("data") : root;
      for (JsonNode item : findList(payload)) {
        items.add(fromRest(item));
      }
      items.sort(Comparator.comparingLong(IncomingMessage::sortValue));
      return items;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("WxPusher REST 轮询被中断", error);
    } catch (IOException error) {
      throw new IllegalStateException("读取 WxPusher 消息失败: " + error.getMessage(), error);
    }
  }

  public String websocketUrl(WxPusherSettings settings) {
    return "wss://wxpusher.zjiecode.com/ws?version="
        + encode(settings.version())
        + "&platform="
        + encode(settings.platform())
        + "&pushToken="
        + encode(settings.pushToken());
  }

  public RealtimePayload parseRealtimePayload(String raw) {
    try {
      JsonNode root = mapper.readTree(raw);
      int msgType = root.path("msgType").asInt(-1);
      if (msgType == 201) {
        return RealtimePayload.heartbeat();
      }
      if (msgType == 202) {
        return RealtimePayload.init(root.path("pushToken").asText(""));
      }
      if (msgType == 20001) {
        return RealtimePayload.message(fromWebsocket(root));
      }
      return RealtimePayload.ignore();
    } catch (IOException error) {
      throw new IllegalStateException("解析 WxPusher WebSocket 消息失败: " + error.getMessage(), error);
    }
  }

  private void verifyPayload(JsonNode root) {
    int code = root.path("code").asInt(1000);
    if (code == 1002) {
      throw new LoginRequiredException("WxPusher 登录态失效，请重新填写 deviceToken");
    }
    if (root.path("success").isBoolean() && !root.path("success").asBoolean() && code != 1000) {
      throw new ApiException(root.path("msg").asText("WxPusher 返回失败"));
    }
  }

  private IncomingMessage fromRest(JsonNode item) {
    String detailUrl = text(item, "url");
    String sourceUrl = text(item, "sourceUrl");
    String cursor = text(item, "messageId");
    String title = text(item, "title");
    String summary = text(item, "summary");
    String sourceName = text(item, "name");
    long sortValue = item.path("createTime").asLong(System.currentTimeMillis());
    return new IncomingMessage(
        "polling",
        WxPusherMessageKey.build(sourceUrl, detailUrl, "polling", cursor, summary),
        blank(sourceName, blank(title, "WxPusher")),
        title,
        summary,
        detailUrl,
        sourceUrl,
        toIsoTime(sortValue),
        cursor,
        mapper.valueToTree(item).toString(),
        sortValue);
  }

  private IncomingMessage fromWebsocket(JsonNode item) {
    String title = text(item, "title");
    String detailUrl = text(item, "url");
    String sourceUrl = text(item, "sourceUrl");
    String fallbackId = blank(text(item, "qid"), text(item, "messageId"));
    long sortValue = item.path("createTime").asLong(System.currentTimeMillis());
    return new IncomingMessage(
        "websocket",
        WxPusherMessageKey.build(sourceUrl, detailUrl, "websocket", fallbackId, text(item, "summary")),
        blank(sourceFromTitle(title), blank(text(item, "name"), "WxPusher")),
        title,
        text(item, "summary"),
        detailUrl,
        sourceUrl,
        toIsoTime(sortValue),
        fallbackId,
        mapper.valueToTree(item).toString(),
        sortValue);
  }

  private List<JsonNode> findList(JsonNode root) {
    if (root == null || root.isNull()) {
      return List.of();
    }
    if (root.isArray()) {
      List<JsonNode> items = new ArrayList<>();
      root.forEach(items::add);
      return items;
    }
    for (String key : List.of("list", "records", "items", "messages", "rows")) {
      JsonNode child = root.path(key);
      if (child.isArray()) {
        List<JsonNode> items = new ArrayList<>();
        child.forEach(items::add);
        return items;
      }
    }
    if (root.isObject()) {
      var fields = root.fields();
      while (fields.hasNext()) {
        List<JsonNode> nested = findList(fields.next().getValue());
        if (!nested.isEmpty()) {
          return nested;
        }
      }
    }
    return List.of();
  }

  private String sourceFromTitle(String title) {
    if (title == null) {
      return "";
    }
    if (title.startsWith("您订阅的【") && title.endsWith("】有新的消息")) {
      return title.substring(5, title.length() - 6);
    }
    return "";
  }

  private String text(JsonNode node, String key) {
    JsonNode value = node.path(key);
    return value.isMissingNode() || value.isNull() ? "" : value.asText("");
  }

  private String toIsoTime(long value) {
    return Instant.ofEpochMilli(value).toString();
  }

  private String encode(String value) {
    return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  private String blank(String primary, String fallback) {
    return primary == null || primary.isBlank() ? fallback : primary.trim();
  }

  public record IncomingMessage(
      String channel,
      String messageKey,
      String bloggerName,
      String title,
      String summary,
      String detailUrl,
      String sourceUrl,
      String messageTime,
      String pageCursor,
      String rawPayloadJson,
      long sortValue) {
  }

  public record RealtimePayload(String kind, IncomingMessage message, String pushTokenHint) {
    public static RealtimePayload heartbeat() {
      return new RealtimePayload("HEARTBEAT", null, "");
    }

    public static RealtimePayload init(String pushTokenHint) {
      return new RealtimePayload("INIT", null, pushTokenHint == null ? "" : pushTokenHint.trim());
    }

    public static RealtimePayload message(IncomingMessage message) {
      return new RealtimePayload("MESSAGE", message, "");
    }

    public static RealtimePayload ignore() {
      return new RealtimePayload("IGNORE", null, "");
    }
  }

  public static class LoginRequiredException extends IllegalStateException {
    public LoginRequiredException(String message) {
      super(message);
    }
  }

  public static class ApiException extends IllegalStateException {
    public ApiException(String message) {
      super(message);
    }
  }
}
