package com.personal.tracker.service.youtube.notify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.repository.youtube.YouTubeRepository;
import com.personal.tracker.repository.youtube.YouTubeRepository.ChannelRecord;
import com.personal.tracker.repository.youtube.YouTubeRepository.TranscriptSegment;
import com.personal.tracker.repository.youtube.YouTubeRepository.VideoRecord;
import com.personal.tracker.service.notify.WxPusherPushClient;
import com.personal.tracker.service.youtube.YouTubeAdminService;
import com.personal.tracker.service.youtube.YouTubeTranscriptNotifier;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class YouTubeNotificationRetryServiceTest {
  @Test
  void retriesReadyVideosWhenPushConfigBecomesAvailable() {
    var repository = mock(YouTubeRepository.class);
    var notifier = mock(YouTubeTranscriptNotifier.class);
    var service = new YouTubeNotificationRetryService(repository, notifier, new MockEnvironment());
    VideoRecord video = video("WAITING_CONFIG", "");
    ChannelRecord channel = channel();
    when(notifier.pushReady()).thenReturn(true);
    when(repository.listVideosPendingNotification(20)).thenReturn(List.of(video));
    when(repository.findChannel("channel-row")).thenReturn(Optional.of(channel));
    when(notifier.notifyTranscriptReady(channel, video))
        .thenReturn(new WxPusherPushClient.PushResult(true, "SENT", ""));

    int retried = service.retryPendingNotifications();

    assertEquals(1, retried);
    verify(notifier).notifyTranscriptReady(channel, video);
    verify(repository).markNotification(eq("vid-1"), eq("SENT"), eq(""), any(), any());
  }

  @Test
  void skipsRetryWhenPushTargetIsNotReady() {
    var repository = mock(YouTubeRepository.class);
    var notifier = mock(YouTubeTranscriptNotifier.class);
    var service = new YouTubeNotificationRetryService(repository, notifier, new MockEnvironment());
    when(notifier.pushReady()).thenReturn(false);

    int retried = service.retryPendingNotifications();

    assertEquals(0, retried);
    verify(repository, never()).listVideosPendingNotification(anyInt());
  }

  @Test
  void ignoresReadyStatusWithoutTranscriptPayload() {
    var repository = mock(YouTubeRepository.class);
    var notifier = mock(YouTubeTranscriptNotifier.class);
    var service = new YouTubeNotificationRetryService(repository, notifier, new MockEnvironment());
    when(notifier.pushReady()).thenReturn(true);
    when(repository.listVideosPendingNotification(20)).thenReturn(List.of(emptyTranscriptVideo()));

    int retried = service.retryPendingNotifications();

    assertEquals(0, retried);
    verify(notifier, never()).notifyTranscriptReady(any(), any());
    verify(repository, never()).markNotification(any(), any(), any(), any(), any());
  }

  private ChannelRecord channel() {
    return new ChannelRecord(
        "channel-row",
        "channel-id",
        "Channel",
        "@channel",
        "https://example.com/channel",
        true,
        "",
        "",
        "created",
        "updated");
  }

  private VideoRecord video(String notifyStatus, String notifiedAt) {
    return new VideoRecord(
        "vid-1",
        "channel-row",
        "channel-id",
        "Video",
        "https://example.com/watch?v=1",
        "2026-06-12T08:00:00Z",
        "audio.m4a",
        1234,
        YouTubeAdminService.TRANSCRIPT_READY,
        "zh",
        "aliyun_filetrans",
        "转写完成",
        List.of(new TranscriptSegment(0, 1000, "转写完成")),
        "",
        notifyStatus,
        "",
        notifiedAt,
        "",
        "2026-06-12T08:05:00Z",
        "created",
        "updated");
  }

  private VideoRecord emptyTranscriptVideo() {
    return new VideoRecord(
        "vid-1",
        "channel-row",
        "channel-id",
        "Video",
        "https://example.com/watch?v=1",
        "2026-06-12T08:00:00Z",
        "audio.m4a",
        1234,
        YouTubeAdminService.TRANSCRIPT_READY,
        "zh",
        "aliyun_filetrans",
        "",
        List.of(),
        "",
        "",
        "",
        "",
        "",
        "2026-06-12T08:05:00Z",
        "created",
        "updated");
  }
}
