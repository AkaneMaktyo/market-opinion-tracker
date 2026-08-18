package com.personal.tracker.service.wxpusher.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.config.WxPusherOcrProperties;
import org.junit.jupiter.api.Test;

class ImageOcrClientTest {
  @Test
  void autoPrefersAliyunAndFallsBackToTesseractOnFailure() {
    var properties = properties("auto");
    var aliyun = mock(AliyunGeneralOcrClient.class);
    var tesseract = mock(TesseractOcrClient.class);
    when(aliyun.recognize("https://img.example/1.png"))
        .thenThrow(new IllegalStateException("调用阿里云 OCR 失败: ocrServiceNotOpen"));
    when(tesseract.recognize("https://img.example/1.png")).thenReturn("9.62 今天鸭梨味");
    var client = new ImageOcrClient(properties, tesseract, aliyun);

    assertEquals("9.62 今天鸭梨味", client.recognize("https://img.example/1.png"));
    verify(tesseract).recognize("https://img.example/1.png");
  }

  @Test
  void autoUsesAliyunResultWithoutCallingTesseract() {
    var properties = properties("auto");
    var aliyun = mock(AliyunGeneralOcrClient.class);
    var tesseract = mock(TesseractOcrClient.class);
    when(aliyun.recognize("https://img.example/1.png")).thenReturn("NVDA 压力 16.80 今天");
    var client = new ImageOcrClient(properties, tesseract, aliyun);

    assertEquals("NVDA 压力 16.80 今天", client.recognize("https://img.example/1.png"));
    verify(tesseract, never()).recognize("https://img.example/1.png");
  }

  @Test
  void autoPropagatesFailureWhenBothProvidersFail() {
    var properties = properties("auto");
    var aliyun = mock(AliyunGeneralOcrClient.class);
    var tesseract = mock(TesseractOcrClient.class);
    when(aliyun.recognize("https://img.example/1.png"))
        .thenThrow(new IllegalStateException("阿里云失败"));
    when(tesseract.recognize("https://img.example/1.png"))
        .thenThrow(new IllegalStateException("Tesseract 失败"));
    var client = new ImageOcrClient(properties, tesseract, aliyun);

    IllegalStateException error =
        assertThrows(IllegalStateException.class, () -> client.recognize("https://img.example/1.png"));
    assertEquals("Tesseract 失败", error.getMessage());
  }

  @Test
  void aliyunModeNeverTouchesTesseract() {
    var properties = properties("aliyun");
    var aliyun = mock(AliyunGeneralOcrClient.class);
    var tesseract = mock(TesseractOcrClient.class);
    when(aliyun.recognize("https://img.example/1.png"))
        .thenThrow(new IllegalStateException("额度不足"));
    var client = new ImageOcrClient(properties, tesseract, aliyun);

    assertThrows(IllegalStateException.class, () -> client.recognize("https://img.example/1.png"));
    verify(tesseract, never()).recognize("https://img.example/1.png");
  }

  private WxPusherOcrProperties properties(String provider) {
    var properties = new WxPusherOcrProperties();
    properties.setProvider(provider);
    return properties;
  }
}
