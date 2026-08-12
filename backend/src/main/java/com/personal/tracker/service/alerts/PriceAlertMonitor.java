package com.personal.tracker.service.alerts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.repository.alerts.PriceAlertRepository;
import com.personal.tracker.repository.alerts.PriceAlertRepository.ActiveAlert;
import com.personal.tracker.service.notify.WxPusherPushClient;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PriceAlertMonitor {
  private static final URI BITGET_PUBLIC_WS = URI.create("wss://ws.bitget.com/v2/ws/public");
  private static final long WEBSOCKET_RETRY_MS = TimeUnit.MINUTES.toMillis(1);
  private static final long FALLBACK_POLL_MS = TimeUnit.SECONDS.toMillis(8);
  private final ObjectMapper mapper;
  private final PriceAlertRepository repository;
  private final WxPusherPushClient pushClient;
  private final BitgetTickerClient tickerClient;
  private final HttpClient http;
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task ->
      daemon(task, "price-alert-monitor"));
  private final ExecutorService notifier = Executors.newSingleThreadExecutor(task ->
      daemon(task, "price-alert-notifier"));
  private final AtomicBoolean connecting = new AtomicBoolean(false);
  private final AtomicBoolean fallbackPolling = new AtomicBoolean(false);
  private final Map<String, BigDecimal> previousPrices = new java.util.concurrent.ConcurrentHashMap<>();
  private volatile Map<String, List<ActiveAlert>> alertsByTopic = Map.of();
  private volatile WebSocket websocket;
  private volatile String subscribedSignature = "";
  private volatile String state = "IDLE";
  private volatile String lastError = "";
  private volatile long lastMessageAt;
  private volatile long lastConnectAttempt;
  private volatile long lastPingAt;
  private volatile long lastFallbackPollAt;

  public PriceAlertMonitor(
      ObjectMapper mapper,
      PriceAlertRepository repository,
      WxPusherPushClient pushClient,
      BitgetTickerClient tickerClient,
      @Value("${HTTP_PROXY_URL:}") String proxyUrl) {
    this.mapper = mapper;
    this.repository = repository;
    this.pushClient = pushClient;
    this.tickerClient = tickerClient;
    HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8));
    InetSocketAddress proxy = proxyAddress(proxyUrl);
    if (proxy != null) {
      builder.proxy(ProxySelector.of(proxy));
    }
    this.http = builder.build();
    scheduler.scheduleAtFixedRate(this::safeRefresh, 2, 5, TimeUnit.SECONDS);
  }

  private static InetSocketAddress proxyAddress(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      URI uri = URI.create(raw.contains("://") ? raw : "http://" + raw);
      int port = uri.getPort() == -1 ? 7897 : uri.getPort();
      return new InetSocketAddress(uri.getHost(), port);
    } catch (IllegalArgumentException error) {
      return null;
    }
  }

  public void refreshNow() {
    scheduler.execute(this::safeRefresh);
  }

  public void reset(String alertId) {
    previousPrices.remove(alertId);
    refreshNow();
  }

  public MonitorStatus status() {
    int activeCount = alertsByTopic.values().stream().mapToInt(List::size).sum();
    return new MonitorStatus(
        state,
        activeCount,
        alertsByTopic.size(),
        iso(lastMessageAt),
        lastError,
        pushClient.isConfigured("PRICE_ALERT", "POSITION_NOTIFY"));
  }

  static boolean inRange(BigDecimal price, BigDecimal lower, BigDecimal upper) {
    return price != null && price.compareTo(lower) >= 0 && price.compareTo(upper) <= 0;
  }

  static boolean crossed(BigDecimal previous, BigDecimal current, BigDecimal target) {
    return crossed(previous, current, target, "ANY");
  }

  static boolean crossed(
      BigDecimal previous, BigDecimal current, BigDecimal target, String direction) {
    if (current == null || target == null) {
      return false;
    }
    if (previous == null) {
      return false;
    }
    int before = previous.compareTo(target);
    int now = current.compareTo(target);
    if ("UP".equalsIgnoreCase(direction)) {
      return before < 0 && now >= 0;
    }
    if ("DOWN".equalsIgnoreCase(direction)) {
      return before > 0 && now <= 0;
    }
    if (now == 0) {
      return true;
    }
    return before < 0 && now > 0 || before > 0 && now < 0;
  }

  private void safeRefresh() {
    try {
      refresh();
    } catch (RuntimeException error) {
      state = "ERROR";
      lastError = message(error);
    }
  }

  private void refresh() {
    repository.recoverStaleDeliveries();
    if (pushClient.isConfigured("PRICE_ALERT", "POSITION_NOTIFY")) {
      repository.recoverMissingPushTargets();
    }
    Map<String, List<ActiveAlert>> next = repository.active().stream()
        .collect(Collectors.groupingBy(
            alert -> topicKey(alert.bitgetCategory(), alert.bitgetSymbol()),
            LinkedHashMap::new,
            Collectors.toList()));
    alertsByTopic = Map.copyOf(next);
    previousPrices.keySet().removeIf(id -> next.values().stream()
        .flatMap(List::stream)
        .noneMatch(alert -> alert.id().equals(id)));
    next.values().stream().flatMap(List::stream)
        .filter(alert -> alert.lastPrice() != null)
        .forEach(alert -> previousPrices.putIfAbsent(alert.id(), alert.lastPrice()));
    String nextSignature = next.keySet().stream().sorted().collect(Collectors.joining(","));
    if (nextSignature.isBlank()) {
      subscribedSignature = "";
      state = "IDLE";
      close("no active alerts");
      return;
    }
    if (!nextSignature.equals(subscribedSignature)) {
      subscribedSignature = nextSignature;
      close("subscriptions changed");
    }
    ensureConnected();
    heartbeat();
    if (websocket == null) {
      pollFallback();
    }
  }

  private void ensureConnected() {
    if (websocket != null || connecting.get()) {
      return;
    }
    long now = System.currentTimeMillis();
    if (now - lastConnectAttempt < WEBSOCKET_RETRY_MS) {
      return;
    }
    lastConnectAttempt = now;
    if (!"POLLING".equals(state) && !"ERROR".equals(state)) {
      state = lastMessageAt <= 0 ? "CONNECTING" : "POLLING";
    }
    if (!connecting.compareAndSet(false, true)) {
      return;
    }
    String expected = subscribedSignature;
    http.newWebSocketBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .buildAsync(BITGET_PUBLIC_WS, new Listener())
        .whenComplete((socket, error) -> onConnected(socket, error, expected));
  }

  private void onConnected(WebSocket socket, Throwable error, String expected) {
    connecting.set(false);
    if (error != null) {
      state = "POLLING";
      lastError = "";
      pollFallback();
      return;
    }
    if (!expected.equals(subscribedSignature) || expected.isBlank()) {
      socket.sendClose(WebSocket.NORMAL_CLOSURE, "stale subscription");
      return;
    }
    websocket = socket;
    lastMessageAt = System.currentTimeMillis();
    lastPingAt = 0;
    socket.sendText(subscriptionPayload(), true).whenComplete((ignored, sendError) -> {
      if (sendError != null && websocket == socket) {
        lastError = message(sendError);
        state = "POLLING";
        close("subscribe failed");
        pollFallback();
      }
    });
  }

  private String subscriptionPayload() {
    List<Map<String, String>> topics = new ArrayList<>();
    for (String key : alertsByTopic.keySet()) {
      String[] parts = key.split("\\|", 2);
      topics.add(Map.of("instType", parts[0], "channel", "ticker", "instId", parts[1]));
    }
    try {
      return mapper.writeValueAsString(Map.of("op", "subscribe", "args", topics));
    } catch (Exception error) {
      throw new IllegalStateException("生成 Bitget 订阅请求失败", error);
    }
  }

  private void heartbeat() {
    WebSocket socket = websocket;
    if (socket == null) {
      return;
    }
    long now = System.currentTimeMillis();
    if (now - lastMessageAt > TimeUnit.SECONDS.toMillis(75)) {
      state = "POLLING";
      close("ticker timeout");
      pollFallback();
      return;
    }
    if (now - lastPingAt >= TimeUnit.SECONDS.toMillis(30)) {
      lastPingAt = now;
      try {
        socket.sendText("ping", true).whenComplete((ignored, error) -> {
          if (error == null || websocket != socket) {
            return;
          }
          lastError = message(error);
          state = "POLLING";
          close("heartbeat failed");
          pollFallback();
        });
      } catch (RuntimeException error) {
        if (websocket == socket) {
          lastError = message(error);
          state = "POLLING";
          close("heartbeat failed");
          pollFallback();
        }
      }
    }
  }

  private void pollFallback() {
    long now = System.currentTimeMillis();
    if (now - lastFallbackPollAt < FALLBACK_POLL_MS
        || !fallbackPolling.compareAndSet(false, true)) {
      return;
    }
    lastFallbackPollAt = now;
    notifier.submit(() -> {
      int succeeded = 0;
      int failed = 0;
      try {
        for (Map.Entry<String, List<ActiveAlert>> entry : alertsByTopic.entrySet()) {
          ActiveAlert sample = entry.getValue().get(0);
          var quote = tickerClient.fetch(sample.bitgetCategory(), sample.bitgetSymbol());
          if (quote.isPresent()) {
            succeeded++;
            var value = quote.orElseThrow();
            evaluate(entry.getKey(), value.price(), value.timestamp());
          } else {
            failed++;
          }
        }
        if (succeeded > 0) {
          state = "POLLING";
          lastMessageAt = System.currentTimeMillis();
          lastError = failed == 0 ? "" : "部分 Bitget 标的轮询失败";
        } else if (!alertsByTopic.isEmpty()) {
          state = "ERROR";
          lastError = "Bitget WebSocket 与 REST 行情均暂时不可用";
        }
      } finally {
        fallbackPolling.set(false);
      }
    });
  }

  private void handleMessage(String message) {
    lastMessageAt = System.currentTimeMillis();
    if (message == null || message.isBlank() || "pong".equalsIgnoreCase(message)) {
      return;
    }
    try {
      JsonNode root = mapper.readTree(message);
      if (root.has("event")) {
        handleEvent(root);
        return;
      }
      String category = root.path("arg").path("instType").asText();
      String symbol = root.path("arg").path("instId").asText();
      for (JsonNode row : root.path("data")) {
        BigDecimal price = decimal(row.path("lastPr"));
        long timestamp = row.path("ts").asLong(root.path("ts").asLong(System.currentTimeMillis()));
        evaluate(topicKey(category, symbol), price, timestamp);
      }
      state = "LIVE";
      lastError = "";
    } catch (RuntimeException | java.io.IOException error) {
      lastError = "解析 Bitget ticker 失败: " + message(error);
    }
  }

  private void handleEvent(JsonNode root) {
    String code = root.path("code").asText("0");
    if (!"0".equals(code) && !"00000".equals(code)) {
      state = "ERROR";
      lastError = root.path("msg").asText("Bitget 订阅失败");
      close("subscription rejected");
      return;
    }
    state = "LIVE";
  }

  private void evaluate(String key, BigDecimal price, long timestamp) {
    if (price == null) {
      return;
    }
    String checkedAt = Instant.ofEpochMilli(timestamp).toString();
    for (ActiveAlert alert : alertsByTopic.getOrDefault(key, List.of())) {
      boolean matched = pointMatched(alert, price, checkedAt);
      if (matched && repository.claim(alert.id(), price, checkedAt)) {
        notifier.submit(() -> notify(alert, price, checkedAt));
      }
    }
  }

  private boolean pointMatched(ActiveAlert alert, BigDecimal price, String checkedAt) {
    if (!"POINT".equalsIgnoreCase(alert.alertType())) {
      return inRange(price, alert.lowerPrice(), alert.upperPrice());
    }
    BigDecimal previous = previousPrices.put(alert.id(), price);
    if (previous == null) {
      repository.observe(alert.id(), price, checkedAt);
    }
    return crossed(previous, price, alert.targetPrice(), alert.triggerDirection());
  }

  private void notify(ActiveAlert alert, BigDecimal price, String checkedAt) {
    boolean point = "POINT".equalsIgnoreCase(alert.alertType());
    String title = point
        ? "%s %s".formatted(alert.symbol(), pointTitle(alert.triggerDirection()))
        : "%s 进入价格区间".formatted(alert.symbol());
    String condition = point
        ? "%s：%s".formatted(directionLabel(alert.triggerDirection()), alert.targetPrice().toPlainString())
        : "目标区间：" + alert.lowerPrice().toPlainString() + " ～ " + alert.upperPrice().toPlainString();
    String content = """
        【价格信号提醒】
        标的：%s%s
        当前价格：%s
        %s
        Bitget 市场：%s / %s
        触发时间：%s

        本提醒为一次性提醒；如需再次监控，请在价格提醒中重新启用。
        """.formatted(
        alert.symbol(),
        alert.name() == null || alert.name().isBlank() ? "" : "（" + alert.name() + "）",
        price.toPlainString(),
        condition,
        alert.bitgetCategory(),
        alert.bitgetSymbol(),
        checkedAt);
    WxPusherPushClient.PushResult result = pushClient.send(
        title, content, "PRICE_ALERT", "POSITION_NOTIFY");
    if (result.ok()) {
      repository.markSent(alert.id(), price, checkedAt);
    } else {
      repository.markError(alert.id(), price, checkedAt, result.error());
    }
    refreshNow();
  }

  private String pointTitle(String direction) {
    return switch (direction == null ? "ANY" : direction.toUpperCase()) {
      case "UP" -> "向上突破提醒点位";
      case "DOWN" -> "向下跌破提醒点位";
      default -> "到达提醒点位";
    };
  }

  private String directionLabel(String direction) {
    return switch (direction == null ? "ANY" : direction.toUpperCase()) {
      case "UP" -> "向上突破";
      case "DOWN" -> "向下跌破";
      default -> "提醒点位";
    };
  }

  private void close(String reason) {
    WebSocket socket = websocket;
    websocket = null;
    if (socket != null) {
      try {
        socket.sendClose(WebSocket.NORMAL_CLOSURE, reason);
      } catch (RuntimeException ignored) {
        // The socket may already be closed; fallback monitoring remains available.
      }
    }
  }

  private static BigDecimal decimal(JsonNode node) {
    if (node == null || node.isMissingNode() || node.asText().isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(node.asText());
    } catch (NumberFormatException error) {
      return null;
    }
  }

  private static String topicKey(String category, String symbol) {
    return category.trim().toUpperCase() + "|" + symbol.trim().toUpperCase();
  }

  private static String iso(long epochMillis) {
    return epochMillis <= 0 ? "" : Instant.ofEpochMilli(epochMillis).toString();
  }

  private static String message(Throwable error) {
    return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
  }

  private static Thread daemon(Runnable task, String name) {
    Thread thread = new Thread(task, name);
    thread.setDaemon(true);
    return thread;
  }

  @PreDestroy
  public void shutdown() {
    close("service shutdown");
    scheduler.shutdownNow();
    notifier.shutdownNow();
  }

  private class Listener implements WebSocket.Listener {
    private final StringBuilder buffer = new StringBuilder();

    @Override
    public void onOpen(WebSocket socket) {
      socket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
      if (websocket != socket) {
        return null;
      }
      buffer.append(data);
      if (last) {
        handleMessage(buffer.toString());
        buffer.setLength(0);
      }
      socket.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
      if (websocket == socket) {
        websocket = null;
        state = alertsByTopic.isEmpty() ? "IDLE" : "POLLING";
        lastError = "";
        pollFallback();
      }
      return WebSocket.Listener.super.onClose(socket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket socket, Throwable error) {
      if (websocket == socket) {
        websocket = null;
        state = "POLLING";
        lastError = "";
        pollFallback();
      }
    }
  }

  public record MonitorStatus(
      String state,
      int activeAlerts,
      int subscribedSymbols,
      String lastMessageAt,
      String lastError,
      boolean pushReady) {
  }
}
