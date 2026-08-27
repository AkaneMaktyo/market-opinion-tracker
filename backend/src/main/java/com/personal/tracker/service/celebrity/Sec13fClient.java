package com.personal.tracker.service.celebrity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.config.celebrity.CelebrityDataProperties;
import com.personal.tracker.config.celebrity.CelebrityHttpClientFactory;
import com.personal.tracker.domain.celebrity.CelebrityInvestor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Component
public class Sec13fClient {
  private static final String DATA_BASE = "https://data.sec.gov";
  private static final String ARCHIVES_BASE = "https://www.sec.gov/Archives/edgar/data";
  private static final BigDecimal THOUSANDS_VALUE_MULTIPLIER = BigDecimal.valueOf(1000);
  private static final BigDecimal DIRECT_DOLLAR_MEDIAN_FLOOR = new BigDecimal("20");
  private static final BigDecimal THOUSANDS_MEDIAN_CEILING = new BigDecimal("10");
  private static final int MAX_REQUEST_ATTEMPTS = 3;
  private final CelebrityDataProperties properties;
  private final ObjectMapper mapper;
  private final HttpClient client;

  public Sec13fClient(
      CelebrityDataProperties properties,
      ObjectMapper mapper,
      CelebrityHttpClientFactory httpClients) {
    this.properties = properties;
    this.mapper = mapper;
    this.client = httpClients.create();
  }

  public List<SecFiling> recentFilings(CelebrityInvestor investor) {
    requireConfigured();
    String cik = normalizedCik(investor.cik());
    JsonNode recent = json(DATA_BASE + "/submissions/CIK" + cik + ".json")
        .path("filings").path("recent");
    JsonNode forms = recent.path("form");
    Map<String, SecFiling> byReportDate = new LinkedHashMap<>();
    for (int index = 0; index < forms.size() && byReportDate.size() < properties.historyLimit(); index++) {
      String form = text(forms, index);
      if (!"13F-HR".equals(form) && !"13F-HR/A".equals(form)) {
        continue;
      }
      String reportDate = text(recent.path("reportDate"), index);
      String accession = text(recent.path("accessionNumber"), index);
      String filingDate = text(recent.path("filingDate"), index);
      if (reportDate.isBlank() || accession.isBlank()) {
        continue;
      }
      byReportDate.putIfAbsent(reportDate, new SecFiling(
          accession, form, reportDate, filingDate, "13F-HR/A".equals(form), cik));
    }
    return List.copyOf(byReportDate.values());
  }

  public List<SecHolding> holdings(SecFiling filing) {
    String archive = archiveBase(filing.cik(), filing.accessionNumber());
    JsonNode items = json(archive + "/index.json").path("directory").path("item");
    String tableFile = informationTableFile(items);
    if (tableFile.isBlank()) {
      throw new IllegalStateException("SEC 13F 未找到 information table：" + filing.accessionNumber());
    }
    return parseInformationTable(bytes(archive + "/" + tableFile));
  }

  public String filingUrl(SecFiling filing) {
    return archiveBase(filing.cik(), filing.accessionNumber()) + "/"
        + filing.accessionNumber() + "-index.htm";
  }

  private JsonNode json(String url) {
    try {
      return mapper.readTree(bytes(url));
    } catch (IOException error) {
      throw new IllegalStateException("读取 SEC 数据失败：" + error.getMessage(), error);
    }
  }

  private byte[] bytes(String url) {
    IOException lastIoError = null;
    for (int attempt = 1; attempt <= MAX_REQUEST_ATTEMPTS; attempt++) {
      try {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", properties.secUserAgent())
            .header("Accept", "application/json, application/xml, text/xml, text/plain")
            .GET().build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
          throw new IllegalStateException("SEC 返回 HTTP " + response.statusCode());
        }
        return response.body();
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("读取 SEC 数据被中断", error);
      } catch (IOException error) {
        lastIoError = error;
        if (attempt < MAX_REQUEST_ATTEMPTS) {
          pauseBeforeRetry(attempt);
        }
      }
    }
    throw new IllegalStateException("读取 SEC 数据失败：" + lastIoError.getMessage(), lastIoError);
  }

  private static void pauseBeforeRetry(int attempt) {
    try {
      Thread.sleep(200L * attempt);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("读取 SEC 数据被中断", error);
    }
  }

  static List<SecHolding> parseInformationTable(byte[] raw) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(raw));
      NodeList rows = document.getElementsByTagNameNS("*", "infoTable");
      if (rows.getLength() == 0) {
        rows = document.getElementsByTagName("infoTable");
      }
      List<RawSecHolding> rowsToNormalize = new java.util.ArrayList<>();
      for (int index = 0; index < rows.getLength(); index++) {
        Element row = (Element) rows.item(index);
        BigDecimal shares = decimal(child(row, "sshPrnamt"));
        BigDecimal rawValue = decimal(child(row, "value"));
        if (shares.signum() <= 0 || rawValue.signum() < 0) {
          continue;
        }
        String issuer = child(row, "nameOfIssuer");
        String cusip = clean(child(row, "cusip"));
        String titleClass = clean(child(row, "titleOfClass"));
        String putCall = clean(child(row, "putCall"));
        String holdingKey = holdingKey(cusip, issuer, titleClass, putCall);
        rowsToNormalize.add(new RawSecHolding(holdingKey, cusip, issuer, titleClass, putCall, shares, rawValue));
      }
      BigDecimal valueMultiplier = valueMultiplier(rowsToNormalize);
      Map<String, SecHolding> holdings = new LinkedHashMap<>();
      for (RawSecHolding row : rowsToNormalize) {
        BigDecimal reportedValue = row.rawValue().multiply(valueMultiplier);
        SecHolding rowHolding = new SecHolding(row.holdingKey(), "", row.cusip(), row.issuerName(),
            row.titleClass(), row.putCall(), row.shares(), reportedValue,
            reportedValue.divide(row.shares(), 8, RoundingMode.HALF_UP));
        holdings.merge(row.holdingKey(), rowHolding, Sec13fClient::mergeHolding);
      }
      return List.copyOf(holdings.values());
    } catch (Exception error) {
      throw new IllegalStateException("解析 SEC 13F information table 失败：" + error.getMessage(), error);
    }
  }

  private static BigDecimal valueMultiplier(List<RawSecHolding> holdings) {
    List<BigDecimal> rawUnitValues = holdings.stream()
        .filter(item -> item.rawValue().signum() > 0)
        .map(item -> item.rawValue().divide(item.shares(), 8, RoundingMode.HALF_UP))
        .sorted()
        .toList();
    if (rawUnitValues.isEmpty()) {
      throw new IllegalStateException("SEC 13F 没有可校验的持仓金额");
    }
    BigDecimal median = rawUnitValues.get(rawUnitValues.size() / 2);
    if (median.compareTo(THOUSANDS_MEDIAN_CEILING) <= 0) {
      return THOUSANDS_VALUE_MULTIPLIER;
    }
    if (median.compareTo(DIRECT_DOLLAR_MEDIAN_FLOOR) >= 0) {
      return BigDecimal.ONE;
    }
    throw new IllegalStateException("SEC 13F 金额单位无法安全判定，已拒绝覆盖已有快照");
  }

  private static SecHolding mergeHolding(SecHolding first, SecHolding duplicate) {
    BigDecimal shares = first.shares().add(duplicate.shares());
    BigDecimal reportedValue = first.reportedValue().add(duplicate.reportedValue());
    return new SecHolding(
        first.holdingKey(), first.symbol(), first.cusip(), first.issuerName(), first.titleClass(),
        first.putCall(), shares, reportedValue,
        reportedValue.divide(shares, 8, RoundingMode.HALF_UP));
  }

  private static String informationTableFile(JsonNode items) {
    String genericXml = "";
    for (JsonNode item : items) {
      String name = item.path("name").asText("");
      String lower = name.toLowerCase(Locale.ROOT);
      if (!lower.endsWith(".xml") || lower.contains("primary") || lower.contains("xsl")) {
        continue;
      }
      if (lower.contains("infotable") || lower.contains("informationtable")) {
        return name;
      }
      if (genericXml.isBlank()) {
        genericXml = name;
      }
    }
    return genericXml;
  }

  private static String child(Element parent, String name) {
    NodeList matches = parent.getElementsByTagNameNS("*", name);
    if (matches.getLength() == 0) {
      matches = parent.getElementsByTagName(name);
    }
    Node match = matches.getLength() == 0 ? null : matches.item(0);
    return match == null ? "" : match.getTextContent().trim();
  }

  private static BigDecimal decimal(String value) {
    try {
      return new BigDecimal(value.replace(",", "").trim());
    } catch (RuntimeException error) {
      return BigDecimal.ZERO;
    }
  }

  private static String holdingKey(String cusip, String issuer, String titleClass, String putCall) {
    String base = cusip.isBlank() ? issuer : cusip;
    return clean(base) + "|" + clean(titleClass) + "|" + clean(putCall);
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
  }

  private static String normalizedCik(String raw) {
    try {
      return String.format("%010d", Long.parseLong(raw == null ? "" : raw.trim()));
    } catch (RuntimeException error) {
      throw new IllegalArgumentException("投资人未配置有效的 SEC CIK");
    }
  }

  private static String archiveBase(String cik, String accession) {
    long numericCik = Long.parseLong(cik);
    return ARCHIVES_BASE + "/" + numericCik + "/" + accession.replace("-", "");
  }

  private void requireConfigured() {
    if (!properties.secConfigured()) {
      throw new IllegalStateException("未配置 SEC_USER_AGENT，无法按 SEC 访问要求同步 13F");
    }
  }

  private static String text(JsonNode node, int index) {
    return node.path(index).asText("").trim();
  }

  public record SecFiling(
      String accessionNumber,
      String formType,
      String reportDate,
      String filedAt,
      boolean amendment,
      String cik) {
  }

  public record SecHolding(
      String holdingKey,
      String symbol,
      String cusip,
      String issuerName,
      String titleClass,
      String putCall,
      BigDecimal shares,
      BigDecimal reportedValue,
      BigDecimal reportedUnitValue) {
  }

  private record RawSecHolding(
      String holdingKey,
      String cusip,
      String issuerName,
      String titleClass,
      String putCall,
      BigDecimal shares,
      BigDecimal rawValue) {
  }
}
