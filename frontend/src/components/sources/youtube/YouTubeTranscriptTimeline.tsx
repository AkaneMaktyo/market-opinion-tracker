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
  const containerRef = useRef<HTMLDivElement | null>(null);
  const refs = useRef<Array<HTMLButtonElement | null>>([]);
  const previousIndexRef = useRef(-1);
  const activeIndex = useMemo(() => findActiveSegmentIndex(activeMs, segments), [activeMs, segments]);

  useEffect(() => {
    if (activeIndex < 0) {
      return;
    }
    if (activeIndex === previousIndexRef.current) {
      return;
    }
    previousIndexRef.current = activeIndex;
    const frameId = window.requestAnimationFrame(() => {
      centerActiveSegment(activeIndex, containerRef.current, refs.current[activeIndex]);
    });
    return () => window.cancelAnimationFrame(frameId);
  }, [activeIndex]);

  return (
    <div className="youtube-transcript" ref={containerRef}>
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

function centerActiveSegment(
  activeIndex: number,
  container: HTMLDivElement | null,
  element: HTMLButtonElement | null,
) {
  if (activeIndex < 0 || !element) {
    return;
  }
  if (container && canScrollInsideContainer(container)) {
    centerInsideContainer(container, element);
    return;
  }
  centerInsideViewport(element);
}

function canScrollInsideContainer(container: HTMLDivElement | null) {
  return !!container && container.scrollHeight > container.clientHeight + 8;
}

function centerInsideContainer(container: HTMLDivElement, element: HTMLButtonElement) {
  const maxTop = Math.max(0, container.scrollHeight - container.clientHeight);
  const nextTop = element.offsetTop - (container.clientHeight - element.offsetHeight) / 2;
  container.scrollTo({
    top: clamp(nextTop, 0, maxTop),
    behavior: 'smooth',
  });
}

function centerInsideViewport(element: HTMLButtonElement) {
  const rect = element.getBoundingClientRect();
  const nextTop = window.scrollY + rect.top - (window.innerHeight - rect.height) / 2;
  window.scrollTo({
    top: Math.max(0, nextTop),
    behavior: 'smooth',
  });
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

function findActiveSegmentIndex(activeMs: number, segments: YouTubeTranscriptSegment[]) {
  for (let index = segments.length - 1; index >= 0; index -= 1) {
    if (activeMs >= segments[index].startMs) {
      return index;
    }
  }
  return -1;
}
