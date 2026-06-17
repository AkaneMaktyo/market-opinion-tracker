package com.personal.tracker.service.youtube;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Date;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AliyunOssClient {
  private final String accessKeyId;
  private final String accessKeySecret;
  private final String endpoint;
  private final String bucketName;
  private final String prefix;
  private final long expireSeconds;

  public AliyunOssClient(Environment environment) {
    this.accessKeyId = value(environment, "ALIYUN_AK_ID");
    this.accessKeySecret = value(environment, "ALIYUN_AK_SECRET");
    this.endpoint = normalizeEndpoint(value(environment, "ALIYUN_OSS_ENDPOINT"));
    this.bucketName = value(environment, "ALIYUN_OSS_BUCKET");
    this.prefix = defaulted(value(environment, "ALIYUN_OSS_PREFIX"), "youtube-audio");
    this.expireSeconds = Math.max(600, longValue(environment, "ALIYUN_OSS_SIGN_EXPIRE_SECONDS", 86400));
  }

  public UploadResult upload(String audioPath) {
    Path path = Path.of(audioPath);
    if (!Files.exists(path)) {
      throw new IllegalArgumentException("音频文件不存在: " + audioPath);
    }
    String objectKey = prefix + "/" + path.getFileName().toString().replace(" ", "-");
    OSS client = build();
    try {
      client.putObject(bucketName, objectKey, path.toFile());
      return new UploadResult(objectKey, sign(client, objectKey));
    } finally {
      client.shutdown();
    }
  }

  public String signVideoAudio(String videoId) {
    String objectKey = prefix + "/" + videoId + ".aliyun.wav";
    OSS client = build();
    try {
      if (!client.doesObjectExist(bucketName, objectKey)) {
        return "";
      }
      return sign(client, objectKey);
    } finally {
      client.shutdown();
    }
  }

  public String signStoredAudio(String audioPath) {
    if (audioPath == null || audioPath.isBlank()) {
      return "";
    }
    Path path = Path.of(audioPath.replace("\\", "/"));
    if (path.getFileName() == null) {
      return "";
    }
    String objectKey = prefix + "/" + path.getFileName().toString().replace(" ", "-");
    OSS client = build();
    try {
      if (!client.doesObjectExist(bucketName, objectKey)) {
        return "";
      }
      return sign(client, objectKey);
    } finally {
      client.shutdown();
    }
  }

  private OSS build() {
    if (accessKeyId.isBlank() || accessKeySecret.isBlank()) {
      throw new IllegalArgumentException("缺少阿里云凭证，请配置 ALIYUN_AK_ID 和 ALIYUN_AK_SECRET");
    }
    if (endpoint.isBlank() || bucketName.isBlank()) {
      throw new IllegalArgumentException("缺少 OSS 配置，请配置 ALIYUN_OSS_ENDPOINT 和 ALIYUN_OSS_BUCKET");
    }
    return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
  }

  private String sign(OSS client, String objectKey) {
    Date expiration = new Date(System.currentTimeMillis() + Duration.ofSeconds(expireSeconds).toMillis());
    return client.generatePresignedUrl(bucketName, objectKey, expiration).toString();
  }

  private static String value(Environment environment, String key) {
    String value = environment.getProperty(key);
    return value == null ? "" : value.trim();
  }

  private static String defaulted(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static long longValue(Environment environment, String key, long fallback) {
    String raw = environment.getProperty(key);
    if (raw == null || raw.isBlank()) {
      return fallback;
    }
    try {
      return Long.parseLong(raw.trim());
    } catch (NumberFormatException error) {
      return fallback;
    }
  }

  private static String normalizeEndpoint(String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    String value = raw.trim().replaceAll("/+$", "");
    return value.startsWith("http") ? value : "https://" + value;
  }

  public record UploadResult(String objectKey, String fileLink) {
  }
}
