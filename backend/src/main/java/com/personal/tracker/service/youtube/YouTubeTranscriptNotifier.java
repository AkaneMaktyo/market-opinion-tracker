package com.personal.tracker.service.youtube;

import com.personal.tracker.repository.youtube.YouTubeRepository.ChannelRecord;
import com.personal.tracker.repository.youtube.YouTubeRepository.VideoRecord;
import com.personal.tracker.service.notify.WxPusherPushClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class YouTubeTranscriptNotifier {
  private static final Logger log = LoggerFactory.getLogger(YouTubeTranscriptNotifier.class);
  private final WxPusherPushClient pushClient;

  public YouTubeTranscriptNotifier(WxPusherPushClient pushClient) {
    this.pushClient = pushClient;
  }

  public void notifyTranscriptReady(ChannelRecord channel, VideoRecord video) {
    if (!pushClient.isConfigured("YOUTUBE", "RESONANCE", "POSITION_NOTIFY")) {
      return;
    }
    WxPusherPushClient.PushResult result = pushClient.send(
        title(video),
        content(channel, video),
        "YOUTUBE",
        "RESONANCE",
        "POSITION_NOTIFY");
    if (!result.ok()) {
      log.warn("YouTube 转写完成推送失败: videoId={}, error={}", video.videoId(), result.error());
    }
  }

  private String title(VideoRecord video) {
    return "YouTube 转写完成: " + video.title();
  }

  private String content(ChannelRecord channel, VideoRecord video) {
    String channelName = channel.title() == null || channel.title().isBlank()
        ? channel.channelId()
        : channel.title();
    return """
        【YouTube 转写完成】
        频道：%s
        视频：%s
        发布时间：%s
        视频链接：%s
        """.formatted(
        channelName,
        blank(video.title(), video.videoId()),
        blank(video.publishedAt(), "未知"),
        blank(video.videoUrl(), "未记录"));
  }

  private String blank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
