package com.personal.tracker.service.wxpusher.ocr;

import com.personal.tracker.config.WxPusherOcrProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class TesseractOcrClient {
  private final WxPusherOcrProperties properties;
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(20))
      .build();

  public TesseractOcrClient(WxPusherOcrProperties properties) {
    this.properties = properties;
  }

  public String recognize(String imageSource) {
    Path imageFile = null;
    try {
      byte[] image = imageBytes(imageSource);
      imageFile = Files.createTempFile("mot-wxpusher-ocr-", OcrImageSource.suffix(imageSource));
      Files.write(imageFile, image);
      Process process = new ProcessBuilder(
          properties.tesseractCommand(),
          imageFile.toString(),
          "stdout",
          "-l",
          properties.tesseractLanguage(),
          "--psm",
          "6").start();
      if (!process.waitFor(60, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new IllegalStateException("Tesseract OCR 处理超时");
      }
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
      String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
      if (process.exitValue() != 0) {
        throw new IllegalStateException("Tesseract OCR 失败: " + error);
      }
      if (output.isBlank()) {
        throw new IllegalStateException("Tesseract OCR 未识别出文字");
      }
      return output;
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Tesseract OCR 被中断", error);
    } catch (IOException error) {
      throw new IllegalStateException("调用 Tesseract OCR 失败: " + error.getMessage(), error);
    } finally {
      delete(imageFile);
    }
  }

  private byte[] imageBytes(String source) throws IOException, InterruptedException {
    if (!OcrImageSource.isRemote(source)) {
      return OcrImageSource.embeddedBytes(source);
    }
    HttpRequest request = HttpRequest.newBuilder(URI.create(source))
        .timeout(Duration.ofSeconds(30))
        .GET()
        .build();
    HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
    if (response.statusCode() >= 400) {
      throw new IllegalStateException("下载 OCR 图片失败: HTTP " + response.statusCode());
    }
    OcrImageSource.validateSize(response.body());
    return response.body();
  }

  private void delete(Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // 临时图片清理失败不影响 OCR 结果
    }
  }
}
