export interface YouTubeTranscriptSegment {
  startMs: number;
  endMs: number;
  text: string;
}

export interface YouTubeVideo {
  videoId: string;
  channelRowId: string;
  channelId: string;
  title: string;
  videoUrl: string;
  publishedAt: string;
  audioPath?: string;
  audioDurationMs: number;
  transcriptStatus: string;
  transcriptLanguage: string;
  transcriptSource: string;
  transcriptText: string;
  transcriptSegments: YouTubeTranscriptSegment[];
  errorMessage?: string;
  syncedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface YouTubeChannelRecord {
  id: string;
  channelId: string;
  title: string;
  handle: string;
  sourceUrl: string;
  enabled: boolean;
  lastCheckedAt?: string;
  lastVideoPublishedAt?: string;
  createdAt: string;
  updatedAt: string;
}

export interface YouTubeChannel {
  channel: YouTubeChannelRecord;
  videos: YouTubeVideo[];
}
