package com.personal.tracker.service.wxpusher;

import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository.WxPusherSettings;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class WxPusherMonitorLifecycle {
  private final WxPusherSettingsRepository settingsRepository;
  private final WxPusherIngestionService ingestion;
  private volatile RuntimeState state = RuntimeState.idle();
  private volatile boolean running;
  private Thread consumerThread;

  public WxPusherMonitorLifecycle(
      WxPusherSettingsRepository settingsRepository,
      WxPusherIngestionService ingestion) {
    this.settingsRepository = settingsRepository;
    this.ingestion = ingestion;
  }

  @EventListener(ApplicationReadyEvent.class)
  public synchronized void start() {
    if (running) {
      return;
    }
    running = true;
    consumerThread = new Thread(this::consumerLoop, "wxpusher-shared-consumer-loop");
    consumerThread.setDaemon(true);
    consumerThread.start();
    state = state.withRunning(true);
  }

  public synchronized void refresh() {
    interrupt(consumerThread);
  }

  public RuntimeState runtimeState() {
    return state;
  }

  @PreDestroy
  public synchronized void stop() {
    running = false;
    interrupt(consumerThread);
    state = state.withRunning(false);
  }

  private void consumerLoop() {
    while (running) {
      try {
        WxPusherSettings settings = settingsRepository.get();
        state = state.withSettings(settings);
        if (!settings.enablePolling() && !settings.enableWebsocket()) {
          sleepSeconds(10);
          continue;
        }
        ingestion.ensureMessageSessions(60);
        ingestion.seedHistory();
        int imported = ingestion.importPending();
        state = state.withSettings(settingsRepository.get());
        sleepSeconds(imported > 0 ? 2 : 10);
      } catch (RuntimeException error) {
        state = state.withError(shortMessage(error));
        sleepSeconds(10);
      }
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

  private static String sharedState(WxPusherSettings settings) {
    if (settings.enableWebsocket()) {
      return "SHARED";
    }
    if (settings.enablePolling()) {
      return "POLLING_ONLY";
    }
    return "IDLE";
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

    RuntimeState withSettings(WxPusherSettings settings) {
      return new RuntimeState(
          running,
          sharedState(settings),
          settings.lastPollAt(),
          settings.lastHeartbeatAt(),
          settings.lastError());
    }

    RuntimeState withError(String nextError) {
      return new RuntimeState(running, websocketState, lastPollAt, lastHeartbeatAt, nextError);
    }
  }
}
