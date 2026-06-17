package com.personal.tracker.service.youtube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class YouTubeClientTest {
  @Test
  void directChannelIdSupportsPlainIdAndUrl() {
    assertEquals("UCabc123", YouTubeClient.directChannelId("UCabc123"));
    assertEquals("UCxyz987", YouTubeClient.directChannelId("https://www.youtube.com/channel/UCxyz987"));
  }

  @Test
  void searchReturnsEmptyWhenNotFound() {
    assertEquals("", YouTubeClient.search("hello", "world"));
  }

  @Test
  void parseFeedExtractsLatestVideos() {
    String xml = """
        <feed xmlns="http://www.w3.org/2005/Atom" xmlns:yt="http://www.youtube.com/xml/schemas/2015">
          <entry>
            <yt:videoId>vid-1</yt:videoId>
            <title>Alpha</title>
            <published>2026-06-12T08:00:00+00:00</published>
          </entry>
          <entry>
            <yt:videoId>vid-2</yt:videoId>
            <title>Beta</title>
            <published>2026-06-12T07:00:00+00:00</published>
          </entry>
        </feed>
        """;

    List<YouTubeClient.VideoMetadata> videos = YouTubeClient.parseFeed(xml, 1);

    assertEquals(1, videos.size());
    assertEquals("vid-1", videos.get(0).videoId());
    assertTrue(videos.get(0).videoUrl().contains("vid-1"));
  }
}
