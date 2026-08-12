package com.personal.tracker.service.youtube;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GetObjectRequest;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
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
    String raw = audioPath.trim().replace("\\", "/");
    if (raw.startsWith("oss://")) {
      return signExisting(raw.substring("oss://".length()));
    }
    Path path = Path.of(audioPath.replace("\\", "/"));
    if (path.getFileName() == null) {
      return "";
    }
    String fileName = path.getFileName().toString().replace(" ", "-");
    return signFirstExisting(List.of(prefix + "/" + fileName, "youtube-bridge/audio/" + fileName));
  }

  private String signFirstExisting(List<String> objectKeys) {
    OSS client = build();
    try {
      for (String objectKey : objectKeys) {
        String clean = cleanKey(objectKey);
        if (client.doesObjectExist(bucketName, clean)) {
          return sign(client, clean);
        }
      }
      return "";
    } finally {
      client.shutdown();
    }
  }

  private String signExisting(String objectKey) {
    OSS client = build();
    try {
      String clean = cleanKey(objectKey);
      return client.doesObjectExist(bucketName, clean) ? sign(client, clean) : "";
    } finally {
      client.shutdown();
    }
  }

  public boolean exists(String objectKey) {
    OSS client = build();
    try {
      return client.doesObjectExist(bucketName, cleanKey(objectKey));
    } finally {
      client.shutdown();
    }
  }

  public String readText(String objectKey) {
    OSS client = build();
    try {
      String key = cleanKey(objectKey);
      if (!client.doesObjectExist(bucketName, key)) {
        return "";
      }
      try (InputStream input = client.getObject(bucketName, key).getObjectContent()) {
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
      }
    } catch (Exception error) {
      throw new IllegalArgumentException("读取 OSS 文本失败: " + objectKey, error);
    } finally {
      client.shutdown();
    }
  }

  public void putText(String objectKey, String content) {
    OSS client = build();
    try {
      byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
      client.putObject(bucketName, cleanKey(objectKey), new ByteArrayInputStream(bytes));
    } finally {
      client.shutdown();
    }
  }

  public void download(String objectKey, Path target) {
    OSS client = build();
    try {
      Path parent = target.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      client.getObject(new GetObjectRequest(bucketName, cleanKey(objectKey)), target.toFile());
    } catch (Exception error) {
      throw new IllegalArgumentException("下载 OSS 音频失败: " + objectKey, error);
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
    return secureUrl(client.generatePresignedUrl(bucketName, objectKey, expiration).toString());
  }

  static String secureUrl(String url) {
    if (url == null || url.isBlank()) {
      return "";
    }
    return url.startsWith("http://") ? "https://" + url.substring("http://".length()) : url;
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

  private static String cleanKey(String objectKey) {
    return objectKey == null ? "" : objectKey.replace("\\", "/").replaceAll("^/+", "");
  }

  public record UploadResult(String objectKey, String fileLink) {
  }
}
