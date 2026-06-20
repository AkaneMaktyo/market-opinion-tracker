package com.personal.tracker.service.youtube.model;

public record ImportedVideo(
    String channelId,
    String channelTitle,
    String handle,
    String sourceUrl,
    String videoId,
    String title,
    String videoUrl,
    String publishedAt,
    String audioPath,
    long audioDurationMs) {
}
