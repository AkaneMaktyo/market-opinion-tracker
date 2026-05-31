package com.personal.tracker.service.wxpusher;

import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository.WxPusherSettings;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class WxPusherMonitorLifecycle {
  private static final int[] RECONNECT_DELAYS = {5, 10, 20, 30, 60};
  private final WxPusherSettingsRepository settingsRepository;
  private final WxPusherClient client;
  private final WxPusherIngestionService ingestion;
  private final ExecutorService processingPool = Executors.newFixedThreadPool(3);
  private final AtomicReference<WebSocket> currentSocket = new AtomicReference<>();
  private volatile RuntimeState state = RuntimeState.idle();
  private volatile boolean running;
  private Thread pollThread;
  private Thread websocketThread;

  public WxPusherMonitorLifecycle(
      WxPusherSettingsRepository settingsRepository,
      WxPusherClient client,
      WxPusherIngestionService ingestion) {
    this.settingsRepository = settingsRepository;
    this.client = client;
    this.ingestion = ingestion;
  }

  @EventListener(ApplicationReadyEvent.class)
  public synchronized void start() {
    if (running) {
      return;
    }
    running = true;
    pollThread = new Thread(this::pollLoop, "wxpusher-poll-loop");
    websocketThread = new Thread(this::websocketLoop, "wxpusher-websocket-loop");
    pollThread.setDaemon(true);
    websocketThread.setDaemon(true);
    pollThread.start();
    websocketThread.start();
    state = state.withRunning(true);
  }

  public synchronized void refresh() {
    closeSocket();
    interrupt(pollThread);
    interrupt(websocketThread);
  }

  public RuntimeState runtimeState() {
    return state;
  }

  @PreDestroy
  public synchronized void stop() {
    running = false;
    closeSocket();
    interrupt(pollThread);
    interrupt(websocketThread);
    processingPool.shutdownNow();
    state = state.withRunning(false);
  }

  private void pollLoop() {
    while (running) {
      try {
        WxPusherSettings settings = settingsRepository.get();
        if (!settings.pollingReady()) {
          clearError();
          sleepSeconds(10);
          continue;
        }
        ingestion.seedHistory();
        for (var message : client.fetchLatest(settings)) {
          processingPool.submit(() -> ingestion.ingest(message));
        }
        state = state.withLastPollAt(JdbcSupport.now());
        clearError();
        sleepSeconds(settings.pollIntervalSeconds());
      } catch (WxPusherClient.LoginRequiredException error) {
        setError(error.getMessage());
        sleepSeconds(20);
      } catch (RuntimeException error) {
        setError("REST 轮询失败: " + shortMessage(error));
        sleepSeconds(20);
      }
    }
  }

  private void websocketLoop() {
    HttpClient websocketClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();
    int attempt = 0;
    while (running) {
      WxPusherSettings settings = settingsRepository.get();
      if (!settings.websocketReady()) {
        sleepSeconds(10);
        attempt = 0;
        continue;
      }
      try {
        state = state.withWebsocketState("CONNECTING");
        CompletableFuture<Void> closed = new CompletableFuture<>();
        WebSocket socket = websocketClient.newWebSocketBuilder()
            .buildAsync(URI.create(client.websocketUrl(settings)), new Listener(closed))
            .join();
        currentSocket.set(socket);
        state = state.withWebsocketState("CONNECTED");
        clearError();
        attempt = 0;
        while (running && !closed.isDone() && currentSocket.get() == socket) {
          socket.sendText("{\"msgType\":101}", true).join();
          sleepSeconds(25);
        }
        closed.orTimeout(5, TimeUnit.SECONDS).exceptionally(error -> null).join();
      } catch (RuntimeException error) {
        setError("WebSocket 断开: " + shortMessage(error));
        state = state.withWebsocketState("RECONNECTING");
        sleepSeconds(RECONNECT_DELAYS[Math.min(attempt, RECONNECT_DELAYS.length - 1)]);
        attempt++;
      } finally {
        closeSocket();
      }
    }
  }

  private void setHeartbeat() {
    String now = JdbcSupport.now();
    settingsRepository.updateRuntime(now, state.lastError());
    state = state.withHeartbeat(now);
  }

  private void setError(String message) {
    settingsRepository.updateRuntime(state.lastHeartbeatAt(), message);
    state = state.withError(message);
  }

  private void clearError() {
    settingsRepository.updateRuntime(state.lastHeartbeatAt(), "");
    state = state.withError("");
  }

  private void closeSocket() {
    WebSocket socket = currentSocket.getAndSet(null);
    if (socket != null) {
      socket.abort();
    }
  }

  private void interrupt(Thread thread) {
    if (thread != null) {
      thread.interrupt();
    }
  }

  private void sleepSeconds(int seconds) {
    try {
      Thread.sleep(Math.max(1, seconds) * 1000L);
    } catch (InterruptedException error) {
      if (!running) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private String shortMessage(Throwable error) {
    String message = error.getMessage();
    return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
  }

  private final class Listener implements WebSocket.Listener {
    private final CompletableFuture<Void> closed;

    private Listener(CompletableFuture<Void> closed) {
      this.closed = closed;
    }

    @Override
    public void onOpen(WebSocket webSocket) {
      setHeartbeat();
      WebSocket.Listener.super.onOpen(webSocket);
      webSocket.request(1);
    }

    @Override
    public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      var payload = client.parseRealtimePayload(data.toString());
      if ("HEARTBEAT".equals(payload.kind()) || "INIT".equals(payload.kind())) {
        setHeartbeat();
        if (!payload.pushTokenHint().isBlank()
            && !payload.pushTokenHint().equals(settingsRepository.get().pushToken())) {
          setError("WxPusher 返回了新的 pushToken，请更新设置");
        }
      }
      if ("MESSAGE".equals(payload.kind()) && payload.message() != null) {
        processingPool.submit(() -> ingestion.ingest(payload.message()));
      }
      webSocket.request(1);
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public java.util.concurrent.CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
      closed.complete(null);
      return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
      closed.completeExceptionally(error);
    }
  }

  public record RuntimeState(
      boolean running,
      String websocketState,
      String lastPollAt,
      String lastHeartbeatAt,
      String lastError) {
    static RuntimeState idle() {
      return new RuntimeState(false, "IDLE", "", "", "");
    }

    RuntimeState withRunning(boolean nextRunning) {
      return new RuntimeState(nextRunning, websocketState, lastPollAt, lastHeartbeatAt, lastError);
    }

    RuntimeState withWebsocketState(String nextState) {
      return new RuntimeState(running, nextState, lastPollAt, lastHeartbeatAt, lastError);
    }

    RuntimeState withLastPollAt(String nextPollAt) {
      return new RuntimeState(running, websocketState, nextPollAt, lastHeartbeatAt, lastError);
    }

    RuntimeState withHeartbeat(String nextHeartbeatAt) {
      return new RuntimeState(running, websocketState, lastPollAt, nextHeartbeatAt, lastError);
    }

    RuntimeState withError(String nextError) {
      return new RuntimeState(running, websocketState, lastPollAt, lastHeartbeatAt, nextError);
    }
  }
}
