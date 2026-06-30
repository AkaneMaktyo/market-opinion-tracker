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
  const activeIndex = useMemo(() => findActiveSegmentIndex(activeMs, segments), [activeMs, segments]);

  useEffect(() => {
    if (activeIndex < 0) {
      return;
    }
    const frameId = window.requestAnimationFrame(() => {
      centerActiveSegment(activeIndex, refs.current);
    });
    return () => window.cancelAnimationFrame(frameId);
  }, [activeIndex, segments]);

  useEffect(() => {
    const locate = () => centerActiveSegment(activeIndex, refs.current);
    window.addEventListener('youtube:locate-current-transcript', locate);
    return () => window.removeEventListener('youtube:locate-current-transcript', locate);
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

function centerActiveSegment(activeIndex: number, refs: Array<HTMLButtonElement | null>) {
  if (activeIndex < 0) {
    return;
  }
  const element = refs[activeIndex];
  if (!element) {
    return;
  }
  const scroller = nearestScroller(element);
  if (scroller) {
    centerInsideScroller(scroller, element);
    return;
  }
  centerInsideViewport(element);
}

function nearestScroller(element: HTMLElement) {
  let parent = element.parentElement;
  while (parent) {
    const styles = window.getComputedStyle(parent);
    const scrollable = /(auto|scroll|overlay)/.test(styles.overflowY);
    if (scrollable && parent.scrollHeight > parent.clientHeight + 8) {
      return parent;
    }
    parent = parent.parentElement;
  }
  return null;
}

function centerInsideScroller(scroller: HTMLElement, element: HTMLElement) {
  const maxTop = Math.max(0, scroller.scrollHeight - scroller.clientHeight);
  const nextTop = offsetWithinScroller(scroller, element) - (scroller.clientHeight - element.offsetHeight) / 2;
  scroller.scrollTo({
    top: clamp(nextTop, 0, maxTop),
    behavior: prefersReducedMotion() ? 'auto' : 'smooth',
  });
}

function offsetWithinScroller(scroller: HTMLElement, element: HTMLElement) {
  const scrollerRect = scroller.getBoundingClientRect();
  const elementRect = element.getBoundingClientRect();
  return elementRect.top - scrollerRect.top + scroller.scrollTop;
}

function centerInsideViewport(element: HTMLElement) {
  const rect = element.getBoundingClientRect();
  const nextTop = window.scrollY + rect.top - (window.innerHeight - rect.height) / 2;
  window.scrollTo({
    top: Math.max(0, nextTop),
    behavior: prefersReducedMotion() ? 'auto' : 'smooth',
  });
}

function prefersReducedMotion() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
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
