package com.personal.tracker.service.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.personal.tracker.repository.youtube.YouTubeRepository.TranscriptSegment;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class AliyunSpeechTranscriber {
  private static final Set<String> DIRECT_UPLOAD_EXTENSIONS =
      Set.of(
          ".aac", ".amr", ".avi", ".flac", ".flv", ".m4a", ".mkv", ".mov",
          ".mp3", ".mp4", ".mpeg", ".ogg", ".opus", ".wav", ".webm", ".wma", ".wmv");
  private final AliyunOssClient ossClient;
  private final AliyunFileTransClient fileTransClient;
  private final String ffmpegCommand;

  public AliyunSpeechTranscriber(
      AliyunOssClient ossClient,
      AliyunFileTransClient fileTransClient,
      Environment environment) {
    this.ossClient = ossClient;
    this.fileTransClient = fileTransClient;
    String value = environment.getProperty("YOUTUBE_FFMPEG_COMMAND");
    this.ffmpegCommand = value == null || value.isBlank() ? "ffmpeg" : value.trim();
  }

  public TranscriptResult transcribe(String audioPath) {
    Path normalized = normalize(Path.of(audioPath));
    var upload = ossClient.upload(normalized.toString());
    JsonNode payload = fileTransClient.transcribe(upload.fileLink());
    List<TranscriptSegment> segments = mergeParagraphs(payload.path("Sentences"));
    if (segments.isEmpty()) {
      segments = mergeParagraphs(payload.path("sentences"));
    }
    if (segments.isEmpty()) {
      throw new IllegalArgumentException("阿里云录音文件识别没有返回有效句子");
    }
    String transcriptText = segments.stream()
        .map(TranscriptSegment::text)
        .filter(text -> text != null && !text.isBlank())
        .reduce((left, right) -> left + "\n\n" + right)
        .orElse("");
    return new TranscriptResult("ready", "zh", "aliyun_filetrans", transcriptText, segments, "");
  }

  static List<TranscriptSegment> mergeParagraphs(JsonNode sentences) {
    List<TranscriptSegment> segments = new ArrayList<>();
    TranscriptSegment current = null;
    if (sentences == null || !sentences.isArray()) {
      return segments;
    }
    for (JsonNode item : sentences) {
      String text = collapseSpaces(value(item, "Text", "text"));
      if (text.isBlank()) {
        continue;
      }
      int startMs = intValue(item, 0, "BeginTime", "begin_time");
      int endMs = intValue(item, startMs, "EndTime", "end_time");
      if (current != null && startMs - current.endMs() < 2500 && current.text().length() < 140) {
        current = new TranscriptSegment(current.startMs(), endMs, (current.text() + " " + text).trim());
        segments.set(segments.size() - 1, current);
        continue;
      }
      current = new TranscriptSegment(startMs, endMs, text);
      segments.add(current);
    }
    return segments;
  }

  static boolean supportsDirectUpload(Path source) {
    String name = source.getFileName() == null ? "" : source.getFileName().toString().toLowerCase(Locale.ROOT);
    return DIRECT_UPLOAD_EXTENSIONS.stream().anyMatch(name::endsWith);
  }

  private Path normalize(Path source) {
    try {
      if (supportsDirectUpload(source) && Files.size(source) > 0) {
        return source;
      }
      if (source.getFileName().toString().endsWith(".aliyun.wav") && Files.size(source) > 44) {
        return source;
      }
      Path target = source.resolveSibling(source.getFileName().toString().replaceFirst("\\.[^.]+$", "") + ".aliyun.wav");
      if (Files.exists(target) && Files.size(target) > 44) {
        return target;
      }
      run(List.of(
          "-y",
          "-i",
          source.toString(),
          "-ac",
          "1",
          "-ar",
          "16000",
          target.toString()));
      return target;
    } catch (IOException error) {
      throw new IllegalArgumentException("准备阿里云转写音频失败: " + error.getMessage(), error);
    }
  }

  private void run(List<String> arguments) {
    List<String> commandLine = new ArrayList<>(YouTubeAudioDownloader.parseCommand(ffmpegCommand));
    commandLine.addAll(arguments);
    try {
      ProcessBuilder builder = new ProcessBuilder(commandLine);
      builder.redirectErrorStream(true);
      Process process = builder.start();
      String output = readAll(process.getInputStream());
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        throw new IllegalArgumentException("ffmpeg 转码失败: " + trimOutput(output));
      }
    } catch (IllegalArgumentException error) {
      throw error;
    } catch (IOException error) {
      if (isMissingCommand(error.getMessage())) {
        throw missingCommand(error);
      }
      throw new IllegalArgumentException("调用 ffmpeg 失败: " + error.getMessage(), error);
    } catch (Exception error) {
      throw new IllegalArgumentException("调用 ffmpeg 失败: " + error.getMessage(), error);
    }
  }

  private IllegalArgumentException missingCommand(IOException error) {
    StringBuilder hint = new StringBuilder("未找到可用的 ffmpeg。");
    hint.append(" 当前文件格式不在阿里云直接支持列表内时才需要 ffmpeg。");
    hint.append(" 如果线上出现这类格式，请安装 ffmpeg 并加入 PATH，");
    hint.append("或把 YOUTUBE_FFMPEG_COMMAND 指到 ffmpeg 可执行文件。");
    hint.append(" 细节: ").append(error.getMessage());
    return new IllegalArgumentException(hint.toString(), error);
  }

  private static boolean isMissingCommand(String message) {
    if (message == null || message.isBlank()) {
      return false;
    }
    return message.contains("CreateProcess error=2")
        || message.contains("Cannot run program")
        || message.contains("No such file or directory");
  }

  private static String value(JsonNode node, String... keys) {
    for (String key : keys) {
      JsonNode value = node.path(key);
      if (!value.isMissingNode() && !value.isNull() && !value.asText("").isBlank()) {
        return value.asText("");
      }
    }
    return "";
  }

  private static int intValue(JsonNode node, int fallback, String... keys) {
    String value = value(node, keys);
    if (value.isBlank()) {
      return fallback;
    }
    try {
      return (int) Math.round(Double.parseDouble(value));
    } catch (NumberFormatException error) {
      return fallback;
    }
  }

  private static String collapseSpaces(String text) {
    return text == null ? "" : text.trim().replaceAll("\\s+", " ");
  }

  private static String readAll(InputStream stream) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    stream.transferTo(output);
    return output.toString(StandardCharsets.UTF_8);
  }

  private static String trimOutput(String output) {
    String text = output == null ? "" : output.trim();
    return text.isBlank() ? "没有返回更多信息" : text;
  }

  public record TranscriptResult(
      String transcriptStatus,
      String transcriptLanguage,
      String transcriptSource,
      String transcriptText,
      List<TranscriptSegment> transcriptSegments,
      String errorMessage) {
  }
}
