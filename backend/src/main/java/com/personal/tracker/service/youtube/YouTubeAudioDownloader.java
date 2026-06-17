package com.personal.tracker.service.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class YouTubeAudioDownloader {
  private static final String AUDIO_FORMAT =
      "bestaudio[ext=m4a]/bestaudio[ext=mp4]/bestaudio[ext=mp3]/bestaudio";
  private final ObjectMapper objectMapper;
  private final Path root;
  private final String command;

  @Autowired
  public YouTubeAudioDownloader(ObjectMapper objectMapper, Environment environment) {
    this(
        objectMapper,
        Paths.get(env(environment, "YOUTUBE_AUDIO_DIR", "data/youtube_audio"))
            .toAbsolutePath()
            .normalize(),
        env(environment, "YOUTUBE_YT_DLP_COMMAND", ""));
  }

  YouTubeAudioDownloader(ObjectMapper objectMapper, Path root, String command) {
    this.objectMapper = objectMapper;
    this.root = root;
    this.command = command;
  }

  public AudioDownload download(String videoId, String videoUrl) {
    try {
      Files.createDirectories(root);
      Path existing = existing(videoId);
      if (existing != null) {
        return new AudioDownload(existing.toString(), 0);
      }
      long durationMs = fetchDuration(videoUrl);
      runYtDlp(videoId, videoUrl);
      Path downloaded = existing(videoId);
      if (downloaded == null) {
        Path partial = partial(videoId);
        if (partial != null) {
          throw new IllegalArgumentException("yt-dlp 下载后只留下临时文件，音频尚未完成: " + partial);
        }
        throw new IllegalArgumentException("音频下载完成，但没有找到输出文件");
      }
      return new AudioDownload(downloaded.toString(), durationMs);
    } catch (IOException error) {
      throw new IllegalArgumentException("准备音频目录失败: " + error.getMessage(), error);
    }
  }

  private void runYtDlp(String videoId, String videoUrl) throws IOException {
    List<String> arguments = List.of(
        "--no-playlist",
        "--quiet",
        "--no-warnings",
        "-f",
        AUDIO_FORMAT,
        "-o",
        root.resolve(videoId + ".%(ext)s").toString(),
        videoUrl);
    try {
      runYtDlp(arguments);
    } catch (IllegalArgumentException error) {
      Path partial = partial(videoId);
      if (partial != null && isRangeNotSatisfiable(error.getMessage())) {
        Files.deleteIfExists(partial);
        runYtDlp(arguments);
        return;
      }
      throw error;
    }
  }

  private long fetchDuration(String videoUrl) {
    try {
      ProcessResult result = runYtDlp(List.of("--dump-single-json", "--no-playlist", videoUrl));
      JsonNode rootNode = objectMapper.readTree(result.stdout());
      return Math.max(0, Math.round(rootNode.path("duration").asDouble(0) * 1000));
    } catch (Exception error) {
      return 0;
    }
  }

  Path existing(String videoId) throws IOException {
    try (Stream<Path> stream = Files.list(root)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().startsWith(videoId + "."))
          .filter(path -> !path.getFileName().toString().contains(".aliyun."))
          .filter(path -> !isPartial(path))
          .sorted(Comparator.comparing(Path::toString))
          .findFirst()
          .orElse(null);
    }
  }

  private Path partial(String videoId) throws IOException {
    try (Stream<Path> stream = Files.list(root)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().startsWith(videoId + "."))
          .filter(this::isPartial)
          .sorted(Comparator.comparing(Path::toString))
          .findFirst()
          .orElse(null);
    }
  }

  private boolean isPartial(Path path) {
    return path.getFileName().toString().endsWith(".part");
  }

  private ProcessResult runYtDlp(List<String> arguments) {
    IllegalArgumentException lastError = null;
    for (List<String> candidate : commandCandidates(command)) {
      List<String> commandLine = new ArrayList<>(candidate);
      commandLine.addAll(arguments);
      try {
        return run(commandLine);
      } catch (MissingCommandException error) {
        lastError = missingCommand(error.getMessage());
      } catch (IllegalArgumentException error) {
        if (isMissingPythonModule(error.getMessage())) {
          lastError = missingCommand(error.getMessage());
          continue;
        }
        throw error;
      }
    }
    throw lastError == null ? missingCommand("未找到可用的 yt-dlp 命令") : lastError;
  }

  private ProcessResult run(List<String> commandLine) {
    try {
      ProcessBuilder builder = new ProcessBuilder(new ArrayList<>(commandLine));
      builder.redirectErrorStream(true);
      Process process = builder.start();
      String output = readAll(process.getInputStream());
      int exitCode = process.waitFor();
      if (exitCode != 0) {
        throw new IllegalArgumentException("yt-dlp 执行失败: " + trimOutput(output));
      }
      return new ProcessResult(exitCode, output);
    } catch (IllegalArgumentException error) {
      throw error;
    } catch (IOException error) {
      if (isMissingCommand(error.getMessage())) {
        throw new MissingCommandException(commandLine.get(0), error);
      }
      throw new IllegalArgumentException("调用 yt-dlp 失败: " + error.getMessage(), error);
    } catch (Exception error) {
      throw new IllegalArgumentException("调用 yt-dlp 失败: " + error.getMessage(), error);
    }
  }

  static List<List<String>> commandCandidates(String configuredCommand) {
    if (configuredCommand != null && !configuredCommand.isBlank()) {
      return List.of(parseCommand(configuredCommand));
    }
    return List.of(
        List.of("yt-dlp"),
        List.of("python", "-m", "yt_dlp"));
  }

  static List<String> parseCommand(String raw) {
    List<String> parts = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean quoted = false;
    for (int index = 0; index < raw.length(); index++) {
      char ch = raw.charAt(index);
      if (ch == '"') {
        quoted = !quoted;
        continue;
      }
      if (!quoted && Character.isWhitespace(ch)) {
        if (current.length() > 0) {
          parts.add(current.toString());
          current.setLength(0);
        }
        continue;
      }
      current.append(ch);
    }
    if (current.length() > 0) {
      parts.add(current.toString());
    }
    if (parts.isEmpty()) {
      throw new IllegalArgumentException("YOUTUBE_YT_DLP_COMMAND 不能为空");
    }
    return List.copyOf(parts);
  }

  private IllegalArgumentException missingCommand(String detail) {
    StringBuilder hint = new StringBuilder("未找到可用的 yt-dlp。");
    hint.append(" 请先执行 `python -m pip install yt-dlp`，");
    hint.append("或安装 `yt-dlp.exe` 并加入 `PATH`，");
    hint.append("或设置环境变量 `YOUTUBE_YT_DLP_COMMAND`。");
    if (detail != null && !detail.isBlank()) {
      hint.append(" 细节: ").append(detail);
    }
    return new IllegalArgumentException(hint.toString());
  }

  private static boolean isMissingCommand(String message) {
    if (message == null || message.isBlank()) {
      return false;
    }
    return message.contains("CreateProcess error=2")
        || message.contains("Cannot run program")
        || message.contains("No such file or directory");
  }

  private static boolean isMissingPythonModule(String message) {
    return message != null && message.contains("No module named yt_dlp");
  }

  static boolean isRangeNotSatisfiable(String message) {
    return message != null && message.contains("HTTP Error 416");
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

  private static String env(Environment environment, String key, String fallback) {
    String value = environment.getProperty(key);
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  public record AudioDownload(String audioPath, long audioDurationMs) {
  }

  private record ProcessResult(int exitCode, String stdout) {
  }

  private static final class MissingCommandException extends RuntimeException {
    private MissingCommandException(String commandName, Throwable cause) {
      super("命令不存在: " + commandName, cause);
    }
  }
}
