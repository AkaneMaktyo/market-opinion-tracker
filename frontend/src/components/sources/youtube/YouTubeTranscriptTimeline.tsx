import { useEffect, useMemo, useRef } from 'react';
import type { YouTubeTranscriptSegment } from '../../../types/youtube';
import { formatMediaClock } from './youtubeFormat';

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
  const refs = useRef<Array<HTMLButtonElement | null>>([]);
  const activeIndex = useMemo(
    () =>
      segments.findIndex(
        (segment) =>
          activeMs >= segment.startMs &&
          activeMs < Math.max(segment.endMs, segment.startMs + 1000),
      ),
    [activeMs, segments],
  );

  useEffect(() => {
    if (activeIndex < 0) {
      return;
    }
    refs.current[activeIndex]?.scrollIntoView({
      block: 'center',
      behavior: 'smooth',
    });
  }, [activeIndex]);

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
