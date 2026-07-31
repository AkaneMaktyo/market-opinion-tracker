package com.personal.tracker.service.wxpusher.ocr;

import com.aliyun.ocr_api20210707.models.RecognizeGeneralRequest;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.config.WxPusherOcrProperties;
import java.io.ByteArrayInputStream;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AliyunGeneralOcrClient {
  private final ObjectMapper mapper;
  private final WxPusherOcrProperties properties;
  private final String accessKeyId;
  private final String accessKeySecret;

  public AliyunGeneralOcrClient(
      ObjectMapper mapper,
      WxPusherOcrProperties properties,
      Environment environment) {
    this.mapper = mapper;
    this.properties = properties;
    this.accessKeyId = first(
        environment.getProperty("ALIYUN_AK_ID"),
        environment.getProperty("ALIBABA_CLOUD_ACCESS_KEY_ID"));
    this.accessKeySecret = first(
        environment.getProperty("ALIYUN_AK_SECRET"),
        environment.getProperty("ALIBABA_CLOUD_ACCESS_KEY_SECRET"));
  }

  public String recognize(String imageUrl) {
    validate();
    try {
      Config config = new Config()
          .setAccessKeyId(accessKeyId)
          .setAccessKeySecret(accessKeySecret);
      config.endpoint = properties.endpoint();
      var client = new com.aliyun.ocr_api20210707.Client(config);
      var response = client.recognizeGeneralWithOptions(
          buildRequest(imageUrl),
          new RuntimeOptions());
      String data = response.body == null ? "" : value(response.body.data);
      JsonNode payload = data.isBlank() ? mapper.createObjectNode() : mapper.readTree(data);
      String content = payload.path("content").asText("").trim();
      if (content.isBlank()) {
        String message = response.body == null ? "" : value(response.body.message);
        throw new IllegalStateException("阿里云 OCR 未识别出文字" + suffix(message));
      }
      return content;
    } catch (IllegalStateException error) {
      throw error;
    } catch (Exception error) {
      throw new IllegalStateException("调用阿里云 OCR 失败: " + value(error.getMessage()), error);
    }
  }

  private void validate() {
    if (accessKeyId.isBlank() || accessKeySecret.isBlank()) {
      throw new IllegalStateException("缺少阿里云 OCR 凭证，请配置 ALIYUN_AK_ID 和 ALIYUN_AK_SECRET");
    }
  }

  static RecognizeGeneralRequest buildRequest(String imageSource) {
    String source = value(imageSource);
    if (OcrImageSource.isRemote(source)) {
      return new RecognizeGeneralRequest().setUrl(source);
    }
    return new RecognizeGeneralRequest().setBody(
        new ByteArrayInputStream(OcrImageSource.embeddedBytes(source)));
  }

  private static String first(String first, String second) {
    return !value(first).isBlank() ? value(first) : value(second);
  }

  private static String suffix(String message) {
    return message.isBlank() ? "" : ": " + message;
  }

  private static String value(String input) {
    return input == null ? "" : input.trim();
  }
}
