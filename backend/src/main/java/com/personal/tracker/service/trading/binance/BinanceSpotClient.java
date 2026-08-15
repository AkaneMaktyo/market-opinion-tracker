package com.personal.tracker.service.trading.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.config.BinanceSpotProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class BinanceSpotClient {
  private final BinanceSpotProperties properties;
  private final ObjectMapper mapper;
  private final BinanceRequestSigner signer;
  private final RestClient client;
  private volatile long timeOffsetMillis;

  public BinanceSpotClient(
      BinanceSpotProperties properties,
      ObjectMapper mapper,
      BinanceRequestSigner signer) {
    this.properties = properties;
    this.mapper = mapper;
    this.signer = signer;
    this.client = RestClient.builder()
        .baseUrl(properties.baseUrl())
        .requestFactory(requestFactory())
        .build();
  }

  public SymbolRules symbolRules(String symbol) {
    JsonNode root = publicGet("/api/v3/exchangeInfo?symbol=" + encode(cleanSymbol(symbol)));
    JsonNode item = root.path("symbols").path(0);
    if (item.isMissingNode() || item.isNull()) {
      throw new IllegalArgumentException("币安现货不存在交易对 " + cleanSymbol(symbol));
    }
    BigDecimal tickSize = BigDecimal.ZERO;
    BigDecimal stepSize = BigDecimal.ZERO;
    BigDecimal minQuantity = BigDecimal.ZERO;
    BigDecimal maxQuantity = BigDecimal.ZERO;
    BigDecimal minNotional = BigDecimal.ZERO;
    for (JsonNode filter : item.path("filters")) {
      switch (filter.path("filterType").asText()) {
        case "PRICE_FILTER" -> tickSize = decimal(filter.path("tickSize"));
        case "LOT_SIZE" -> {
          stepSize = decimal(filter.path("stepSize"));
          minQuantity = decimal(filter.path("minQty"));
          maxQuantity = decimal(filter.path("maxQty"));
        }
        case "MIN_NOTIONAL", "NOTIONAL" -> minNotional = decimal(filter.path("minNotional"));
        default -> {
          // Other exchange filters do not affect the basic GTC limit order plan.
        }
      }
    }
    return new SymbolRules(
        item.path("symbol").asText(),
        item.path("status").asText(),
        item.path("baseAsset").asText(),
        item.path("quoteAsset").asText(),
        tickSize,
        stepSize,
        minQuantity,
        maxQuantity,
        minNotional);
  }

  public OrderSnapshot placeLimitOrder(
      String symbol,
      String side,
      BigDecimal quantity,
      BigDecimal price,
      String clientOrderId) {
    properties.ensureTradingReady();
    Map<String, String> params = new LinkedHashMap<>();
    params.put("symbol", cleanSymbol(symbol));
    params.put("side", side);
    params.put("type", "LIMIT");
    params.put("timeInForce", "GTC");
    params.put("quantity", quantity.toPlainString());
    params.put("price", price.toPlainString());
    params.put("newClientOrderId", clientOrderId);
    params.put("newOrderRespType", "FULL");
    return orderSnapshot(signed(HttpMethod.POST, "/api/v3/order", params));
  }

  public OrderSnapshot order(String symbol, String clientOrderId) {
    properties.ensureTradingReady();
    Map<String, String> params = new LinkedHashMap<>();
    params.put("symbol", cleanSymbol(symbol));
    params.put("origClientOrderId", clientOrderId);
    return orderSnapshot(signed(HttpMethod.GET, "/api/v3/order", params));
  }

  public List<AccountBalance> balances() {
    properties.ensureTradingReady();
    JsonNode root = signed(HttpMethod.GET, "/api/v3/account", new LinkedHashMap<>());
    List<AccountBalance> balances = new ArrayList<>();
    for (JsonNode row : root.path("balances")) {
      BigDecimal free = decimal(row.path("free"));
      BigDecimal locked = decimal(row.path("locked"));
      if (free.signum() > 0 || locked.signum() > 0) {
        balances.add(new AccountBalance(row.path("asset").asText(), free, locked));
      }
    }
    return List.copyOf(balances);
  }

  public List<FundingBalance> fundingBalances() {
    properties.ensureTradingReady();
    JsonNode root = signed(HttpMethod.POST, "/sapi/v1/asset/get-funding-asset",
        new LinkedHashMap<>());
    List<FundingBalance> balances = new ArrayList<>();
    for (JsonNode row : root) {
      FundingBalance balance = new FundingBalance(
          row.path("asset").asText(),
          decimal(row.path("free")),
          decimal(row.path("locked")),
          decimal(row.path("freeze")),
          decimal(row.path("withdrawing")));
      if (!balance.asset().isBlank() && balance.total().signum() > 0) {
        balances.add(balance);
      }
    }
    return List.copyOf(balances);
  }

  public EquityQuote equityQuote(String symbol) {
    String clean = cleanSymbol(symbol);
    JsonNode root = request(
        HttpMethod.GET,
        "/sapi/v1/equity/market/quote?symbol=" + encode(clean),
        true);
    return new EquityQuote(
        root.path("symbol").asText(clean),
        decimal(root.path("bidPrice")),
        decimal(root.path("askPrice")));
  }

  public SymbolRules equityRules(String symbol) {
    String clean = cleanSymbol(symbol);
    JsonNode root = request(
        HttpMethod.GET,
        "/sapi/v1/equity/market/exchangeInfo?symbol=" + encode(clean),
        true);
    JsonNode payload = payload(root);
    JsonNode item = payload.path("symbols").path(0);
    if ((item.isMissingNode() || item.isNull()) && payload.isArray()) item = payload.path(0);
    if ((item.isMissingNode() || item.isNull()) && payload.has("symbol")) item = payload;
    if (item.isMissingNode() || item.isNull() || item.path("symbol").asText().isBlank()) {
      throw new IllegalArgumentException("币安股票不存在交易标的 " + clean);
    }
    String tradability = item.path("tradability").asText("NONE");
    String status = "BUY_SELL".equals(tradability) || "BUY".equals(tradability)
        ? "TRADING" : tradability;
    return new SymbolRules(
        item.path("symbol").asText(clean),
        status,
        item.path("symbol").asText(clean),
        item.path("quoteAsset").asText("USDC"),
        new BigDecimal("0.01"),
        decimal(item.path("stepSize")),
        decimal(item.path("minQty")),
        decimal(item.path("maxQty")),
        decimal(item.path("minNotional")));
  }

  public OrderSnapshot placeEquityLimitOrder(
      String symbol,
      String side,
      BigDecimal quantity,
      BigDecimal price,
      String clientOrderId) {
    properties.ensureTradingReady();
    Map<String, String> params = new LinkedHashMap<>();
    params.put("symbol", cleanSymbol(symbol));
    params.put("side", side);
    params.put("orderType", "LIMIT");
    params.put("quoteAsset", "USDC");
    params.put("price", price.setScale(2, RoundingMode.DOWN).toPlainString());
    params.put("quantity", quantity.toPlainString());
    params.put("timeInForce", "DAY");
    params.put("tradingSession", "RTH");
    params.put("walletType", "CARD");
    params.put("clientOrderId", clientOrderId);
    params.put("tokenize", "true");
    JsonNode root = signed(HttpMethod.POST, "/sapi/v1/equity/order/place", params);
    return equityOrderAck(root, quantity, price, clientOrderId);
  }

  public OrderSnapshot equityOrder(String clientOrderId) {
    properties.ensureTradingReady();
    Map<String, String> params = new LinkedHashMap<>();
    params.put("clientOrderId", clientOrderId);
    return equityOrderSnapshot(signed(
        HttpMethod.GET, "/sapi/v1/equity/order/detail", params));
  }

  public Map<String, BigDecimal> prices() {
    JsonNode root = publicGet("/api/v3/ticker/price");
    Map<String, BigDecimal> prices = new LinkedHashMap<>();
    if (root.isArray()) {
      for (JsonNode row : root) {
        String symbol = row.path("symbol").asText();
        BigDecimal price = decimal(row.path("price"));
        if (!symbol.isBlank() && price.signum() > 0) {
          prices.put(symbol, price);
        }
      }
    }
    return Map.copyOf(prices);
  }

  private JsonNode signed(HttpMethod method, String path, Map<String, String> parameters) {
    BinanceClientException first = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        Map<String, String> signed = new LinkedHashMap<>(parameters);
        signed.put("recvWindow", String.valueOf(properties.recvWindow()));
        signed.put("timestamp", String.valueOf(System.currentTimeMillis() + timeOffsetMillis));
        String query = query(signed);
        String signature = signer.sign(query);
        String uri = path + "?" + query + "&signature=" + encode(signature);
        return request(method, uri, true);
      } catch (BinanceClientException error) {
        if (attempt == 0 && error.code() == -1021) {
          first = error;
          syncTime();
          continue;
        }
        throw error;
      }
    }
    throw first == null ? new BinanceClientException(0, "币安签名请求失败") : first;
  }

  private void syncTime() {
    JsonNode root = publicGet("/api/v3/time");
    long serverTime = root.path("serverTime").asLong();
    if (serverTime > 0) {
      timeOffsetMillis = serverTime - System.currentTimeMillis();
    }
  }

  private JsonNode publicGet(String uri) {
    return request(HttpMethod.GET, uri, false);
  }

  private JsonNode request(HttpMethod method, String uri, boolean authenticated) {
    try {
      RestClient.RequestBodySpec request = client.method(method)
          .uri(URI.create(properties.baseUrl() + uri));
      if (authenticated) {
        request.header("X-MBX-APIKEY", properties.apiKey());
      }
      JsonNode body = request.retrieve().body(JsonNode.class);
      if (body == null) {
        throw new BinanceClientException(0, "币安返回空响应");
      }
      return body;
    } catch (RestClientResponseException error) {
      JsonNode body = readJson(error.getResponseBodyAsByteArray());
      int code = body == null ? error.getStatusCode().value() : body.path("code").asInt();
      String message = body == null ? error.getMessage() : body.path("msg").asText(error.getMessage());
      throw new BinanceClientException(code, "币安请求失败：" + message);
    } catch (BinanceClientException error) {
      throw error;
    } catch (RuntimeException error) {
      throw new BinanceClientException(0, "连接币安失败：" + message(error));
    }
  }

  private OrderSnapshot orderSnapshot(JsonNode root) {
    BigDecimal executed = decimal(root.path("executedQty"));
    BigDecimal quote = decimal(root.path("cummulativeQuoteQty"));
    BigDecimal average = executed.signum() > 0
        ? quote.divide(executed, 16, RoundingMode.HALF_UP)
        : BigDecimal.ZERO;
    return new OrderSnapshot(
        root.path("orderId").asText(),
        root.path("clientOrderId").asText(),
        root.path("status").asText(),
        decimal(root.path("origQty")),
        executed,
        quote,
        decimal(root.path("price")),
        average);
  }

  private OrderSnapshot equityOrderAck(
      JsonNode root,
      BigDecimal quantity,
      BigDecimal price,
      String requestedClientOrderId) {
    JsonNode item = payload(root);
    String result = item.path("status").asText();
    return new OrderSnapshot(
        item.path("orderId").asText(),
        item.path("clientOrderId").asText(requestedClientOrderId),
        equityStatus(result),
        quantity,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        price,
        BigDecimal.ZERO);
  }

  private OrderSnapshot equityOrderSnapshot(JsonNode root) {
    JsonNode item = payload(root);
    BigDecimal executed = decimal(item.path("filledQty"));
    BigDecimal quote = decimal(item.path("filledTotal"));
    BigDecimal average = decimal(item.path("avgFilledPrice"));
    if (average.signum() <= 0 && executed.signum() > 0) {
      average = quote.divide(executed, 16, RoundingMode.HALF_UP);
    }
    return new OrderSnapshot(
        item.path("orderId").asText(),
        item.path("clientOrderId").asText(),
        equityStatus(item.path("status").asText()),
        decimal(item.path("qty")),
        executed,
        quote,
        decimal(item.path("limitPrice")),
        average);
  }

  private static JsonNode payload(JsonNode root) {
    JsonNode data = root == null ? null : root.path("data");
    return data != null && !data.isMissingNode() && !data.isNull() ? data : root;
  }

  private static String equityStatus(String status) {
    return switch (status == null ? "" : status.toUpperCase()) {
      case "S", "PENDING", "PENDING_NEW", "ACCEPTED" -> "NEW";
      case "F", "FAILED" -> "REJECTED";
      case "CANCELLED" -> "CANCELED";
      default -> status == null || status.isBlank() ? "UNKNOWN" : status.toUpperCase();
    };
  }

  private JsonNode readJson(byte[] bytes) {
    try {
      return bytes == null || bytes.length == 0 ? null : mapper.readTree(bytes);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String query(Map<String, String> params) {
    return params.entrySet().stream()
        .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
        .reduce((left, right) -> left + "&" + right)
        .orElse("");
  }

  private SimpleClientHttpRequestFactory requestFactory() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(8));
    factory.setReadTimeout(Duration.ofSeconds(15));
    if (!properties.proxyUrl().isBlank()) {
      URI proxyUri = URI.create(properties.proxyUrl());
      if (proxyUri.getHost() == null) {
        throw new IllegalArgumentException("币安代理地址格式不正确");
      }
      int port = proxyUri.getPort() > 0 ? proxyUri.getPort() : 80;
      factory.setProxy(new Proxy(
          Proxy.Type.HTTP, new InetSocketAddress(proxyUri.getHost(), port)));
    }
    return factory;
  }

  private static String cleanSymbol(String symbol) {
    String clean = symbol == null ? "" : symbol.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    if (clean.isBlank()) {
      throw new IllegalArgumentException("币安交易对不能为空");
    }
    return clean;
  }

  private static BigDecimal decimal(JsonNode node) {
    try {
      String value = node == null ? "" : node.asText("");
      return value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
    } catch (NumberFormatException error) {
      return BigDecimal.ZERO;
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static String message(Throwable error) {
    return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
  }

  public record SymbolRules(
      String symbol,
      String status,
      String baseAsset,
      String quoteAsset,
      BigDecimal tickSize,
      BigDecimal stepSize,
      BigDecimal minQuantity,
      BigDecimal maxQuantity,
      BigDecimal minNotional) {
  }

  public record OrderSnapshot(
      String orderId,
      String clientOrderId,
      String status,
      BigDecimal originalQuantity,
      BigDecimal executedQuantity,
      BigDecimal cumulativeQuote,
      BigDecimal price,
      BigDecimal averagePrice) {
  }

  public record AccountBalance(String asset, BigDecimal free, BigDecimal locked) {
    public BigDecimal total() {
      return free.add(locked);
    }
  }

  public record FundingBalance(
      String asset,
      BigDecimal free,
      BigDecimal locked,
      BigDecimal frozen,
      BigDecimal withdrawing) {
    public BigDecimal total() {
      return free.add(locked).add(frozen).add(withdrawing);
    }
  }

  public record EquityQuote(String symbol, BigDecimal bidPrice, BigDecimal askPrice) {
    public BigDecimal midpoint() {
      if (bidPrice.signum() > 0 && askPrice.signum() > 0) {
        return bidPrice.add(askPrice).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
      }
      return bidPrice.signum() > 0 ? bidPrice : askPrice;
    }
  }

  public static class BinanceClientException extends RuntimeException {
    private final int code;

    public BinanceClientException(int code, String message) {
      super(message);
      this.code = code;
    }

    public int code() {
      return code;
    }
  }
}
