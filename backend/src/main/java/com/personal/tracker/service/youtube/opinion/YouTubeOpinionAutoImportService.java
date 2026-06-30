package com.personal.tracker.service.youtube.opinion;

import com.personal.tracker.config.LlmProperties;
import com.personal.tracker.repository.KolRepository;
import com.personal.tracker.repository.youtube.YouTubeOpinionImportRepository;
import com.personal.tracker.repository.youtube.YouTubeOpinionImportRepository.ImportState;
import com.personal.tracker.repository.youtube.YouTubeRepository.ChannelRecord;
import com.personal.tracker.repository.youtube.YouTubeRepository.VideoRecord;
import com.personal.tracker.service.imports.OpinionImportWriter;
import com.personal.tracker.service.json.JsonOpinionParser;
import com.personal.tracker.service.llm.OpenAiCompatibleChatService;
import com.personal.tracker.service.youtube.YouTubeAdminService;
import com.personal.tracker.service.youtube.YouTubeVideoSupport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class YouTubeOpinionAutoImportService {
  private static final String SKILL_FILE_PROPERTY = "MARKET_OPINION_JSON_SKILL_FILE";
  private static final String DEFAULT_SKILL_FILE = "skills/market-opinion-json/SKILL.md";
  private static final String ALT_SKILL_FILE = "../skills/market-opinion-json/SKILL.md";
  private static final String EMPTY_RESULT = "模型没有提取到可入库观点";
  private static final String IMPORT_SCENE = "YOUTUBE_AUTO_IMPORT";
  private static final String FALLBACK_PROMPT = """
      你是市场观点结构化助手。你的任务是把直播文字、口播转写、聊天记录或自由格式摘要整理成系统可直接导入的 JSON。
      只输出合法 JSON，不要输出 Markdown、解释、注释或代码块。
      输出结构：
      {
        "总体摘要": {
          "大盘与风格": "",
          "主线": "",
          "风险提示": ""
        },
        "按具体品种划分": [
          {
            "品种": "",
            "代码": "",
            "市场": "",
            "方向": "",
            "周期": "",
            "关键判断": "",
            "催化": [],
            "触发条件": "",
            "风险": [],
            "关键价位": [],
            "原文摘录": ""
          }
        ],
        "待确认映射": {}
      }
      不要新增系统外字段。
      """;
  private final LlmProperties properties;
  private final KolRepository kols;
  private final JsonOpinionParser parser;
  private final OpinionImportWriter writer;
  private final OpenAiCompatibleChatService chatService;
  private final YouTubeOpinionImportRepository imports;
  private final String skillPrompt;

  public YouTubeOpinionAutoImportService(
      LlmProperties properties,
      KolRepository kols,
      JsonOpinionParser parser,
      OpinionImportWriter writer,
      OpenAiCompatibleChatService chatService,
      YouTubeOpinionImportRepository imports,
      Environment environment) {
    this.properties = properties;
    this.kols = kols;
    this.parser = parser;
    this.writer = writer;
    this.chatService = chatService;
    this.imports = imports;
    this.skillPrompt = loadSkillPrompt(environment.getProperty(SKILL_FILE_PROPERTY, ""));
  }

  public void importIfReady(ChannelRecord channel, VideoRecord video) {
    if (!properties.youtubeAutoImportEnabled()
        || video == null
        || !YouTubeAdminService.TRANSCRIPT_READY.equals(video.transcriptStatus())) {
      return;
    }
    if (alreadyImported(video.videoId())) {
      return;
    }
    String transcript = YouTubeVideoSupport.transcriptText(video);
    if (transcript.isBlank()) {
      return;
    }
    imports.markProcessing(video.videoId());
    String llmJson = "";
    try {
      llmJson = stripCodeFence(chatService.chat(IMPORT_SCENE, skillPrompt, buildPrompt(channel, video, transcript)));
      var preview = parser.parse(llmJson);
      if (preview.candidates().isEmpty()) {
        imports.markEmpty(video.videoId(), llmJson, EMPTY_RESULT);
        return;
      }
      String kolId = kols.save(channelName(channel), "YouTube 频道").id();
      var result = writer.write(
          kolId,
          sessionTitle(channel, video),
          sessionDate(video.publishedAt()),
          "YOUTUBE_AUTO",
          transcript,
          preview.candidates());
      imports.markImported(video.videoId(), result.sessionId(), llmJson);
    } catch (Exception error) {
      imports.markFailed(video.videoId(), llmJson, error.getMessage());
    }
  }

  private boolean alreadyImported(String videoId) {
    ImportState state = imports.find(videoId).orElse(null);
    return state != null
        && ("IMPORTED".equalsIgnoreCase(state.status())
        || "EMPTY".equalsIgnoreCase(state.status()));
  }

  private String buildPrompt(ChannelRecord channel, VideoRecord video, String transcript) {
    return """
        来源：YouTube 转写
        频道：%s
        视频标题：%s
        发布时间：%s
        视频链接：%s
        转写正文：
        %s
        """.formatted(
        channelName(channel),
        blank(video.title()),
        blank(video.publishedAt()),
        blank(video.videoUrl()),
        transcript.trim());
  }

  private String loadSkillPrompt(String configuredPath) {
    for (String candidate : new String[] { configuredPath, DEFAULT_SKILL_FILE, ALT_SKILL_FILE }) {
      String content = readPrompt(candidate);
      if (!content.isBlank()) {
        return content;
      }
    }
    return FALLBACK_PROMPT;
  }

  private String readPrompt(String rawPath) {
    if (rawPath == null || rawPath.isBlank()) {
      return "";
    }
    try {
      Path path = Path.of(rawPath).toAbsolutePath().normalize();
      return Files.exists(path) ? Files.readString(path) : "";
    } catch (IOException | RuntimeException error) {
      return "";
    }
  }

  private String stripCodeFence(String text) {
    String trimmed = text == null ? "" : text.trim();
    if (!trimmed.startsWith("```")) {
      return trimmed;
    }
    return trimmed.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
  }

  private String sessionTitle(ChannelRecord channel, VideoRecord video) {
    return "YouTube / " + channelName(channel) + " / " + blank(video.title());
  }

  private String channelName(ChannelRecord channel) {
    if (channel == null) {
      return "YouTube";
    }
    if (channel.title() != null && !channel.title().isBlank()) {
      return channel.title().trim();
    }
    return blank(channel.channelId());
  }

  private String sessionDate(String publishedAt) {
    if (publishedAt != null && publishedAt.length() >= 10) {
      return publishedAt.substring(0, 10);
    }
    return LocalDate.now().toString();
  }

  private String blank(String value) {
    return value == null ? "" : value.trim();
  }
}
