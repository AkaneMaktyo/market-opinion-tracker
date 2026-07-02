package com.personal.tracker.repository.youtube;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class YouTubeRepository {
  private static final TypeReference<List<TranscriptSegment>> SEGMENT_LIST = new TypeReference<>() {
  };
  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final RowMapper<ChannelRecord> channelMapper = (rs, rowNum) -> new ChannelRecord(
      rs.getString("id"),
      rs.getString("channel_id"),
      rs.getString("title"),
      rs.getString("handle"),
      rs.getString("source_url"),
      rs.getBoolean("enabled"),
      rs.getString("last_checked_at"),
      rs.getString("last_video_published_at"),
      rs.getString("created_at"),
      rs.getString("updated_at"));
  private final RowMapper<VideoRecord> videoMapper = (rs, rowNum) -> new VideoRecord(
      rs.getString("video_id"),
      rs.getString("channel_row_id"),
      rs.getString("channel_id"),
      rs.getString("title"),
      rs.getString("video_url"),
      rs.getString("published_at"),
      rs.getString("audio_path"),
      rs.getLong("audio_duration_ms"),
      rs.getString("transcript_status"),
      rs.getString("transcript_language"),
      rs.getString("transcript_source"),
      rs.getString("transcript_text"),
      readSegments(rs.getString("transcript_segments_json")),
      rs.getString("error_message"),
      rs.getString("notify_status"),
      rs.getString("notify_error"),
      rs.getString("notified_at"),
      rs.getString("read_at"),
      rs.getString("synced_at"),
      rs.getString("created_at"),
      rs.getString("updated_at"));

  public YouTubeRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public List<ChannelRecord> listChannels() {
    return jdbc.query("SELECT * FROM youtube_channels ORDER BY updated_at DESC", channelMapper);
  }

  public Optional<ChannelRecord> findChannel(String id) {
    return jdbc.query("SELECT * FROM youtube_channels WHERE id = ? LIMIT 1", channelMapper, id)
        .stream()
        .findFirst();
  }

  public Optional<ChannelRecord> findChannelByRemoteId(String channelId) {
    return jdbc.query("SELECT * FROM youtube_channels WHERE channel_id = ? LIMIT 1", channelMapper, channelId)
        .stream()
        .findFirst();
  }

  public ChannelRecord saveChannel(SaveChannelCommand command) {
    ChannelRecord existing = findChannelByRemoteId(command.channelId()).orElse(null);
    String id = existing == null ? "ytc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) : existing.id();
    String createdAt = existing == null ? command.updatedAt() : existing.createdAt();
    jdbc.update("""
        INSERT INTO youtube_channels(
          id, channel_id, title, handle, source_url, enabled,
          last_checked_at, last_video_published_at, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          title = VALUES(title),
          handle = VALUES(handle),
          source_url = VALUES(source_url),
          enabled = VALUES(enabled),
          last_checked_at = VALUES(last_checked_at),
          last_video_published_at = VALUES(last_video_published_at),
          updated_at = VALUES(updated_at)
        """,
        id,
        command.channelId(),
        value(command.title()),
        value(command.handle()),
        value(command.sourceUrl()),
        command.enabled(),
        value(command.lastCheckedAt()),
        value(command.lastVideoPublishedAt()),
        createdAt,
        command.updatedAt());
    return findChannel(id).orElseThrow();
  }

  public boolean deleteChannel(String channelRowId) {
    jdbc.update("DELETE FROM youtube_videos WHERE channel_row_id = ?", channelRowId);
    return jdbc.update("DELETE FROM youtube_channels WHERE id = ?", channelRowId) > 0;
  }

  public List<VideoRecord> listVideos(String channelRowId, int limit) {
    return jdbc.query("""
        SELECT * FROM youtube_videos
        WHERE channel_row_id = ?
        ORDER BY published_at DESC
        LIMIT ?
        """, videoMapper, channelRowId, Math.max(1, limit));
  }

  public Optional<VideoRecord> findVideo(String videoId) {
    return jdbc.query("SELECT * FROM youtube_videos WHERE video_id = ? LIMIT 1", videoMapper, videoId)
        .stream()
        .findFirst();
  }

  public List<VideoRecord> listByTranscriptStatus(String transcriptStatus, int limit) {
    return jdbc.query("""
        SELECT * FROM youtube_videos
        WHERE transcript_status = ?
        ORDER BY updated_at ASC
        LIMIT ?
        """, videoMapper, value(transcriptStatus), Math.max(1, limit));
  }

  public List<VideoRecord> listVideosPendingNotification(int limit) {
    return jdbc.query("""
        SELECT * FROM youtube_videos
        WHERE transcript_status = ?
          AND (
            notify_status = ''
            OR UPPER(notify_status) <> 'SENT'
            OR notified_at = ''
          )
        ORDER BY updated_at ASC
        LIMIT ?
        """, videoMapper, "ready", Math.max(1, limit));
  }

  public VideoRecord saveVideo(SaveVideoCommand command) {
    VideoRecord existing = findVideo(command.videoId()).orElse(null);
    jdbc.update("""
        INSERT INTO youtube_videos(
          video_id, channel_row_id, channel_id, title, video_url, published_at,
          audio_path, audio_duration_ms, transcript_status, transcript_language, transcript_source,
          transcript_text, transcript_segments_json, error_message, synced_at, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          channel_row_id = VALUES(channel_row_id),
          channel_id = VALUES(channel_id),
          title = VALUES(title),
          video_url = VALUES(video_url),
          published_at = VALUES(published_at),
          audio_path = VALUES(audio_path),
          audio_duration_ms = VALUES(audio_duration_ms),
          transcript_status = VALUES(transcript_status),
          transcript_language = VALUES(transcript_language),
          transcript_source = VALUES(transcript_source),
          transcript_text = VALUES(transcript_text),
          transcript_segments_json = VALUES(transcript_segments_json),
          error_message = VALUES(error_message),
          synced_at = VALUES(synced_at),
          updated_at = VALUES(updated_at)
        """,
        command.videoId(),
        command.channelRowId(),
        command.channelId(),
        value(command.title()),
        value(command.videoUrl()),
        value(command.publishedAt()),
        value(command.audioPath()),
        Math.max(0, command.audioDurationMs()),
        value(command.transcriptStatus()),
        value(command.transcriptLanguage()),
        value(command.transcriptSource()),
        value(command.transcriptText()),
        writeSegments(command.transcriptSegments()),
        value(command.errorMessage()),
        value(command.syncedAt()),
        existing == null ? command.updatedAt() : existing.createdAt(),
        command.updatedAt());
    return findVideo(command.videoId()).orElseThrow();
  }

  public VideoRecord markNotification(
      String videoId,
      String notifyStatus,
      String notifyError,
      String notifiedAt,
      String updatedAt) {
    jdbc.update("""
        UPDATE youtube_videos
        SET notify_status = ?, notify_error = ?, notified_at = ?, updated_at = ?
        WHERE video_id = ?
        """,
        value(notifyStatus),
        value(notifyError),
        value(notifiedAt),
        value(updatedAt),
        videoId);
    return findVideo(videoId).orElseThrow();
  }

  public VideoRecord markRead(String videoId, String readAt, String updatedAt) {
    jdbc.update("""
        UPDATE youtube_videos
        SET read_at = ?, updated_at = ?
        WHERE video_id = ?
        """,
        value(readAt),
        value(updatedAt),
        videoId);
    return findVideo(videoId).orElseThrow();
  }

  private List<TranscriptSegment> readSegments(String payload) {
    if (payload == null || payload.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(payload, SEGMENT_LIST);
    } catch (Exception error) {
      return List.of();
    }
  }

  private String writeSegments(List<TranscriptSegment> segments) {
    try {
      return objectMapper.writeValueAsString(segments == null ? List.of() : segments);
    } catch (JsonProcessingException error) {
      return "[]";
    }
  }

  private static String value(String text) {
    return text == null ? "" : text.trim();
  }

  public record ChannelRecord(
      String id,
      String channelId,
      String title,
      String handle,
      String sourceUrl,
      boolean enabled,
      String lastCheckedAt,
      String lastVideoPublishedAt,
      String createdAt,
      String updatedAt) {
  }

  public record VideoRecord(
      String videoId,
      String channelRowId,
      String channelId,
      String title,
      String videoUrl,
      String publishedAt,
      String audioPath,
      long audioDurationMs,
      String transcriptStatus,
      String transcriptLanguage,
      String transcriptSource,
      String transcriptText,
      List<TranscriptSegment> transcriptSegments,
      String errorMessage,
      String notifyStatus,
      String notifyError,
      String notifiedAt,
      String readAt,
      String syncedAt,
      String createdAt,
      String updatedAt) {
    public VideoRecord(
        String videoId,
        String channelRowId,
        String channelId,
        String title,
        String videoUrl,
        String publishedAt,
        String audioPath,
        long audioDurationMs,
        String transcriptStatus,
        String transcriptLanguage,
        String transcriptSource,
        String transcriptText,
        List<TranscriptSegment> transcriptSegments,
        String errorMessage,
        String syncedAt,
        String createdAt,
        String updatedAt) {
      this(
          videoId,
          channelRowId,
          channelId,
          title,
          videoUrl,
          publishedAt,
          audioPath,
          audioDurationMs,
          transcriptStatus,
          transcriptLanguage,
          transcriptSource,
          transcriptText,
          transcriptSegments,
          errorMessage,
          "",
          "",
          "",
          "",
          syncedAt,
          createdAt,
          updatedAt);
    }
  }

  public record SaveChannelCommand(
      String channelId,
      String title,
      String handle,
      String sourceUrl,
      boolean enabled,
      String lastCheckedAt,
      String lastVideoPublishedAt,
      String updatedAt) {
  }

  public record SaveVideoCommand(
      String videoId,
      String channelRowId,
      String channelId,
      String title,
      String videoUrl,
      String publishedAt,
      String audioPath,
      long audioDurationMs,
      String transcriptStatus,
      String transcriptLanguage,
      String transcriptSource,
      String transcriptText,
      List<TranscriptSegment> transcriptSegments,
      String errorMessage,
      String syncedAt,
      String updatedAt) {
  }

  public record TranscriptSegment(int startMs, int endMs, String text) {
  }
}
