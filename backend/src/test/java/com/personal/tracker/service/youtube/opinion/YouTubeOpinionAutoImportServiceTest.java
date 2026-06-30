package com.personal.tracker.service.youtube.opinion;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.personal.tracker.config.LlmProperties;
import com.personal.tracker.domain.Kol;
import com.personal.tracker.repository.KolRepository;
import com.personal.tracker.repository.youtube.YouTubeOpinionImportRepository;
import com.personal.tracker.repository.youtube.YouTubeOpinionImportRepository.ImportState;
import com.personal.tracker.repository.youtube.YouTubeRepository.ChannelRecord;
import com.personal.tracker.repository.youtube.YouTubeRepository.VideoRecord;
import com.personal.tracker.service.ImportService.ImportCandidate;
import com.personal.tracker.service.ImportService.ImportPreview;
import com.personal.tracker.service.imports.OpinionImportWriter;
import com.personal.tracker.service.imports.OpinionImportWriter.WriteResult;
import com.personal.tracker.service.json.JsonOpinionParser;
import com.personal.tracker.service.llm.OpenAiCompatibleChatService;
import com.personal.tracker.service.youtube.YouTubeAdminService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class YouTubeOpinionAutoImportServiceTest {
  @Test
  void importsReadyTranscriptIntoOpinions() throws Exception {
    var properties = mock(LlmProperties.class);
    var kols = mock(KolRepository.class);
    var parser = mock(JsonOpinionParser.class);
    var writer = mock(OpinionImportWriter.class);
    var chat = mock(OpenAiCompatibleChatService.class);
    var imports = mock(YouTubeOpinionImportRepository.class);
    var service = new YouTubeOpinionAutoImportService(
        properties,
        kols,
        parser,
        writer,
        chat,
        imports,
        new MockEnvironment());
    when(properties.youtubeAutoImportEnabled()).thenReturn(true);
    when(imports.find("vid-1")).thenReturn(Optional.empty());
    when(chat.chat(anyString(), anyString(), contains("转写正文"))).thenReturn("{\"ok\":true}");
    when(parser.parse("{\"ok\":true}")).thenReturn(preview());
    when(kols.save("Channel", "YouTube 频道")).thenReturn(new Kol("kol-1", "Channel", "YouTube 频道", "now"));
    when(writer.write(eq("kol-1"), contains("YouTube / Channel / Video"), eq("2026-06-12"), eq("YOUTUBE_AUTO"), contains("英伟达"), eq(preview().candidates())))
        .thenReturn(new WriteResult("session-1", 1));

    service.importIfReady(channel(), video("英伟达继续强，关注 NVDA"));

    verify(imports).markProcessing("vid-1");
    verify(writer).write(eq("kol-1"), contains("YouTube / Channel / Video"), eq("2026-06-12"), eq("YOUTUBE_AUTO"), contains("英伟达"), eq(preview().candidates()));
    verify(imports).markImported("vid-1", "session-1", "{\"ok\":true}");
  }

  @Test
  void skipsWhenAlreadyImported() {
    var properties = mock(LlmProperties.class);
    var service = new YouTubeOpinionAutoImportService(
        properties,
        mock(KolRepository.class),
        mock(JsonOpinionParser.class),
        mock(OpinionImportWriter.class),
        mock(OpenAiCompatibleChatService.class),
        importedRepo(),
        new MockEnvironment());
    when(properties.youtubeAutoImportEnabled()).thenReturn(true);

    service.importIfReady(channel(), video("任意文本"));

    verify(properties).youtubeAutoImportEnabled();
  }

  @Test
  void marksEmptyWhenNoCandidates() throws Exception {
    var properties = mock(LlmProperties.class);
    var parser = mock(JsonOpinionParser.class);
    var chat = mock(OpenAiCompatibleChatService.class);
    var imports = mock(YouTubeOpinionImportRepository.class);
    var service = new YouTubeOpinionAutoImportService(
        properties,
        mock(KolRepository.class),
        parser,
        mock(OpinionImportWriter.class),
        chat,
        imports,
        new MockEnvironment());
    when(properties.youtubeAutoImportEnabled()).thenReturn(true);
    when(imports.find("vid-1")).thenReturn(Optional.empty());
    when(chat.chat(anyString(), anyString(), contains("转写正文"))).thenReturn("{\"empty\":true}");
    when(parser.parse("{\"empty\":true}")).thenReturn(new ImportPreview(List.of(), List.of(), List.of(), List.of()));

    service.importIfReady(channel(), video("今天主要聊宏观，没有明确交易标的"));

    verify(imports).markProcessing("vid-1");
    verify(imports).markEmpty("vid-1", "{\"empty\":true}", "模型没有提取到可入库观点");
  }

  @Test
  void ignoresWhenTranscriptNotReady() {
    var properties = mock(LlmProperties.class);
    var imports = mock(YouTubeOpinionImportRepository.class);
    var service = new YouTubeOpinionAutoImportService(
        properties,
        mock(KolRepository.class),
        mock(JsonOpinionParser.class),
        mock(OpinionImportWriter.class),
        mock(OpenAiCompatibleChatService.class),
        imports,
        new MockEnvironment());
    when(properties.youtubeAutoImportEnabled()).thenReturn(true);
    VideoRecord video = new VideoRecord(
        "vid-1",
        "channel-row",
        "channel-id",
        "Video",
        "https://example.com/watch?v=1",
        "2026-06-12T08:00:00Z",
        "",
        0,
        YouTubeAdminService.TRANSCRIPT_ERROR,
        "",
        "",
        "",
        List.of(),
        "error",
        "",
        "",
        "");

    service.importIfReady(channel(), video);

    verify(imports, never()).markProcessing(anyString());
  }

  private YouTubeOpinionImportRepository importedRepo() {
    var imports = mock(YouTubeOpinionImportRepository.class);
    when(imports.find("vid-1")).thenReturn(Optional.of(new ImportState(
        "vid-1", "IMPORTED", "session-1", "{}", "", "now", "created", "updated")));
    return imports;
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
        "2026-06-12T08:00:00Z",
        "created",
        "updated");
  }

  private VideoRecord video(String transcriptText) {
    return new VideoRecord(
        "vid-1",
        "channel-row",
        "channel-id",
        "Video",
        "https://example.com/watch?v=1",
        "2026-06-12T08:00:00Z",
        "",
        0,
        YouTubeAdminService.TRANSCRIPT_READY,
        "zh",
        "aliyun_filetrans",
        transcriptText,
        List.of(),
        "",
        "2026-06-12T08:05:00Z",
        "created",
        "updated");
  }

  private ImportPreview preview() {
    return new ImportPreview(List.of(), List.of(), List.of(candidate()), List.of());
  }

  private ImportCandidate candidate() {
    return new ImportCandidate(
        true,
        "NVDA",
        "英伟达",
        "US",
        "BULLISH",
        "看多",
        "OPEN",
        "中线",
        "AI 需求继续强化",
        "",
        "",
        "",
        "",
        "英伟达继续强",
        "{\"代码\":\"NVDA\"}",
        List.of());
  }
}
