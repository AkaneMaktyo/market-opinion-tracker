package com.personal.tracker.service.market.bitget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class BitgetPublicApiClient {
  private static final Logger log = LoggerFactory.getLogger(BitgetPublicApiClient.class);
  private static final String BASE_URL = "https://api.bitget.com";
  private final ObjectMapper mapper;
  private final RestClient client;

  public BitgetPublicApiClient(ObjectMapper mapper) {
    this.mapper = mapper;
    this.client = RestClient.builder()
        .baseUrl(BASE_URL)
        .requestFactory(requestFactory())
        .build();
  }

  public JsonNode get(String uri) {
    try {
      return client.get().uri(uri).retrieve().body(JsonNode.class);
    } catch (RestClientResponseException error) {
      JsonNode body = readJson(error.getResponseBodyAsByteArray());
      if (body != null) {
        return body;
      }
      log.debug("Bitget Java client status error, trying PowerShell fallback: {}", error.getMessage());
      return getWithPowerShell(BASE_URL + uri);
    } catch (RuntimeException error) {
      log.debug("Bitget Java client failed, trying PowerShell fallback: {}", error.getMessage());
      return getWithPowerShell(BASE_URL + uri);
    }
  }

  private SimpleClientHttpRequestFactory requestFactory() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(8));
    factory.setReadTimeout(Duration.ofSeconds(15));
    return factory;
  }

  private JsonNode getWithPowerShell(String url) {
    Path output = null;
    try {
      output = Files.createTempFile("bitget-public-", ".json");
      String command = """
          $ProgressPreference = 'SilentlyContinue';
          [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new();
          try {
            $response = Invoke-WebRequest -Uri '%s' -TimeoutSec 15 -UseBasicParsing;
            $response.Content;
          } catch {
            if ($_.ErrorDetails -ne $null -and -not [string]::IsNullOrWhiteSpace($_.ErrorDetails.Message)) {
              $_.ErrorDetails.Message;
              exit 0;
            }
            if ($_.Exception.Response -ne $null) {
              $stream = $_.Exception.Response.GetResponseStream();
              if ($stream -ne $null) {
                $reader = [System.IO.StreamReader]::new($stream);
                $reader.ReadToEnd();
                exit 0;
              }
            }
            throw;
          }
          """.formatted(url.replace("'", "''"));
      Process process = new ProcessBuilder(
          "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", command)
          .redirectErrorStream(true)
          .redirectOutput(output.toFile())
          .start();
      CompletableFuture<Integer> exitCode = CompletableFuture.supplyAsync(() -> waitFor(process));
      try {
        if (exitCode.get(20, TimeUnit.SECONDS) != 0) {
          return null;
        }
        return readJson(Files.readAllBytes(output));
      } finally {
        if (process.isAlive()) {
          process.destroyForcibly();
        }
      }
    } catch (TimeoutException error) {
      log.debug("Bitget PowerShell fallback timed out: {}", url);
      return null;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      return null;
    } catch (Exception error) {
      log.debug("Bitget PowerShell fallback failed: {}", error.getMessage());
      return null;
    } finally {
      delete(output);
    }
  }

  private JsonNode readJson(byte[] data) {
    if (data == null || data.length == 0) {
      return null;
    }
    try {
      return mapper.readTree(data);
    } catch (IOException error) {
      return null;
    }
  }

  private int waitFor(Process process) {
    try {
      return process.waitFor();
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      return -1;
    }
  }

  private void delete(Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException error) {
      log.debug("Bitget temp file cleanup failed: {}", error.getMessage());
    }
  }
}
