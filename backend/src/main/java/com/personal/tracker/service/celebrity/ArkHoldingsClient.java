package com.personal.tracker.service.celebrity;

import com.personal.tracker.config.celebrity.CelebrityDataProperties;
import com.personal.tracker.config.celebrity.CelebrityHttpClientFactory;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ArkHoldingsClient {
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/uuuu");
  private final CelebrityDataProperties properties;
  private final HttpClient client;
  private final HttpClient directClient;

  public ArkHoldingsClient(CelebrityDataProperties properties, CelebrityHttpClientFactory httpClients) {
    this.properties = properties;
    this.client = httpClients.create();
    this.directClient = httpClients.createDirect();
  }

  public ArkSnapshot currentArkk() {
    if (properties.arkHoldingsUrl().isBlank()) {
      throw new IllegalStateException("未配置 ARK 官方持仓 CSV 地址");
    }
    List<List<String>> rows = csv(download());
    if (rows.size() < 2) {
      throw new IllegalStateException("ARK 官方 CSV 没有可用持仓");
    }
    Map<String, Integer> columns = columns(rows.get(0));
    List<ArkHolding> holdings = new ArrayList<>();
    String reportDate = "";
    for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
      List<String> row = rows.get(rowIndex);
      if (!"ARKK".equalsIgnoreCase(value(row, columns, "fund"))) {
        continue;
      }
      String date = parseDate(value(row, columns, "date"));
      String issuer = value(row, columns, "company");
      String ticker = value(row, columns, "ticker").toUpperCase(Locale.ROOT);
      String cusip = value(row, columns, "cusip").toUpperCase(Locale.ROOT);
      BigDecimal shares = decimal(value(row, columns, "shares"));
      BigDecimal value = decimal(value(row, columns, "market value ($)"));
      BigDecimal weight = decimal(value(row, columns, "weight (%)")).movePointLeft(2);
      if (date.isBlank() || issuer.isBlank() || shares.signum() <= 0 || value.signum() < 0) {
        continue;
      }
      reportDate = reportDate.isBlank() ? date : reportDate;
      BigDecimal unitValue = value.divide(shares, 8, RoundingMode.HALF_UP);
      String holdingKey = (cusip.isBlank() ? ticker : cusip) + "|EQUITY|";
      holdings.add(new ArkHolding(holdingKey, ticker, cusip, issuer, "EQUITY", "",
          shares, value, weight, unitValue));
    }
    if (reportDate.isBlank() || holdings.isEmpty()) {
      throw new IllegalStateException("ARK 官方 CSV 未返回 ARKK 持仓");
    }
    return new ArkSnapshot("ARKK:" + reportDate, reportDate, properties.arkHoldingsUrl(), holdings);
  }

  private String download() {
    try {
      return download(client);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("读取 ARK 持仓被中断", error);
    } catch (IOException error) {
      return downloadDirectAfterProxyFailure(error);
    }
  }

  private String downloadDirectAfterProxyFailure(IOException proxyError) {
    if (properties.proxyUrl() == null || properties.proxyUrl().isBlank()) {
      throw new IllegalStateException("读取 ARK 持仓失败：" + proxyError.getMessage(), proxyError);
    }
    try {
      return download(directClient);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("读取 ARK 持仓被中断", error);
    } catch (IOException directError) {
      directError.addSuppressed(proxyError);
      throw new IllegalStateException(
          "读取 ARK 持仓失败：代理 " + proxyError.getMessage() + "；直连 " + directError.getMessage(),
          directError);
    }
  }

  private String download(HttpClient httpClient) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(URI.create(properties.arkHoldingsUrl()))
        .timeout(Duration.ofSeconds(20))
        .header("User-Agent", "market-opinion-tracker/1.0")
        .GET().build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException("ARK 返回 HTTP " + response.statusCode());
    }
    return response.body();
  }

  private static Map<String, Integer> columns(List<String> headers) {
    Map<String, Integer> result = new HashMap<>();
    for (int index = 0; index < headers.size(); index++) {
      result.put(headers.get(index).replace("\ufeff", "").trim().toLowerCase(Locale.ROOT), index);
    }
    return result;
  }

  private static String value(List<String> row, Map<String, Integer> columns, String column) {
    Integer index = columns.get(column.toLowerCase(Locale.ROOT));
    return index == null || index >= row.size() ? "" : row.get(index).trim();
  }

  private static String parseDate(String value) {
    try {
      return LocalDate.parse(value.trim(), DATE_FORMAT).toString();
    } catch (RuntimeException error) {
      return "";
    }
  }

  private static BigDecimal decimal(String value) {
    String normalized = value.replace("$", "").replace("%", "").replace(",", "").trim();
    try {
      return new BigDecimal(normalized);
    } catch (RuntimeException error) {
      return BigDecimal.ZERO;
    }
  }

  private static List<List<String>> csv(String source) {
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder cell = new StringBuilder();
    boolean quoted = false;
    for (int index = 0; index < source.length(); index++) {
      char current = source.charAt(index);
      if (current == '"') {
        if (quoted && index + 1 < source.length() && source.charAt(index + 1) == '"') {
          cell.append('"');
          index++;
        } else {
          quoted = !quoted;
        }
      } else if (current == ',' && !quoted) {
        row.add(cell.toString());
        cell.setLength(0);
      } else if ((current == '\n' || current == '\r') && !quoted) {
        if (current == '\r' && index + 1 < source.length() && source.charAt(index + 1) == '\n') {
          index++;
        }
        row.add(cell.toString());
        cell.setLength(0);
        if (!row.stream().allMatch(String::isBlank)) {
          rows.add(row);
        }
        row = new ArrayList<>();
      } else {
        cell.append(current);
      }
    }
    if (cell.length() > 0 || !row.isEmpty()) {
      row.add(cell.toString());
      rows.add(row);
    }
    return rows;
  }

  public record ArkSnapshot(String externalId, String reportDate, String sourceUrl, List<ArkHolding> holdings) {
  }

  public record ArkHolding(
      String holdingKey,
      String symbol,
      String cusip,
      String issuerName,
      String titleClass,
      String putCall,
      BigDecimal shares,
      BigDecimal reportedValue,
      BigDecimal reportedWeight,
      BigDecimal reportedUnitValue) {
  }
}
