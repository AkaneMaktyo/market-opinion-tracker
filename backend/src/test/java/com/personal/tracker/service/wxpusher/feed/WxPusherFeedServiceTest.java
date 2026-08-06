package com.personal.tracker.service.wxpusher.feed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository.WxPusherSettings;
import com.personal.tracker.repository.wxpusher.WxPusherSharedMessageRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSharedMessageRepository.RecentMessage;
import com.personal.tracker.service.wxpusher.WxPusherArticleExtractor;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WxPusherFeedServiceTest {
  @Test
  void recentUsesRawInboxAndDerivesKolNames() {
    var messages = mock(WxPusherSharedMessageRepository.class);
    var service = new WxPusherFeedService(
        messages,
        mock(WxPusherSettingsRepository.class),
        mock(WxPusherArticleExtractor.class));
    when(messages.listRecentFeed(50)).thenReturn(List.of(
        message("1", "", "[🟢｜舒琴行情分析] 舒琴: GOOG 做多", ""),
        message("2", "", "[💎｜顺哥vip小群] jiaozhu: signal.jpg", ""),
        message("3", "牛顿师兄", "正文", "已提取正文")));

    var result = service.recent(50);

    assertEquals(List.of("舒琴行情分析", "顺哥", "牛顿师兄"),
        result.stream().map(WxPusherFeedService.FeedMessage::bloggerName).toList());
    assertEquals("已提取正文", result.get(2).detailText());
  }

  @Test
  void detailFetchesImageSourceOnlyForImageMessages() {
    var messages = mock(WxPusherSharedMessageRepository.class);
    var settings = mock(WxPusherSettingsRepository.class);
    var articles = mock(WxPusherArticleExtractor.class);
    var service = new WxPusherFeedService(messages, settings, articles);
    RecentMessage message = message(
        "image-1", "", "[💎｜顺哥vip小群] jiaozhu: signal.jpg", "");
    WxPusherSettings current = mock(WxPusherSettings.class);
    when(messages.findRecentFeedById("image-1")).thenReturn(Optional.of(message));
    when(settings.get()).thenReturn(current);
    when(articles.fetchText(message.detailUrl(), current))
        .thenReturn("WXPUSHER_IMAGE_URL=data:image/jpeg;base64,AA==");

    var result = service.detail("image-1");

    assertEquals("WXPUSHER_IMAGE_URL=data:image/jpeg;base64,AA==", result.detailText());
    verify(articles).fetchText(message.detailUrl(), current);
  }

  private RecentMessage message(
      String id,
      String processedBlogger,
      String summary,
      String detailText) {
    return new RecentMessage(
        id,
        "key-" + id,
        "CIA-信息推送",
        processedBlogger,
        "",
        "title",
        summary,
        "https://wxpusher.zjiecode.com/api/message/v2/" + id,
        "https://discord.example/" + id,
        "2026-08-04T14:11:15Z",
        detailText,
        processedBlogger.isBlank() ? "RECEIVED" : "IMPORTED");
  }
}
