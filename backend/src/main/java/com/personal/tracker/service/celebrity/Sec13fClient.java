package com.personal.tracker.service.celebrity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.config.celebrity.CelebrityDataProperties;
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
import java.util.ArrayList;
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
  private final CelebrityDataProperties properties;
  private final ObjectMapper mapper;
  private final HttpClient client = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(12))
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();

  public Sec13fClient(CelebrityDataProperties properties, ObjectMapper mapper) {
    this.properties = properties;
    this.mapper = mapper;
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
      throw new IllegalStateException("读取 SEC 数据失败：" + error.getMessage(), error);
    }
  }

  private static List<SecHolding> parseInformationTable(byte[] raw) {
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
      List<SecHolding> holdings = new ArrayList<>();
      for (int index = 0; index < rows.getLength(); index++) {
        Element row = (Element) rows.item(index);
        BigDecimal shares = decimal(child(row, "sshPrnamt"));
        BigDecimal valueThousands = decimal(child(row, "value"));
        if (shares.signum() <= 0 || valueThousands.signum() < 0) {
          continue;
        }
        String issuer = child(row, "nameOfIssuer");
        String cusip = clean(child(row, "cusip"));
        String titleClass = clean(child(row, "titleOfClass"));
        String putCall = clean(child(row, "putCall"));
        BigDecimal reportedValue = valueThousands.multiply(BigDecimal.valueOf(1000));
        BigDecimal unitValue = reportedValue.divide(shares, 8, RoundingMode.HALF_UP);
        String holdingKey = holdingKey(cusip, issuer, titleClass, putCall);
        holdings.add(new SecHolding(holdingKey, "", cusip, issuer, titleClass, putCall,
            shares, reportedValue, unitValue));
      }
      return holdings;
    } catch (Exception error) {
      throw new IllegalStateException("解析 SEC 13F information table 失败：" + error.getMessage(), error);
    }
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
}
