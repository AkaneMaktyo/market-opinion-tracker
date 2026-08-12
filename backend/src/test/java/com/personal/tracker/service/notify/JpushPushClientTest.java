package com.personal.tracker.service.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JpushPushClientTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void pushKeepsOfflineMessageAndCarriesColdStartIntent() throws Exception {
    var requestBody = new AtomicReference<String>();
    HttpServer server = server(200, "{\"sendno\":1,\"msg_id\":123}", requestBody);
    try {
      JpushPushClient client = client(server, new MockEnvironment());

      JpushPushClient.PushResult result = client.push("标题", "正文", "message-123");

      assertTrue(result.ok());
      JsonNode payload = mapper.readTree(requestBody.get());
      assertEquals(259_200, payload.path("options").path("time_to_live").asInt());
      assertEquals("message-123", payload.path("notification").path("android")
          .path("extras").path("messageId").asText());
      assertTrue(payload.path("notification").path("android").path("intent").path("url")
          .asText().contains("S.messageId=message-123"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void pushSurfacesHttpFailure() throws Exception {
    HttpServer server = server(503, "{\"message\":\"unavailable\"}", new AtomicReference<>());
    try {
      JpushPushClient.PushResult result = client(server, new MockEnvironment())
          .push("标题", "正文", "message-123");

      assertFalse(result.ok());
      assertEquals("FAILED", result.status());
      assertTrue(result.error().contains("HTTP 503"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void pushClampsConfiguredOfflineRetention() throws Exception {
    var requestBody = new AtomicReference<String>();
    HttpServer server = server(200, "{\"msg_id\":123}", requestBody);
    try {
      var env = new MockEnvironment().withProperty("JPUSH_TIME_TO_LIVE_SECONDS", "9999999");

      assertTrue(client(server, env).push("标题", "正文", "message-123").ok());
      JsonNode payload = mapper.readTree(requestBody.get());
      assertEquals(864_000, payload.path("options").path("time_to_live").asInt());
    } finally {
      server.stop(0);
    }
  }

  private JpushPushClient client(HttpServer server, MockEnvironment extra) {
    extra.withProperty("JPUSH_APP_KEY", "app-key")
        .withProperty("JPUSH_MASTER_SECRET", "secret")
        .withProperty("JPUSH_ALIAS", "market_tracker_user")
        .withProperty("JPUSH_PUSH_URL", "http://127.0.0.1:" + server.getAddress().getPort() + "/push");
    return new JpushPushClient(extra, mapper);
  }

  private static HttpServer server(
      int status,
      String responseBody,
      AtomicReference<String> requestBody) throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/push", exchange -> {
      requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, response.length);
      try (OutputStream output = exchange.getResponseBody()) {
        output.write(response);
      }
    });
    server.start();
    return server;
  }
}
