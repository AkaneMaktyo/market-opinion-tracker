package com.personal.tracker.service.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.repository.wxpusher.WxPusherNotifySettingsRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class WxPusherPushClientTest {
  @Test
  void isConfiguredSupportsFallbackPrefixes() {
    var env = new MockEnvironment()
        .withProperty("POSITION_NOTIFY_WXPUSHER_APP_TOKEN", "token-1")
        .withProperty("POSITION_NOTIFY_WXPUSHER_UIDS", "uid-1");
    var client = new WxPusherPushClient(env, new ObjectMapper(), mock(WxPusherNotifySettingsRepository.class));

    assertTrue(client.isConfigured("YOUTUBE", "RESONANCE", "POSITION_NOTIFY"));
  }

  @Test
  void sendReturnsWaitingConfigWhenTargetMissing() {
    var client = new WxPusherPushClient(
        new MockEnvironment(),
        new ObjectMapper(),
        mock(WxPusherNotifySettingsRepository.class));

    WxPusherPushClient.PushResult result = client.send("title", "content", "YOUTUBE");

    assertFalse(result.ok());
    assertEquals("WAITING_CONFIG", result.status());
  }

  @Test
  void isConfiguredFallsBackToStoredNotifySettings() {
    var repository = mock(WxPusherNotifySettingsRepository.class);
    when(repository.get()).thenReturn(new WxPusherNotifySettingsRepository.WxPusherNotifySettings(
        "default", "", "token-2", "uid-2 uid-3", "", "", ""));
    var client = new WxPusherPushClient(new MockEnvironment(), new ObjectMapper(), repository);

    assertTrue(client.isConfigured("YOUTUBE"));
  }

  @Test
  void sendUsesSimplePushForSptTargets() throws Exception {
    var mapper = new ObjectMapper();
    var bodyRef = new AtomicReference<String>();
    var methodRef = new AtomicReference<String>();
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/simple-push", exchange -> {
      methodRef.set(exchange.getRequestMethod());
      bodyRef.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] response = "{\"success\":true,\"code\":1000,\"data\":[]}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, response.length);
      try (OutputStream output = exchange.getResponseBody()) {
        output.write(response);
      }
    });
    server.start();
    try {
      var env = new MockEnvironment().withProperty("YOUTUBE_WXPUSHER_SPT", "spt-1");
      var client = new WxPusherPushClient(
          env,
          mapper,
          mock(WxPusherNotifySettingsRepository.class),
          HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
          "http://127.0.0.1:" + server.getAddress().getPort() + "/send",
          "http://127.0.0.1:" + server.getAddress().getPort() + "/simple-push");

      WxPusherPushClient.PushResult result = client.send("标题", "正文", "YOUTUBE");

      assertTrue(result.ok());
      assertEquals("SENT", result.status());
      assertEquals("POST", methodRef.get());
      JsonNode payload = mapper.readTree(bodyRef.get());
      assertEquals("spt-1", payload.path("spt").asText());
      assertEquals("标题", payload.path("summary").asText());
      assertEquals(2, payload.path("contentType").asInt());
      assertTrue(payload.path("content").asText().contains("white-space:pre-wrap"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void sendReportsHtmlResponseClearly() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/simple-push", exchange -> {
      byte[] response = "<html>bad gateway</html>".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(502, response.length);
      try (OutputStream output = exchange.getResponseBody()) {
        output.write(response);
      }
    });
    server.start();
    try {
      var env = new MockEnvironment().withProperty("YOUTUBE_WXPUSHER_SPT", "spt-1");
      var client = new WxPusherPushClient(
          env,
          new ObjectMapper(),
          mock(WxPusherNotifySettingsRepository.class),
          HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
          "http://127.0.0.1:" + server.getAddress().getPort() + "/send",
          "http://127.0.0.1:" + server.getAddress().getPort() + "/simple-push");

      WxPusherPushClient.PushResult result = client.send("标题", "正文", "YOUTUBE");

      assertFalse(result.ok());
      assertEquals("FAILED", result.status());
      assertNotNull(result.error());
      assertTrue(result.error().contains("HTML"));
    } finally {
      server.stop(0);
    }
  }
}
