package com.personal.tracker.service.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.repository.wxpusher.WxPusherNotifySettingsRepository;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class WxPusherPushClient {
  private static final String SEND_URL = "https://wxpusher.zjiecode.com/api/send/message";
  private static final String SIMPLE_PUSH_URL = "https://wxpusher.zjiecode.com/api/send/message/simple-push";
  private final Environment environment;
  private final ObjectMapper mapper;
  private final WxPusherNotifySettingsRepository notifySettingsRepository;
  private final HttpClient http;
  private final String sendUrl;
  private final String simplePushUrl;

  @Autowired
  public WxPusherPushClient(
      Environment environment,
      ObjectMapper mapper,
      WxPusherNotifySettingsRepository notifySettingsRepository) {
    this(
        environment,
        mapper,
        notifySettingsRepository,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
        SEND_URL,
        SIMPLE_PUSH_URL);
  }

  WxPusherPushClient(
      Environment environment,
      ObjectMapper mapper,
      WxPusherNotifySettingsRepository notifySettingsRepository,
      HttpClient http,
      String sendUrl,
      String simplePushUrl) {
    this.environment = environment;
    this.mapper = mapper;
    this.notifySettingsRepository = notifySettingsRepository;
    this.http = http;
    this.sendUrl = sendUrl;
    this.simplePushUrl = simplePushUrl;
  }

  public boolean isConfigured(String... prefixes) {
    return resolveTarget(prefixes).configured();
  }

  public PushResult send(String title, String content, String... prefixes) {
    try {
      PushTarget target = resolveTarget(prefixes);
      if (!target.spt().isBlank()) {
        return parse(sendSpt(target.spt(), title, content));
      }
      if (!target.configured()) {
        return new PushResult(false, "WAITING_CONFIG", "WxPusher 推送目标未配置");
      }
      return parse(sendApp(target.appToken(), target.uids(), target.topicIds(), title, content));
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      return new PushResult(false, "FAILED", "WxPusher 推送被中断");
    } catch (IOException | RuntimeException error) {
      return new PushResult(false, "FAILED", error.getMessage());
    }
  }

  private PushTarget resolveTarget(String... prefixes) {
    String spt = env(keys(prefixes, "SPT"));
    String token = env(keys(prefixes, "APP_TOKEN"));
    List<String> uids = splitList(env(keys(prefixes, "UIDS")));
    List<Integer> topicIds = parseTopicIds(splitList(env(keys(prefixes, "TOPIC_IDS"))));
    if (!spt.isBlank() || (!token.isBlank() && (!uids.isEmpty() || !topicIds.isEmpty()))) {
      return new PushTarget(spt, token, uids, topicIds);
    }
    var settings = notifySettings();
    return new PushTarget(
        blank(settings.spt()),
        blank(settings.appToken()),
        splitList(settings.uids()),
        parseTopicIds(splitList(settings.topicIds())));
  }

  private WxPusherNotifySettingsRepository.WxPusherNotifySettings notifySettings() {
    try {
      var settings = notifySettingsRepository.get();
      return settings == null
          ? new WxPusherNotifySettingsRepository.WxPusherNotifySettings("default", "", "", "", "", "", "")
          : settings;
    } catch (RuntimeException error) {
      return new WxPusherNotifySettingsRepository.WxPusherNotifySettings("default", "", "", "", "", "", "");
    }
  }

  private PushResult parse(String body) throws IOException {
    if (body == null || body.isBlank()) {
      return new PushResult(false, "FAILED", "WxPusher 返回为空");
    }
    if (body.stripLeading().startsWith("<")) {
      return new PushResult(false, "FAILED", "WxPusher 返回了 HTML，请检查 SPT 接口或凭证是否可用");
    }
    JsonNode root = mapper.readTree(body);
    String recordError = recordError(root.path("data"));
    if (!recordError.isBlank()) {
      return new PushResult(false, "FAILED", recordError);
    }
    String code = root.path("code").asText("");
    boolean ok = root.path("success").asBoolean(false) || List.of("0", "200", "1000").contains(code);
    return ok ? new PushResult(true, "SENT", "") : new PushResult(false, "FAILED", root.path("msg").asText(body));
  }

  private String recordError(JsonNode data) {
    if (!data.isArray()) {
      return "";
    }
    for (JsonNode item : data) {
      String code = item.path("code").asText("");
      boolean delivered = item.hasNonNull("sendRecordId") || item.hasNonNull("messageId");
      if (!List.of("0", "200", "1000").contains(code) && !(code.isBlank() && delivered)) {
        return item.path("status").asText(item.toString());
      }
    }
    return "";
  }

  private String sendSpt(String spt, String title, String content) throws IOException, InterruptedException {
    Map<String, Object> payload = Map.of(
        "spt", spt,
        "summary", summary(title),
        "content", htmlContent(title, content),
        "contentType", 2);
    HttpRequest request = HttpRequest.newBuilder(URI.create(simplePushUrl))
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(10))
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
        .build();
    return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
  }

  private String sendApp(
      String token,
      List<String> uids,
      List<Integer> topics,
      String title,
      String content) throws IOException, InterruptedException {
    Map<String, Object> payload = Map.of(
        "appToken", token,
        "summary", summary(title),
        "content", content,
        "contentType", 1,
        "uids", uids,
        "topicIds", topics);
    HttpRequest request = HttpRequest.newBuilder(URI.create(sendUrl))
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(10))
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
        .build();
    return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
  }

  private String summary(String title) {
    return title.length() > 100 ? title.substring(0, 100) : title;
  }

  private String htmlContent(String title, String content) {
    return "<h1>" + escapeHtml(title) + "</h1><br/><div style='white-space:pre-wrap'>"
        + escapeHtml(content) + "</div>";
  }

  private String escapeHtml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private List<Integer> parseTopicIds(List<String> values) {
    return values.stream().flatMap(value -> {
      try {
        return java.util.stream.Stream.of(Integer.parseInt(value));
      } catch (NumberFormatException error) {
        return java.util.stream.Stream.empty();
      }
    }).toList();
  }

  private List<String> splitList(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return Arrays.stream(value.split("[,;\\s]+")).map(String::trim).filter(item -> !item.isBlank()).toList();
  }

  private String env(String... keys) {
    for (String key : keys) {
      String value = environment.getProperty(key);
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return "";
  }

  private String[] keys(String[] prefixes, String suffix) {
    return Arrays.stream(prefixes)
        .filter(prefix -> prefix != null && !prefix.isBlank())
        .map(prefix -> prefix.trim() + "_WXPUSHER_" + suffix)
        .toArray(String[]::new);
  }

  private String blank(String value) {
    return value == null ? "" : value.trim();
  }

  private record PushTarget(String spt, String appToken, List<String> uids, List<Integer> topicIds) {
    private boolean configured() {
      return !spt.isBlank() || (!appToken.isBlank() && (!uids.isEmpty() || !topicIds.isEmpty()));
    }
  }

  public record PushResult(boolean ok, String status, String error) {
  }
}
