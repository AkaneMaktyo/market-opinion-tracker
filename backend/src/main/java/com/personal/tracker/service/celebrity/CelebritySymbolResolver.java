package com.personal.tracker.service.celebrity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.domain.celebrity.CelebrityHolding;
import com.personal.tracker.repository.celebrity.CelebrityPortfolioRepository;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class CelebritySymbolResolver {
  private final CelebrityPortfolioRepository repository;
  private final ObjectMapper mapper;
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

  public CelebritySymbolResolver(CelebrityPortfolioRepository repository, ObjectMapper mapper) {
    this.repository = repository;
    this.mapper = mapper;
  }

  public int resolvePending(int limit) {
    int resolved = 0;
    for (CelebrityHolding holding : repository.latestUnmappedHoldings(limit)) {
      Optional<String> symbol = findSymbol(holding.issuerName());
      if (symbol.isPresent()) {
        repository.saveSymbolMapping(holding.cusip(), symbol.get(), "YAHOO_SEARCH", "MEDIUM");
        resolved++;
      }
      pause();
    }
    return resolved;
  }

  private Optional<String> findSymbol(String issuerName) {
    if (issuerName == null || issuerName.isBlank()) {
      return Optional.empty();
    }
    try {
      String query = URLEncoder.encode(issuerName, StandardCharsets.UTF_8);
      HttpRequest request = HttpRequest.newBuilder(URI.create(
          "https://query1.finance.yahoo.com/v1/finance/search?q=" + query + "&quotesCount=8&newsCount=0"))
          .timeout(Duration.ofSeconds(12)).header("User-Agent", "Mozilla/5.0").GET().build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        return Optional.empty();
      }
      JsonNode quotes = mapper.readTree(response.body()).path("quotes");
      return java.util.stream.StreamSupport.stream(quotes.spliterator(), false)
          .filter(item -> supportedType(item.path("quoteType").asText("")))
          .filter(item -> validSymbol(item.path("symbol").asText("")))
          .max(Comparator.comparingInt(item -> score(issuerName, item)))
          .filter(item -> score(issuerName, item) > 0)
          .map(item -> item.path("symbol").asText("").trim().toUpperCase(Locale.ROOT));
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    } catch (IOException | RuntimeException error) {
      return Optional.empty();
    }
  }

  private static int score(String issuer, JsonNode quote) {
    String candidate = quote.path("longname").asText("") + " " + quote.path("shortname").asText("");
    String normalizedCandidate = normalize(candidate);
    return (int) Arrays.stream(normalize(issuer).split(" "))
        .filter(token -> token.length() > 2 && normalizedCandidate.contains(token))
        .count();
  }

  private static boolean supportedType(String value) {
    return "EQUITY".equalsIgnoreCase(value) || "ETF".equalsIgnoreCase(value);
  }

  private static boolean validSymbol(String value) {
    return value != null && value.matches("[A-Za-z][A-Za-z.\\-]{0,14}");
  }

  private static String normalize(String value) {
    return value == null ? "" : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim();
  }

  private static void pause() {
    try {
      Thread.sleep(250);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
    }
  }
}
