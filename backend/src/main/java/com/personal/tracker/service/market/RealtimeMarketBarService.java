package com.personal.tracker.service.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.domain.Instrument;
import com.personal.tracker.domain.MarketBar;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.repository.MarketBarRepository;
import com.personal.tracker.service.MarketDataService;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class RealtimeMarketBarService {
  private static final URI BITGET_PUBLIC_WS = URI.create("wss://ws.bitget.com/v2/ws/public");
  private static final long WEBSOCKET_RETRY_MS = TimeUnit.MINUTES.toMillis(1);
  private static final Map<String, String> CHANNELS = Map.of(
      "1H", "candle1H",
      "4H", "candle4H",
      "1D", "candle1D");

  private final ObjectMapper mapper;
  private final InstrumentRepository instruments;
  private final MarketBarRepository bars;
  private final MarketDataService marketData;
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(8))
      .build();
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
    Thread thread = new Thread(task, "market-realtime-watchdog");
    thread.setDaemon(true);
    return thread;
  });
  private final ExecutorService poller = Executors.newFixedThreadPool(2, task -> {
    Thread thread = new Thread(task, "market-realtime-poller");
    thread.setDaemon(true);
    return thread;
  });
  private final Map<String, StreamState> streams = new ConcurrentHashMap<>();

  public RealtimeMarketBarService(
      ObjectMapper mapper,
      InstrumentRepository instruments,
      MarketBarRepository bars,
      MarketDataService marketData) {
    this.mapper = mapper;
    this.instruments = instruments;
    this.bars = bars;
    this.marketData = marketData;
    scheduler.scheduleAtFixedRate(this::watchStreams, 5, 10, TimeUnit.SECONDS);
  }

  public SseEmitter stream(String symbol, String timeframe) {
    Instrument instrument = instruments.saveIfAbsent(symbol, symbol, "US", null);
    String frame = normalizeFrame(timeframe);
    SseEmitter emitter = new SseEmitter(0L);
    StreamState state = streams.computeIfAbsent(key(instrument.id(), frame), ignored ->
        new StreamState(instrument, frame, bitgetTopic(instrument, frame)));
    state.emitters.add(emitter);
    emitter.onCompletion(() -> removeEmitter(state, emitter));
    emitter.onTimeout(() -> removeEmitter(state, emitter));
    emitter.onError(error -> removeEmitter(state, emitter));
    sendStatus(emitter, "connecting");
    pollLatest(state);
    connectIfNeeded(state);
    return emitter;
  }

  private void connectIfNeeded(StreamState state) {
    if (!supportsBitget(state.instrument)) {
      broadcastStatus(state, "polling");
      return;
    }
    long now = System.currentTimeMillis();
    if (now - state.lastConnectAttempt < WEBSOCKET_RETRY_MS) {
      return;
    }
    if (state.websocket != null || !state.connecting.compareAndSet(false, true)) {
      return;
    }
    state.lastConnectAttempt = now;
    http.newWebSocketBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .buildAsync(BITGET_PUBLIC_WS, new BitgetListener(state))
        .whenComplete((socket, error) -> {
          state.connecting.set(false);
          if (error != null) {
            broadcastStatus(state, "polling");
            pollLatest(state);
            return;
          }
          if (state.emitters.isEmpty()) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "no subscribers");
            return;
          }
          state.websocket = socket;
          state.lastMessageAt = System.currentTimeMillis();
          socket.sendText(subscriptionPayload(state), true);
          broadcastStatus(state, "subscribed");
        });
  }

  private void watchStreams() {
    streams.values().forEach(state -> {
      if (state.emitters.isEmpty()) {
        flushPending(state);
        close(state);
        streams.remove(key(state.instrument.id(), state.timeframe));
        return;
      }
      flushPending(state);
      sendHeartbeat(state);
      WebSocket socket = state.websocket;
      if (socket == null) {
        pollLatest(state);
        connectIfNeeded(state);
        return;
      }
      if (System.currentTimeMillis() - state.lastMessageAt > TimeUnit.SECONDS.toMillis(75)) {
        close(state);
        pollLatest(state);
        connectIfNeeded(state);
        return;
      }
      socket.sendText("ping", true).whenComplete((ignored, error) -> {
        if (error == null || state.emitters.isEmpty()) {
          return;
        }
        close(state);
        connectIfNeeded(state);
      });
    });
  }

  private void handleMessage(StreamState state, String message) {
    state.lastMessageAt = System.currentTimeMillis();
    if (message == null || message.isBlank() || "pong".equalsIgnoreCase(message)) {
      return;
    }
    try {
      JsonNode root = mapper.readTree(message);
      if (root.has("event") && !"0".equals(root.path("code").asText("0"))) {
        broadcastStatus(state, "polling");
        close(state);
        pollLatest(state);
        return;
      }
      JsonNode data = root.path("data");
      if (!data.isArray()) {
        return;
      }
      for (JsonNode row : data) {
        parseBar(state, row).ifPresent(bar -> publishBar(state, bar, "live"));
      }
    } catch (IOException ignored) {
      broadcastStatus(state, "parse_error");
    }
  }

  private java.util.Optional<MarketBar> parseBar(StreamState state, JsonNode row) {
    if (!row.isArray() || row.size() < 6) {
      return java.util.Optional.empty();
    }
    MarketBar bar = new MarketBar(
        JdbcSupport.id(),
        state.instrument.id(),
        state.timeframe,
        MarketBarSupport.time(state.timeframe, row.get(0).asLong()),
        MarketBarSupport.decimal(row.get(1)),
        MarketBarSupport.decimal(row.get(2)),
        MarketBarSupport.decimal(row.get(3)),
        MarketBarSupport.decimal(row.get(4)),
        MarketBarSupport.decimal(row.get(5)));
    return java.util.Optional.of(bar);
  }

  private void broadcastBar(StreamState state, MarketBar bar) {
    state.emitters.forEach(emitter -> {
      try {
        emitter.send(SseEmitter.event().name("bar").data(bar));
      } catch (IOException error) {
        removeEmitter(state, emitter);
      }
    });
  }

  private void publishBar(StreamState state, MarketBar bar, String status) {
    String signature = bar.barTime() + ':' + bar.open() + ':' + bar.high()
        + ':' + bar.low() + ':' + bar.close() + ':' + bar.volume();
    if (signature.equals(state.lastBarSignature)) {
      return;
    }
    state.lastBarSignature = signature;
    state.pendingBar = bar;
    broadcastBar(state, bar);
    broadcastStatus(state, status);
  }

  private void pollLatest(StreamState state) {
    long now = System.currentTimeMillis();
    if (now - state.lastPollAt < TimeUnit.SECONDS.toMillis(8)
        || !state.polling.compareAndSet(false, true)) {
      return;
    }
    state.lastPollAt = now;
    poller.submit(() -> {
      try {
        marketData.fetchLatestBar(state.instrument, state.timeframe)
            .ifPresent(bar -> publishBar(state, bar, "polling"));
      } catch (RuntimeException error) {
        broadcastStatus(state, "delayed");
      } finally {
        state.polling.set(false);
      }
    });
  }

  private void flushPending(StreamState state) {
    MarketBar pending = state.pendingBar;
    if (pending == null) {
      return;
    }
    state.pendingBar = null;
    try {
      bars.saveAll(List.of(pending));
    } catch (RuntimeException error) {
      if (state.pendingBar == null) {
        state.pendingBar = pending;
      }
    }
  }

  private void sendHeartbeat(StreamState state) {
    state.emitters.forEach(emitter -> {
      try {
        emitter.send(SseEmitter.event().comment("keepalive"));
      } catch (IOException error) {
        removeEmitter(state, emitter);
      }
    });
  }

  private void broadcastStatus(StreamState state, String status) {
    state.emitters.forEach(emitter -> sendStatus(emitter, status));
  }

  private void sendStatus(SseEmitter emitter, String status) {
    try {
      emitter.send(SseEmitter.event().name("status").reconnectTime(2000).data(status));
    } catch (IOException ignored) {
      emitter.complete();
    }
  }

  private void removeEmitter(StreamState state, SseEmitter emitter) {
    state.emitters.remove(emitter);
    if (state.emitters.isEmpty()) {
      close(state);
      streams.remove(key(state.instrument.id(), state.timeframe));
    }
  }

  private void close(StreamState state) {
    WebSocket socket = state.websocket;
    state.websocket = null;
    if (socket != null) {
      socket.sendClose(WebSocket.NORMAL_CLOSURE, "no subscribers");
    }
  }

  private String subscriptionPayload(StreamState state) {
    return """
        {"op":"subscribe","args":[{"instType":"%s","channel":"%s","instId":"%s"}]}
        """.formatted(state.topic.instType(), state.topic.channel(), state.topic.instId()).trim();
  }

  private static BitgetTopic bitgetTopic(Instrument instrument, String timeframe) {
    String instType = present(instrument.bitgetCategory()) ? instrument.bitgetCategory() : "USDT-FUTURES";
    String instId = present(instrument.bitgetSymbol())
        ? instrument.bitgetSymbol()
        : MarketBarSupport.cleanSymbol(instrument.symbol()) + "USDT";
    return new BitgetTopic(instType, CHANNELS.getOrDefault(timeframe, "candle1D"), instId);
  }

  private static String normalizeFrame(String timeframe) {
    return timeframe == null || timeframe.isBlank() ? "1D" : timeframe.trim().toUpperCase();
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  private static boolean supportsBitget(Instrument instrument) {
    if (present(instrument.bitgetCategory()) && present(instrument.bitgetSymbol())) {
      return true;
    }
    return "CRYPTO".equalsIgnoreCase(instrument.market())
        || "CRYPTO".equals(JdbcSupport.market(null, instrument.symbol()));
  }

  private static String key(String instrumentId, String timeframe) {
    return instrumentId + ":" + timeframe;
  }

  @PreDestroy
  public void shutdown() {
    scheduler.shutdownNow();
    poller.shutdownNow();
    streams.values().forEach(state -> {
      flushPending(state);
      close(state);
    });
  }

  private class BitgetListener implements WebSocket.Listener {
    private final StreamState state;
    private final StringBuilder buffer = new StringBuilder();

    BitgetListener(StreamState state) {
      this.state = state;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      buffer.append(data);
      if (last) {
        handleMessage(state, buffer.toString());
        buffer.setLength(0);
      }
      webSocket.request(1);
      return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      state.websocket = null;
      broadcastStatus(state, "polling");
      pollLatest(state);
      return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      state.websocket = null;
      broadcastStatus(state, "polling");
      pollLatest(state);
    }
  }

  private record BitgetTopic(String instType, String channel, String instId) {
  }

  private static class StreamState {
    final Instrument instrument;
    final String timeframe;
    final BitgetTopic topic;
    final Set<SseEmitter> emitters = new CopyOnWriteArraySet<>();
    final AtomicBoolean connecting = new AtomicBoolean(false);
    final AtomicBoolean polling = new AtomicBoolean(false);
    volatile WebSocket websocket;
    volatile long lastMessageAt;
    volatile long lastConnectAttempt;
    volatile long lastPollAt;
    volatile String lastBarSignature = "";
    volatile MarketBar pendingBar;

    StreamState(Instrument instrument, String timeframe, BitgetTopic topic) {
      this.instrument = instrument;
      this.timeframe = timeframe;
      this.topic = topic;
    }
  }
}
