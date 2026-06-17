package com.personal.tracker.service.youtube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class YouTubeAudioDownloaderTest {
  @Test
  void parseCommandSupportsQuotedWindowsPath() {
    assertEquals(
        List.of("C:\\Program Files\\yt-dlp.exe", "--proxy", "http://127.0.0.1:7890"),
        YouTubeAudioDownloader.parseCommand(
            "\"C:\\Program Files\\yt-dlp.exe\" --proxy http://127.0.0.1:7890"));
  }

  @Test
  void commandCandidatesFallbackToExecutableAndPythonModule() {
    List<List<String>> candidates = YouTubeAudioDownloader.commandCandidates("");

    assertEquals(List.of("yt-dlp"), candidates.get(0));
    assertTrue(candidates.contains(List.of("python", "-m", "yt_dlp")));
  }

  @Test
  void commandCandidatesRespectConfiguredCommand() {
    assertEquals(
        List.of(List.of("python", "-m", "yt_dlp")),
        YouTubeAudioDownloader.commandCandidates("python -m yt_dlp"));
  }

  @Test
  void detectsRangeNotSatisfiableFromYtDlpMessage() {
    assertTrue(YouTubeAudioDownloader.isRangeNotSatisfiable(
        "yt-dlp 执行失败: ERROR: unable to download video data: HTTP Error 416: Requested range not satisfiable"));
  }

  @Test
  void existingIgnoresPartFiles() throws Exception {
    Path root = Files.createTempDirectory("yt-audio-test");
    Files.writeString(root.resolve("abc123.m4a.part"), "partial");
    var downloader = new YouTubeAudioDownloader(new ObjectMapper(), root, "python -m yt_dlp");

    assertNull(downloader.existing("abc123"));
  }

  @Test
  void existingReturnsFinishedAudioFile() throws Exception {
    Path root = Files.createTempDirectory("yt-audio-test");
    Path target = root.resolve("abc123.m4a");
    Files.writeString(target, "done");
    var downloader = new YouTubeAudioDownloader(new ObjectMapper(), root, "python -m yt_dlp");

    assertEquals(target, downloader.existing("abc123"));
  }
}
