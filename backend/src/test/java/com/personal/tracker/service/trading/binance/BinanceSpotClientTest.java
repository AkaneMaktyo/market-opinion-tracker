package com.personal.tracker.service.trading.binance;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.config.BinanceSpotProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BinanceSpotClientTest {
  @TempDir
  Path tempDir;

  private HttpServer server;

  @AfterEach
  void stopServer() {
    if (server != null) server.stop(0);
  }

  @Test
  void preservesRsaSignatureWhenBuildingRequestUri() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();
    Path privateKey = tempDir.resolve("private.pem");
    Files.writeString(privateKey, pem(keyPair), StandardCharsets.UTF_8);

    AtomicBoolean signatureValid = new AtomicBoolean();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/v3/account", exchange -> {
      signatureValid.set(verify(exchange.getRequestURI().getRawQuery(), keyPair));
      byte[] response = "{\"balances\":[]}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    });
    server.start();

    BinanceSpotProperties properties = new BinanceSpotProperties();
    properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
    properties.setApiKey("test-api-key");
    properties.setKeyType("RSA");
    properties.setPrivateKeyPath(privateKey.toString());
    BinanceSpotClient client = new BinanceSpotClient(
        properties, new ObjectMapper(), new BinanceRequestSigner(properties));

    client.balances();

    assertTrue(signatureValid.get());
  }

  @Test
  void readsFundingBalancesAndEquityQuotes() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();
    Path privateKey = tempDir.resolve("funding-private.pem");
    Files.writeString(privateKey, pem(keyPair), StandardCharsets.UTF_8);

    AtomicBoolean fundingSignatureValid = new AtomicBoolean();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/sapi/v1/asset/get-funding-asset", exchange -> {
      fundingSignatureValid.set(
          "POST".equals(exchange.getRequestMethod())
              && verify(exchange.getRequestURI().getRawQuery(), keyPair));
      respond(exchange, "[{\"asset\":\"EQ_GOOGL\",\"free\":\"0.3\","
          + "\"locked\":\"0.2\",\"freeze\":\"0.1\",\"withdrawing\":\"0\"}]");
    });
    server.createContext("/sapi/v1/equity/market/quote", exchange ->
        respond(exchange, "{\"symbol\":\"GOOGL\",\"bidPrice\":\"200\","
            + "\"askPrice\":\"202\"}"));
    server.start();

    BinanceSpotClient client = client(privateKey);

    var funding = client.fundingBalances();
    var quote = client.equityQuote("GOOGL");

    assertTrue(fundingSignatureValid.get());
    assertEquals(new BigDecimal("0.6"), funding.get(0).total());
    assertEquals(new BigDecimal("201.00000000"), quote.midpoint());
  }

  @Test
  void readsEquityRulesAndUsesStockOrderEndpoints() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();
    Path privateKey = tempDir.resolve("stock-private.pem");
    Files.writeString(privateKey, pem(keyPair), StandardCharsets.UTF_8);

    AtomicReference<String> placeQuery = new AtomicReference<>();
    AtomicBoolean placeSignatureValid = new AtomicBoolean();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/sapi/v1/equity/market/exchangeInfo", exchange -> respond(exchange,
        "{\"symbols\":[{\"symbol\":\"GOOGL\",\"tradability\":\"BUY_SELL\","
            + "\"stepSize\":\"0.0001\",\"minQty\":\"0.0001\","
            + "\"maxQty\":\"1000\",\"minNotional\":\"1\"}]}"));
    server.createContext("/sapi/v1/equity/order/place", exchange -> {
      placeQuery.set(exchange.getRequestURI().getRawQuery());
      placeSignatureValid.set(
          "POST".equals(exchange.getRequestMethod()) && verify(placeQuery.get(), keyPair));
      respond(exchange, "{\"status\":\"S\",\"orderId\":\"stock-order-1\","
          + "\"clientOrderId\":\"mot123456789012345678901234567801\"}");
    });
    server.createContext("/sapi/v1/equity/order/detail", exchange -> respond(exchange,
        "{\"orderId\":\"stock-order-1\",\"clientOrderId\":"
            + "\"mot123456789012345678901234567801\",\"status\":\"FILLED\","
            + "\"qty\":\"0.5\",\"filledQty\":\"0.5\",\"filledTotal\":\"100\","
            + "\"limitPrice\":\"200\",\"avgFilledPrice\":\"200\"}"));
    server.start();

    BinanceSpotClient client = client(privateKey);
    var rules = client.equityRules("GOOGL");
    var placed = client.placeEquityLimitOrder(
        "GOOGL", "BUY", new BigDecimal("0.5"), new BigDecimal("200"),
        "mot123456789012345678901234567801");
    var filled = client.equityOrder("mot123456789012345678901234567801");

    assertEquals("USDC", rules.quoteAsset());
    assertEquals("NEW", placed.status());
    assertEquals("FILLED", filled.status());
    assertEquals(new BigDecimal("100"), filled.cumulativeQuote());
    assertTrue(placeSignatureValid.get());
    assertTrue(placeQuery.get().contains("orderType=LIMIT"));
    assertTrue(placeQuery.get().contains("tradingSession=RTH"));
    assertTrue(placeQuery.get().contains("walletType=CARD"));
    assertTrue(placeQuery.get().contains("tokenize=true"));
  }

  private BinanceSpotClient client(Path privateKey) {
    BinanceSpotProperties properties = new BinanceSpotProperties();
    properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
    properties.setApiKey("test-api-key");
    properties.setKeyType("RSA");
    properties.setPrivateKeyPath(privateKey.toString());
    return new BinanceSpotClient(
        properties, new ObjectMapper(), new BinanceRequestSigner(properties));
  }

  private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body)
      throws java.io.IOException {
    byte[] response = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, response.length);
    exchange.getResponseBody().write(response);
    exchange.close();
  }

  private static boolean verify(String rawQuery, KeyPair keyPair) {
    try {
      int signatureIndex = rawQuery.lastIndexOf("&signature=");
      String payload = rawQuery.substring(0, signatureIndex);
      String encoded = rawQuery.substring(signatureIndex + "&signature=".length());
      byte[] signatureBytes = Base64.getDecoder().decode(
          URLDecoder.decode(encoded, StandardCharsets.UTF_8));
      Signature verifier = Signature.getInstance("SHA256withRSA");
      verifier.initVerify(keyPair.getPublic());
      verifier.update(payload.getBytes(StandardCharsets.UTF_8));
      return verifier.verify(signatureBytes);
    } catch (Exception ignored) {
      return false;
    }
  }

  private static String pem(KeyPair pair) {
    String encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
        .encodeToString(pair.getPrivate().getEncoded());
    return "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n";
  }
}
