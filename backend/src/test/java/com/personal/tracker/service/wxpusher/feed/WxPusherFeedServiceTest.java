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
        processedBlogger.isBlank() ? "RECEIVED" : "IMPORTED",
        "NOT_STARTED",
        "",
        0);
  }

  @Test
  void searchNormalizesTermsAndRanksTitleMatchFirst() {
    var messages = mock(WxPusherSharedMessageRepository.class);
    var service = new WxPusherFeedService(
        messages,
        mock(WxPusherSettingsRepository.class),
        mock(WxPusherArticleExtractor.class));
    RecentMessage detailHit = message("detail", "牛顿师兄", "普通消息", "NVDA 英伟达继续走强");
    RecentMessage titleHit = new RecentMessage(
        "title", "key-title", "CIA-信息推送", "舒琴", "", "NVDA 英伟达机会",
        "盘中观察", "", "", "2026-08-05T14:11:15Z", "盘中观察", "IMPORTED",
        "NOT_STARTED", "", 0);
    when(messages.searchRecentFeed(List.of("nvda", "英伟达"), 365, 400))
        .thenReturn(List.of(detailHit, titleHit));

    var result = service.search("  NVDA，英伟达  ", 500, 100);

    assertEquals(List.of("title", "detail"), result.stream().map(WxPusherFeedService.FeedMessage::id).toList());
    verify(messages).searchRecentFeed(List.of("nvda", "英伟达"), 365, 400);
  }

  @Test
  void searchIgnoresImagePayloadButKeepsOcrMatches() {
    var messages = mock(WxPusherSharedMessageRepository.class);
    var service = new WxPusherFeedService(
        messages,
        mock(WxPusherSettingsRepository.class),
        mock(WxPusherArticleExtractor.class));
    RecentMessage imagePayloadHit = message(
        "image-payload", "牛顿师兄", "普通消息",
        "正文没有股票代码\nWXPUSHER_IMAGE_URL=data:image/jpeg;base64,AAmcdBB");
    RecentMessage ocrHit = message(
        "ocr", "舒琴", "图中观点",
        "WXPUSHER_IMAGE_URL=data:image/jpeg;base64,AA==\n"
            + "[图片转文字 1]\nMCD 关注支撑位\n[/图片转文字]");
    when(messages.searchRecentFeed(List.of("mcd"), 365, 200))
        .thenReturn(List.of(imagePayloadHit, ocrHit));

    var result = service.search("mcd", 365, 50);

    assertEquals(List.of("ocr"), result.stream().map(WxPusherFeedService.FeedMessage::id).toList());
    verify(messages).searchRecentFeed(List.of("mcd"), 365, 200);
  }
}
