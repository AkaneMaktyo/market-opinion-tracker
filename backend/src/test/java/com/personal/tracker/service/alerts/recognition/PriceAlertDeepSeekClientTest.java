package com.personal.tracker.service.alerts.recognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.config.PriceAlertDeepSeekProperties;
import com.personal.tracker.repository.llm.LlmCallLogRepository;
import com.personal.tracker.repository.llm.LlmCallLogRepository.AuditCompletion;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PriceAlertDeepSeekClientTest {
  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) server.stop(0);
  }

  @Test
  void writesPendingAuditBeforeHttpAndCompletesFullAudit() throws Exception {
    AtomicBoolean auditStarted = new AtomicBoolean();
    AtomicBoolean requestArrivedAfterAudit = new AtomicBoolean();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/chat/completions", exchange -> {
      requestArrivedAfterAudit.set(auditStarted.get());
      String body = """
          {"id":"deepseek-request-1","choices":[{"message":{"content":"{\\"candidates\\":[]}"}}],"usage":{"prompt_tokens":31,"completion_tokens":7,"total_tokens":38}}
          """;
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    server.start();

    LlmCallLogRepository logs = mock(LlmCallLogRepository.class);
    when(logs.beginAudit(anyString(), anyString(), anyString(), anyString())).thenAnswer(call -> {
      auditStarted.set(true);
      return "audit-1";
    });
    PriceAlertDeepSeekProperties properties = properties();
    properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
    PriceAlertDeepSeekClient client = new PriceAlertDeepSeekClient(
        properties, logs, new ObjectMapper());

    assertEquals("{\"candidates\":[]}", client.recognize("msg-1", "system", "user"));
    assertTrue(requestArrivedAfterAudit.get());
    ArgumentCaptor<String> request = ArgumentCaptor.forClass(String.class);
    verify(logs).beginAudit(
        org.mockito.ArgumentMatchers.eq("msg-1"),
        org.mockito.ArgumentMatchers.eq(PriceAlertDeepSeekClient.SCENE),
        org.mockito.ArgumentMatchers.eq(PriceAlertDeepSeekProperties.MODEL), request.capture());
    assertTrue(request.getValue().contains("\"thinking\":{\"type\":\"disabled\"}"));
    assertTrue(request.getValue().contains("\"response_format\":{\"type\":\"json_object\"}"));
    assertTrue(request.getValue().contains("\"max_tokens\":16384"));
    assertFalse(request.getValue().contains("test-secret"));

    ArgumentCaptor<AuditCompletion> completion = ArgumentCaptor.forClass(AuditCompletion.class);
    verify(logs).completeAudit(org.mockito.ArgumentMatchers.eq("audit-1"), completion.capture());
    assertEquals("SUCCESS", completion.getValue().status());
    assertEquals("deepseek-request-1", completion.getValue().providerRequestId());
    assertEquals(31, completion.getValue().promptTokens());
    assertEquals(7, completion.getValue().completionTokens());
    assertEquals(38, completion.getValue().totalTokens());
  }

  @Test
  void neverCallsProviderWhenPendingAuditCannotBeCreated() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/chat/completions", exchange -> {
      calls.incrementAndGet();
      exchange.sendResponseHeaders(500, -1);
      exchange.close();
    });
    server.start();
    LlmCallLogRepository logs = mock(LlmCallLogRepository.class);
    when(logs.beginAudit(anyString(), anyString(), anyString(), anyString()))
        .thenThrow(new IllegalStateException("审计预写失败"));
    PriceAlertDeepSeekProperties properties = properties();
    properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
    PriceAlertDeepSeekClient client = new PriceAlertDeepSeekClient(
        properties, logs, new ObjectMapper());

    IllegalStateException error = assertThrows(
        IllegalStateException.class, () -> client.recognize("msg-2", "system", "user"));

    assertEquals("审计预写失败", error.getMessage());
    assertEquals(0, calls.get());
    verify(logs, never()).completeAudit(anyString(), any(AuditCompletion.class));
  }

  private PriceAlertDeepSeekProperties properties() {
    PriceAlertDeepSeekProperties properties = new PriceAlertDeepSeekProperties();
    properties.setApiKey("test-secret");
    return properties;
  }
}
