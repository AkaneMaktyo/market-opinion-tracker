import { useEffect } from 'react';
import type { YouTubeTranscriptSegment } from '../../../types/youtube';
import { formatMediaClock } from './youtubeFormat';
import { useTranscriptAutoCenter } from './timeline/useTranscriptAutoCenter';

interface Props {
  segments: YouTubeTranscriptSegment[];
  activeMs: number;
  onSeek: (startMs: number) => void;
}

export function YouTubeTranscriptTimeline({
  segments,
  activeMs,
  onSeek,
}: Props) {
  const { activeIndex, centerActive, refs } = useTranscriptAutoCenter(segments, activeMs);

  useEffect(() => {
    window.addEventListener('youtube:locate-current-transcript', centerActive);
    return () => window.removeEventListener('youtube:locate-current-transcript', centerActive);
  }, [centerActive]);

  return (
    <div className="youtube-transcript">
      {segments.map((segment, index) => {
        const active = index === activeIndex;
        return (
          <button
            className={`youtube-segment youtube-transcript-card${active ? ' active' : ''}`}
            key={`${segment.startMs}-${segment.endMs}-${index}`}
            onClick={() => onSeek(segment.startMs)}
            ref={(node) => {
              refs.current[index] = node;
            }}
            type="button"
          >
            <div className="youtube-transcript-meta">
              <span className="youtube-speaker-pill">
                {active ? '正在播放' : '转写片段'}
              </span>
              <span className="youtube-transcript-time">
                {formatMediaClock(segment.startMs)}
              </span>
            </div>
            <div className="youtube-transcript-text">{segment.text}</div>
          </button>
        );
      })}
    </div>
  );
}
