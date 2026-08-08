package com.personal.tracker.service.market.bitget;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BitgetPublicApiClientTest {
  @Test
  void curlFallbackReadsJsonOnWindowsAndLinux() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/ticker", exchange -> {
      byte[] response = """
          {"code":"00000","data":[{"lastPr":"117.03"}]}
          """.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, response.length);
      try (OutputStream output = exchange.getResponseBody()) {
        output.write(response);
      }
    });
    server.start();
    try {
      var client = new BitgetPublicApiClient(new ObjectMapper(), "");

      var result = client.getWithCurl(
          "http://127.0.0.1:" + server.getAddress().getPort() + "/ticker");

      assertEquals("00000", result.path("code").asText());
      assertEquals("117.03", result.path("data").path(0).path("lastPr").asText());
    } finally {
      server.stop(0);
    }
  }
}
