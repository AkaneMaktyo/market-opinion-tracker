package com.personal.tracker.service.youtube;

import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.http.FormatType;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AliyunFileTransClient {
  static final String QUOTA_STATUS = "USER_BIZDURATION_QUOTA_EXCEED";
  private final ObjectMapper objectMapper;
  private final String appKey;
  private final String accessKeyId;
  private final String accessKeySecret;
  private final String region;
  private final String product;
  private final String domain;
  private final String version;
  private final boolean enableSampleRateAdaptive;
  private final int pollIntervalSeconds;
  private final int pollAttempts;

  public AliyunFileTransClient(ObjectMapper objectMapper, Environment environment) {
    this.objectMapper = objectMapper;
    this.appKey = value(environment, "ALIYUN_NLS_APP_KEY");
    this.accessKeyId = value(environment, "ALIYUN_AK_ID");
    this.accessKeySecret = value(environment, "ALIYUN_AK_SECRET");
    this.region = firstNonBlank(
        value(environment, "ALIYUN_FILETRANS_REGION"),
        value(environment, "ALIYUN_NLS_REGION"),
        "cn-shanghai");
    this.product = firstNonBlank(value(environment, "ALIYUN_FILETRANS_PRODUCT"), "nls-filetrans");
    this.domain = firstNonBlank(value(environment, "ALIYUN_FILETRANS_DOMAIN"), "filetrans." + region + ".aliyuncs.com");
    this.version = firstNonBlank(value(environment, "ALIYUN_FILETRANS_VERSION"), "2018-08-17");
    this.enableSampleRateAdaptive = boolValue(environment, "ALIYUN_FILETRANS_ENABLE_SAMPLE_RATE_ADAPTIVE", true);
    this.pollIntervalSeconds = Math.max(2, intValue(environment, "ALIYUN_FILETRANS_POLL_SECONDS", 5));
    this.pollAttempts = Math.max(12, intValue(environment, "ALIYUN_FILETRANS_POLL_ATTEMPTS", 120));
  }

  public JsonNode transcribe(String fileLink) {
    validate();
    String taskId = submit(fileLink);
    for (int i = 0; i < pollAttempts; i++) {
      JsonNode payload = request("GetTaskResult", Map.of("TaskId", taskId), MethodType.GET, true);
      String status = payload.path("StatusText").asText("").toUpperCase();
      if ("SUCCESS".equals(status)) {
        return resultPayload(payload);
      }
      if ("SUCCESS_WITH_NO_VALID_FRAGMENT".equals(status)) {
        throw new IllegalArgumentException("阿里云识别完成，但没有返回有效句子");
      }
      if ("RUNNING".equals(status) || "QUEUEING".equals(status)) {
        sleep();
        continue;
      }
      throw new IllegalArgumentException("阿里云录音文件识别失败: " + payload);
    }
    throw new IllegalArgumentException("阿里云录音文件识别超时，请稍后重试");
  }

  static boolean isQuotaExceeded(Throwable error) {
    if (error == null) {
      return false;
    }
    if (error instanceof QuotaExceededException) {
      return true;
    }
    String message = error.getMessage();
    return message != null && (message.contains(QUOTA_STATUS) || message.contains("配额不足"));
  }

  private String submit(String fileLink) {
    String task = buildTaskPayload(appKey, fileLink, enableSampleRateAdaptive);
    JsonNode payload = request("SubmitTask", Map.of("Task", task), MethodType.POST, false);
    String taskId = payload.path("TaskId").asText("");
    if (!"SUCCESS".equalsIgnoreCase(payload.path("StatusText").asText("")) || taskId.isBlank()) {
      throw submitError(payload);
    }
    return taskId;
  }

  static String buildTaskPayload(String appKey, String fileLink, boolean enableSampleRateAdaptive) {
    return "{\"appkey\":\"" + appKey + "\",\"file_link\":\"" + fileLink
        + "\",\"version\":\"4.0\",\"enable_words\":false,\"enable_sample_rate_adaptive\":"
        + enableSampleRateAdaptive + "}";
  }

  private JsonNode request(String action, Map<String, String> params, MethodType method, boolean query) {
    try {
      DefaultProfile.addEndpoint(region, region, product, domain);
      DefaultProfile profile = DefaultProfile.getProfile(region, accessKeyId, accessKeySecret);
      DefaultAcsClient client = new DefaultAcsClient(profile);
      CommonRequest request = new CommonRequest();
      request.setDomain(domain);
      request.setVersion(version);
      request.setProduct(product);
      request.setAction(action);
      request.setMethod(method);
      request.setHttpContentType(FormatType.JSON);
      for (Map.Entry<String, String> entry : params.entrySet()) {
        if (query) {
          request.putQueryParameter(entry.getKey(), entry.getValue());
        } else {
          request.putBodyParameter(entry.getKey(), entry.getValue());
        }
      }
      CommonResponse response = client.getCommonResponse(request);
      if (response.getHttpStatus() >= 400) {
        throw new IllegalArgumentException(
            "阿里云请求失败: HTTP " + response.getHttpStatus() + " " + response.getData());
      }
      return objectMapper.readTree(response.getData());
    } catch (IllegalArgumentException error) {
      throw error;
    } catch (Exception error) {
      throw new IllegalArgumentException("调用阿里云录音文件识别失败: " + error.getMessage(), error);
    }
  }

  private JsonNode resultPayload(JsonNode payload) {
    JsonNode result = payload.path("Result");
    if (result.isTextual()) {
      try {
        return objectMapper.readTree(result.asText(""));
      } catch (Exception error) {
        return objectMapper.createObjectNode().put("raw", result.asText(""));
      }
    }
    return result;
  }

  private void validate() {
    if (appKey.isBlank()) {
      throw new IllegalArgumentException("缺少阿里云 AppKey，请配置 ALIYUN_NLS_APP_KEY");
    }
    if (accessKeyId.isBlank() || accessKeySecret.isBlank()) {
      throw new IllegalArgumentException("缺少阿里云凭证，请配置 ALIYUN_AK_ID 和 ALIYUN_AK_SECRET");
    }
  }

  private IllegalArgumentException submitError(JsonNode payload) {
    String status = payload.path("StatusText").asText("").toUpperCase();
    if (QUOTA_STATUS.equals(status)) {
      return new QuotaExceededException("阿里云录音文件识别配额不足");
    }
    return new IllegalArgumentException("阿里云录音文件任务提交失败: " + payload);
  }

  private void sleep() {
    try {
      Thread.sleep(pollIntervalSeconds * 1000L);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IllegalArgumentException("轮询阿里云识别结果时被中断", error);
    }
  }

  private static String value(Environment environment, String key) {
    String value = environment.getProperty(key);
    return value == null ? "" : value.trim();
  }

  private static int intValue(Environment environment, String key, int fallback) {
    String raw = environment.getProperty(key);
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException error) {
      return fallback;
    }
  }

  private static boolean boolValue(Environment environment, String key, boolean fallback) {
    String raw = environment.getProperty(key);
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    return Boolean.parseBoolean(raw.trim());
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return "";
  }

  static class QuotaExceededException extends IllegalArgumentException {
    QuotaExceededException(String message) {
      super(message);
    }
  }
}
