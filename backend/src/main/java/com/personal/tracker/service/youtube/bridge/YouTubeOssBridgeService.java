package com.personal.tracker.service.youtube.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.personal.tracker.repository.JdbcSupport;
import com.personal.tracker.repository.youtube.YouTubeRepository;
import com.personal.tracker.service.youtube.AliyunOssClient;
import com.personal.tracker.service.youtube.YouTubeAdminService;
import com.personal.tracker.service.youtube.model.ImportedVideo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class YouTubeOssBridgeService {
  private final ObjectMapper objectMapper;
  private final YouTubeRepository repository;
  private final AliyunOssClient ossClient;
  private final YouTubeAdminService adminService;
  private final Path audioRoot;
  private final String prefix;

  public YouTubeOssBridgeService(
      ObjectMapper objectMapper,
      YouTubeRepository repository,
      AliyunOssClient ossClient,
      YouTubeAdminService adminService,
      Environment environment) {
    this.objectMapper = objectMapper;
    this.repository = repository;
    this.ossClient = ossClient;
    this.adminService = adminService;
    this.audioRoot = Path.of(value(environment, "YOUTUBE_AUDIO_DIR", "data/youtube_audio"))
        .toAbsolutePath()
        .normalize();
    this.prefix = value(environment, "YOUTUBE_OSS_BRIDGE_PREFIX", "youtube-bridge").replaceAll("/+$", "");
  }

  public BridgeSummary sync() {
    int channels = exportChannels();
    int imported = importLatest();
    return new BridgeSummary(channels, imported);
  }

  public int exportChannels() {
    List<ChannelExport> channels = repository.listChannels().stream()
        .filter(YouTubeRepository.ChannelRecord::enabled)
        .map(channel -> new ChannelExport(
            channel.channelId(), channel.title(), channel.handle(), channel.sourceUrl()))
        .toList();
    write(key("channels.json"), new ChannelList(JdbcSupport.now(), channels));
    return channels.size();
  }

  public int importLatest() {
    String payload = ossClient.readText(key("latest.json"));
    if (payload.isBlank()) {
      return 0;
    }
    FetchManifest manifest = read(payload, FetchManifest.class);
    int imported = 0;
    for (FetchedChannel channel : safe(manifest.channels())) {
      for (FetchedVideo video : safe(channel.videos())) {
        if (importVideo(channel, video)) {
          imported++;
        }
      }
    }
    return imported;
  }

  private boolean importVideo(FetchedChannel channel, FetchedVideo video) {
    if (video.videoId() == null || video.audioObjectKey() == null || video.audioObjectKey().isBlank()) {
      return false;
    }
    Path audioPath = audioRoot.resolve(video.videoId() + extension(video.audioObjectKey())).normalize();
    if (!audioPath.startsWith(audioRoot)) {
      throw new IllegalArgumentException("OSS 音频路径不安全: " + video.audioObjectKey());
    }
    if (!Files.exists(audioPath)) {
      ossClient.download(video.audioObjectKey(), audioPath);
    }
    adminService.importFetchedVideo(new ImportedVideo(
        channel.channelId(),
        channel.title(),
        channel.handle(),
        channel.sourceUrl(),
        video.videoId(),
        video.title(),
        video.videoUrl(),
        video.publishedAt(),
        audioPath.toString(),
        video.audioDurationMs()));
    return true;
  }

  private String key(String name) {
    return prefix.isBlank() ? name : prefix + "/" + name;
  }

  private void write(String objectKey, Object payload) {
    try {
      ossClient.putText(objectKey, objectMapper.writeValueAsString(payload));
    } catch (Exception error) {
      throw new IllegalArgumentException("写入 OSS 桥接清单失败", error);
    }
  }

  private <T> T read(String payload, Class<T> type) {
    try {
      return objectMapper.readValue(payload, type);
    } catch (Exception error) {
      throw new IllegalArgumentException("解析 OSS 桥接清单失败", error);
    }
  }

  private static String extension(String objectKey) {
    String name = Path.of(objectKey.replace("\\", "/")).getFileName().toString();
    int index = name.lastIndexOf('.');
    return index < 0 ? ".m4a" : name.substring(index);
  }

  private static String value(Environment environment, String key, String fallback) {
    String value = environment.getProperty(key);
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private static <T> List<T> safe(List<T> items) {
    return items == null ? List.of() : items;
  }

  public record BridgeSummary(int exportedChannels, int importedVideos) {
  }

  private record ChannelList(String generatedAt, List<ChannelExport> channels) {
  }

  private record ChannelExport(String channelId, String title, String handle, String sourceUrl) {
  }

  private record FetchManifest(String generatedAt, List<FetchedChannel> channels) {
  }

  private record FetchedChannel(
      String channelId,
      String title,
      String handle,
      String sourceUrl,
      List<FetchedVideo> videos) {
  }

  private record FetchedVideo(
      String videoId,
      String title,
      String videoUrl,
      String publishedAt,
      String audioObjectKey,
      long audioDurationMs) {
  }
}
