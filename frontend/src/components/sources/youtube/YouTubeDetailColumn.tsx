import type { RefObject } from 'react';
import { api } from '../../../api/client';
import type { YouTubeVideo } from '../../../types/youtube';
import { YouTubeTranscriptTimeline } from './YouTubeTranscriptTimeline';
import {
  formatDateTimeLabel,
  formatDurationLabel,
  formatTranscriptStatus,
} from './youtubeFormat';

interface Props {
  activeMs: number;
  activeVideo: YouTubeVideo | null;
  activeVideoId: string;
  audioRef: RefObject<HTMLAudioElement | null>;
  durationMs: number;
  videos: YouTubeVideo[];
  onOpenVideo: (videoId: string) => void;
  onSeek: (startMs: number) => void;
  setActiveMs: (value: number) => void;
  setDurationMs: (value: number) => void;
  setPlaying: (value: boolean) => void;
}

export function YouTubeDetailColumn({
  activeMs,
  activeVideo,
  activeVideoId,
  audioRef,
  durationMs,
  videos,
  onOpenVideo,
  onSeek,
  setActiveMs,
  setDurationMs,
  setPlaying,
}: Props) {
  return (
    <div className="youtube-column">
      <div className="youtube-subhead">
        <strong>视频与转写</strong>
        <span className="muted">
          {activeVideo ? formatTranscriptStatus(activeVideo.transcriptStatus) : '点击视频查看详情'}
        </span>
      </div>

      <div className="youtube-list">
        {videos.map((item) => (
          <button
            className={`youtube-card youtube-video${activeVideoId === item.videoId ? ' active' : ''}`}
            key={item.videoId}
            onClick={() => onOpenVideo(item.videoId)}
            type="button"
          >
            <div>
              <strong>{item.title}</strong>
              <p className="muted">
                {formatDateTimeLabel(item.publishedAt)} / {formatDurationLabel(item.audioDurationMs)}
              </p>
            </div>
            <span className={`status-pill${item.transcriptStatus === 'error' ? ' error' : ''}`}>
              {formatTranscriptStatus(item.transcriptStatus)}
            </span>
          </button>
        ))}
        {!videos.length ? <div className="muted">同步后这里会显示最近视频。</div> : null}
      </div>

      <div className="youtube-detail">
        <div className="youtube-subhead">
          <strong>{activeVideo?.title || '尚未选择视频'}</strong>
          <span className="muted">
            {activeVideo
              ? `${formatDateTimeLabel(activeVideo.publishedAt)} / ${formatDurationLabel(
                  durationMs || activeVideo.audioDurationMs,
                )}`
              : '选择后可播放音频，并让当前片段始终保持在屏幕中间'}
          </span>
        </div>
        <audio
          className="youtube-audio-source"
          onLoadedMetadata={(event) =>
            setDurationMs(
              Math.max(
                (event.currentTarget.duration || 0) * 1000,
                activeVideo?.audioDurationMs || 0,
              ),
            )
          }
          onPause={() => setPlaying(false)}
          onPlay={() => setPlaying(true)}
          onTimeUpdate={(event) => setActiveMs(event.currentTarget.currentTime * 1000)}
          preload="metadata"
          ref={audioRef}
          src={activeVideo ? api.youtubeAudioUrl(activeVideo.videoId) : undefined}
        />
        {(activeVideo?.transcriptSegments || []).length ? (
          <YouTubeTranscriptTimeline
            activeMs={activeMs}
            onSeek={onSeek}
            segments={activeVideo?.transcriptSegments || []}
          />
        ) : (
          <div className="muted">
            {activeVideo?.errorMessage || '当前还没有可播放分段。'}
          </div>
        )}
      </div>
    </div>
  );
}
