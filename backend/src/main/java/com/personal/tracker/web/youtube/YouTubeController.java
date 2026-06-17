package com.personal.tracker.web.youtube;

import com.personal.tracker.repository.youtube.YouTubeRepository.VideoRecord;
import com.personal.tracker.service.youtube.YouTubeAdminService;
import com.personal.tracker.service.youtube.YouTubeAdminService.DashboardChannel;
import com.personal.tracker.service.youtube.YouTubeAdminService.SyncResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/youtube")
public class YouTubeController {
  private final YouTubeAdminService service;

  public YouTubeController(YouTubeAdminService service) {
    this.service = service;
  }

  @GetMapping("/channels")
  ChannelListResponse channels() {
    return new ChannelListResponse(service.listDashboard());
  }

  @PostMapping("/channels")
  CreateChannelResponse createChannel(@RequestBody CreateChannelRequest request) {
    return new CreateChannelResponse(true, service.addChannel(request.sourceUrl(), request.name()));
  }

  @PostMapping("/channels/{channelRowId}/sync")
  CreateChannelResponse syncChannel(@PathVariable String channelRowId) {
    return new CreateChannelResponse(true, service.syncChannel(channelRowId));
  }

  @PostMapping("/sync")
  SyncAllResponse syncAll() {
    return new SyncAllResponse(true, service.syncAll());
  }

  @GetMapping("/videos/{videoId}")
  VideoResponse video(@PathVariable String videoId) {
    VideoRecord video = service.getVideo(videoId);
    if (video == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "视频不存在");
    }
    return new VideoResponse(true, video);
  }

  @GetMapping("/audio/{videoId}")
  ResponseEntity<Resource> audio(@PathVariable String videoId) {
    VideoRecord video = service.getVideo(videoId);
    if (video == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "音频不存在");
    }
    Path path = video.audioPath() == null || video.audioPath().isBlank()
        ? null
        : Path.of(video.audioPath().replace("\\", "/"));
    if (path != null && Files.exists(path)) {
      return fileResponse(path);
    }
    String link = service.getCloudAudioLink(videoId);
    if (!link.isBlank()) {
      return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
          .header(HttpHeaders.LOCATION, link)
          .build();
    }
    VideoRecord refreshed = service.ensureAudio(videoId);
    Path refreshedPath = refreshed == null || refreshed.audioPath() == null || refreshed.audioPath().isBlank()
        ? null
        : Path.of(refreshed.audioPath().replace("\\", "/"));
    if (refreshedPath != null && Files.exists(refreshedPath)) {
      return fileResponse(refreshedPath);
    }
    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "音频文件不存在");
  }

  @DeleteMapping("/channels/{channelRowId}")
  DeleteResponse deleteChannel(@PathVariable String channelRowId) {
    if (!service.deleteChannel(channelRowId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "频道不存在");
    }
    return new DeleteResponse(true);
  }

  private ResponseEntity<Resource> fileResponse(Path path) {
    MediaType contentType = MediaTypeFactory.getMediaType(path.getFileName().toString())
        .orElse(MediaType.APPLICATION_OCTET_STREAM);
    return ResponseEntity.ok()
        .contentType(contentType)
        .body(new FileSystemResource(path));
  }

  public record CreateChannelRequest(String sourceUrl, String name) {
  }

  public record ChannelListResponse(List<DashboardChannel> channels) {
  }

  public record CreateChannelResponse(boolean ok, SyncResult result) {
  }

  public record SyncAllResponse(boolean ok, List<SyncResult> results) {
  }

  public record VideoResponse(boolean ok, VideoRecord video) {
  }

  public record DeleteResponse(boolean ok) {
  }
}
