package com.personal.tracker.service.wxpusher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.domain.LiveSession;
import com.personal.tracker.repository.InstrumentRepository;
import com.personal.tracker.repository.SessionRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository;
import com.personal.tracker.repository.wxpusher.WxPusherBloggerRepository.WxPusherBlogger;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.PendingMessage;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.SaveResult;
import com.personal.tracker.repository.wxpusher.WxPusherMessageRepository.WxPusherMessage;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository;
import com.personal.tracker.repository.wxpusher.WxPusherSettingsRepository.WxPusherSettings;
import com.personal.tracker.repository.wxpusher.WxPusherSharedMessageRepository;
import com.personal.tracker.service.imports.OpinionImportWriter;
import com.personal.tracker.service.imports.OpinionImportWriter.WriteResult;
import com.personal.tracker.service.json.JsonOpinionParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class WxPusherIngestionRetryTest {
  @Test
  void reassignsMessageWhenDetailShowsNestedSource() throws Exception {
    var fx = fixture();
    when(fx.settings.get()).thenReturn(settings());
    when(fx.bloggers.enabled()).thenReturn(List.of(
        blogger("Alpha", "kol-1", List.of("CIA Feed")),
        blogger("Beta", "kol-beta", List.of())));
    when(fx.ai.extractionEnabled()).thenReturn(false);
    when(fx.messages.createPending(any(PendingMessage.class)))
        .thenReturn(new SaveResult(message("msg-reassign"), true));
    when(fx.sessions.create(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(session("session-beta", "kol-beta"));
    when(fx.articles.fetchText(anyString(), any())).thenReturn("*Beta\nNVDA BUY NOW");

    fx.service.ingest(incoming("Alpha"));

    verify(fx.messages).reassign("msg-reassign", "kol-beta", "Beta");
    verify(fx.sessions).create(
        eq("kol-beta"), eq("2026-05-31"), contains("WxPusher / Beta / "),
        eq("WXPUSHER_AUTO"), eq("*Beta\nNVDA BUY NOW"));
  }

  @Test
  void reassignsRetriedMessageWhenSummaryShowsNestedSource() {
    var fx = fixture();
    when(fx.bloggers.enabled()).thenReturn(List.of(
        blogger("Alpha", "kol-1", List.of("CIA Feed")),
        blogger("Beta", "kol-beta", List.of())));
    when(fx.ai.extractionEnabled()).thenReturn(false);
    when(fx.messages.findById("msg-expired")).thenReturn(java.util.Optional.of(
        messageWithSummary("msg-expired", "[feed] Beta: NVDA BUY NOW")));
    when(fx.sessions.create(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(session("session-beta", "kol-beta"));
    when(fx.writer.write(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(new WriteResult("session-beta", 1));

    fx.service.retry("msg-expired");

    verify(fx.messages).reassign("msg-expired", "kol-beta", "Beta");
    verify(fx.writer).write(
        eq("session-beta"), eq("kol-beta"), contains("WxPusher / Beta / "),
        eq("2026-05-31"), eq("WXPUSHER_KEYWORD_FALLBACK"),
        eq("title\n[feed] Beta: NVDA BUY NOW\nhttps://source"), anyList());
  }

  @Test
  void createsNewSessionWhenReassignedMessageHadOldKolSession() throws Exception {
    var fx = fixture();
    when(fx.settings.get()).thenReturn(settings());
    when(fx.bloggers.enabled()).thenReturn(List.of(
        blogger("Alpha", "kol-1", List.of("CIA Feed")),
        blogger("Beta", "kol-beta", List.of())));
    when(fx.ai.extractionEnabled()).thenReturn(false);
    when(fx.messages.findById("msg-retry")).thenReturn(java.util.Optional.of(
        messageWithSession("msg-retry", "old-session")));
    when(fx.sessions.findById("old-session")).thenReturn(java.util.Optional.of(
        new LiveSession("old-session", "kol-1", "2026-05-31", "old", "WXPUSHER_AUTO", "", "now")));
    when(fx.sessions.create(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(session("new-session", "kol-beta"));
    when(fx.articles.fetchText(anyString(), any())).thenReturn("*Beta\nNVDA BUY NOW");
    when(fx.writer.write(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(new WriteResult("new-session", 1));

    fx.service.retry("msg-retry");

    verify(fx.messages).reassign("msg-retry", "kol-beta", "Beta");
    verify(fx.messages).attachSession("msg-retry", "new-session");
    verify(fx.writer).write(
        eq("new-session"), eq("kol-beta"), contains("WxPusher / Beta / "),
        eq("2026-05-31"), eq("WXPUSHER_KEYWORD_FALLBACK"),
        eq("*Beta\nNVDA BUY NOW"), anyList());
  }

  @Test
  void retryUsesStoredDetailTextBeforeFetchingRemoteDetail() {
    var fx = fixture();
    when(fx.bloggers.enabled()).thenReturn(List.of(blogger("Alpha", "kol-1", List.of("Alpha"))));
    when(fx.ai.extractionEnabled()).thenReturn(false);
    when(fx.messages.findById("msg-stored")).thenReturn(java.util.Optional.of(
        messageWithDetail("msg-stored", "NVDA BUY NOW")));
    when(fx.sessions.create(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(session("session-stored", "kol-1"));
    when(fx.writer.write(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(new WriteResult("session-stored", 1));

    fx.service.retry("msg-stored");

    verify(fx.articles, never()).fetchText(anyString(), any());
    verify(fx.writer).write(
        eq("session-stored"), eq("kol-1"), contains("WxPusher / Alpha / "),
        eq("2026-05-31"), eq("WXPUSHER_KEYWORD_FALLBACK"), eq("NVDA BUY NOW"), anyList());
  }

  @Test
  void retryFallsBackWhenStoredDetailIsExpiredNotice() {
    var fx = fixture();
    when(fx.bloggers.enabled()).thenReturn(List.of(
        blogger("Alpha", "kol-1", List.of("CIA Feed")),
        blogger("Beta", "kol-beta", List.of())));
    when(fx.ai.extractionEnabled()).thenReturn(false);
    when(fx.messages.findById("msg-expired-stored")).thenReturn(java.util.Optional.of(
        messageWithDetailAndSummary(
            "msg-expired-stored",
            "\u9519\u8bef\u63d0\u793a\n\u6d88\u606f\u5185\u5bb9\u4e0d\u5b58\u5728\u6216\u8005\u5df2\u7ecf\u8fc7\u671f",
            "[feed] Beta: NVDA BUY NOW")));
    when(fx.sessions.create(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(session("session-beta", "kol-beta"));
    when(fx.writer.write(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(new WriteResult("session-beta", 1));

    fx.service.retry("msg-expired-stored");

    verify(fx.messages).reassign("msg-expired-stored", "kol-beta", "Beta");
    verify(fx.writer).write(
        eq("session-beta"), eq("kol-beta"), contains("WxPusher / Beta / "),
        eq("2026-05-31"), eq("WXPUSHER_KEYWORD_FALLBACK"),
        eq("title\n[feed] Beta: NVDA BUY NOW\nhttps://source"), anyList());
  }

  @Test
  void retryUsesFallbackTextBeforeFetchingRemoteDetail() {
    var fx = fixture();
    when(fx.bloggers.enabled()).thenReturn(List.of(blogger("Alpha", "kol-1", List.of("Alpha"))));
    when(fx.ai.extractionEnabled()).thenReturn(false);
    when(fx.messages.findById("msg-fallback")).thenReturn(java.util.Optional.of(
        messageWithSummary("msg-fallback", "NVDA BUY NOW")));
    when(fx.sessions.create(anyString(), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(session("session-fallback", "kol-1"));
    when(fx.writer.write(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyList()))
        .thenReturn(new WriteResult("session-fallback", 1));

    fx.service.retry("msg-fallback");

    verify(fx.articles, never()).fetchText(anyString(), any());
    verify(fx.writer).write(
        eq("session-fallback"), eq("kol-1"), contains("WxPusher / Alpha / "),
        eq("2026-05-31"), eq("WXPUSHER_KEYWORD_FALLBACK"),
        eq("title\nNVDA BUY NOW\nhttps://source"), anyList());
  }

  @Test
  void skipsRetryWhenDetailShowsUnconfiguredNestedSource() throws Exception {
    var fx = fixture();
    when(fx.settings.get()).thenReturn(settings());
    when(fx.bloggers.enabled()).thenReturn(List.of(blogger("Alpha", "kol-1", List.of("CIA Feed"))));
    when(fx.ai.extractionEnabled()).thenReturn(false);
    when(fx.messages.findById("msg-gold")).thenReturn(java.util.Optional.of(message("msg-gold")));
    when(fx.articles.fetchText(anyString(), any())).thenReturn("*GoldenEmpire\nGOLD SELL NOW @ 4081");

    fx.service.retry("msg-gold");

    verify(fx.messages).markSkipped(eq("msg-gold"), contains("GoldenEmpire"), contains("KOL"));
    verify(fx.writer, never()).write(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyList());
  }

  @Test
  void skipsPremiumSignalsEvenWhenOuterFeedAliasWouldMatch() {
    var fx = fixture();
    when(fx.bloggers.enabled()).thenReturn(List.of(
        blogger("Shun", "kol-shun", List.of("CIA-\u4fe1\u606f\u63a8\u9001", "Shun"))));
    when(fx.ai.extractionEnabled()).thenReturn(false);
    when(fx.messages.findById("msg-premium")).thenReturn(java.util.Optional.of(
        messageWithDetailAndSummary(
            "msg-premium",
            "\u60a8\u8ba2\u9605\u7684\u3010CIA-\u4fe1\u606f\u63a8\u9001\u3011\u6709\u65b0\u7684\u6d88\u606f\n"
                + "[\u2728\u9ec4\u91d1\u5e1d\u56fd-PREMIUM-CIRCLE-\u2b55] PREMIUM SIGNALS: [Photo]",
            "[\u2728\u9ec4\u91d1\u5e1d\u56fd-PREMIUM-CIRCLE-\u2b55] PREMIUM SIGNALS: [Photo]")));

    fx.service.retry("msg-premium");

    verify(fx.messages).markSkipped(eq("msg-premium"), contains("PREMIUM SIGNALS"), contains("KOL"));
    verify(fx.writer, never()).write(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyList());
  }

  private Fixture fixture() {
    var sessions = mock(SessionRepository.class);
    var settings = mock(WxPusherSettingsRepository.class);
    var bloggers = mock(WxPusherBloggerRepository.class);
    var messages = mock(WxPusherMessageRepository.class);
    var shared = mock(WxPusherSharedMessageRepository.class);
    var articles = mock(WxPusherArticleExtractor.class);
    var ai = mock(OpenAiJsonExtractor.class);
    var parser = mock(JsonOpinionParser.class);
    var writer = mock(OpinionImportWriter.class);
    var instruments = mock(InstrumentRepository.class);
    var service = new WxPusherIngestionService(
        sessions, settings, bloggers, messages, shared, articles, ai, parser, writer, instruments);
    return new Fixture(service, sessions, settings, bloggers, messages, articles, ai, writer);
  }

  private WxPusherSettings settings() {
    return new WxPusherSettings(
        "default", "device-token", "", "", "Chrome-Windows", "1.1.1", 60, true, false, "", "", "", "", "");
  }

  private WxPusherBlogger blogger(String name, String kolId, List<String> aliases) {
    return new WxPusherBlogger("blogger-" + name, kolId, name, aliases, true, "LAST_30", null, "", "");
  }

  private LiveSession session(String id, String kolId) {
    return new LiveSession(id, kolId, "2026-05-31", "title", "WXPUSHER_AUTO", "", "now");
  }

  private WxPusherClient.IncomingMessage incoming(String bloggerName) {
    return incomingWithSummary(bloggerName, "summary");
  }

  private WxPusherClient.IncomingMessage incomingWithSummary(String bloggerName, String summary) {
    return new WxPusherClient.IncomingMessage(
        "polling", "wxpusher:src:https://source", bloggerName, "title", summary,
        "https://wxpusher.zjiecode.com/api/message/1", "https://source",
        "2026-05-31T06:00:00Z", "123", "{\"id\":1}", 1L);
  }

  private WxPusherMessage message(String id) {
    return new WxPusherMessage(
        id, "wxpusher:src:https://source", "kol-1", "Alpha", "title", "summary",
        "https://wxpusher.zjiecode.com/api/message/1", "https://source",
        "2026-05-31T06:00:00Z", "{\"id\":1}", "", "", "PENDING", "", "", "", "");
  }

  private WxPusherMessage messageWithSession(String id, String sessionId) {
    return new WxPusherMessage(
        id, "wxpusher:src:https://source", "kol-1", "Alpha", "title", "summary",
        "https://wxpusher.zjiecode.com/api/message/1", "https://source",
        "2026-05-31T06:00:00Z", "{\"id\":1}", "*Beta\nNVDA BUY NOW", "", "SKIPPED", "", sessionId, "", "");
  }

  private WxPusherMessage messageWithDetail(String id, String detailText) {
    return new WxPusherMessage(
        id, "wxpusher:src:https://source", "kol-1", "Alpha", "title", "summary",
        "https://wxpusher.zjiecode.com/api/message/1", "https://source",
        "2026-05-31T06:00:00Z", "{\"id\":1}", detailText, "", "SKIPPED", "", "", "", "");
  }

  private WxPusherMessage messageWithDetailAndSummary(String id, String detailText, String summary) {
    return new WxPusherMessage(
        id, "wxpusher:src:https://source", "kol-1", "Alpha", "title", summary,
        "https://wxpusher.zjiecode.com/api/message/1", "https://source",
        "2026-05-31T06:00:00Z", "{\"id\":1}", detailText, "", "SKIPPED", "", "", "", "");
  }

  private WxPusherMessage messageWithSummary(String id, String summary) {
    return new WxPusherMessage(
        id, "wxpusher:src:https://source", "kol-1", "Alpha", "title", summary,
        "https://wxpusher.zjiecode.com/api/message/1", "https://source",
        "2026-05-31T06:00:00Z", "{\"id\":1}", "", "", "SKIPPED", "", "", "", "");
  }

  private record Fixture(
      WxPusherIngestionService service,
      SessionRepository sessions,
      WxPusherSettingsRepository settings,
      WxPusherBloggerRepository bloggers,
      WxPusherMessageRepository messages,
      WxPusherArticleExtractor articles,
      OpenAiJsonExtractor ai,
      OpinionImportWriter writer) {
  }
}
