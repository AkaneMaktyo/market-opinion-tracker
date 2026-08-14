import { useCallback, useEffect, useMemo, useRef } from 'react';
import type { YouTubeTranscriptSegment } from '../../../../types/youtube';

export function useTranscriptAutoCenter(
  segments: YouTubeTranscriptSegment[],
  activeMs: number,
  enabled = true,
) {
  const refs = useRef<Array<HTMLButtonElement | null>>([]);
  const activeIndex = useMemo(() => findActiveSegmentIndex(activeMs, segments), [activeMs, segments]);
  const centerActive = useCallback(() => {
    centerActiveSegment(activeIndex, refs.current);
  }, [activeIndex]);

  useEffect(() => {
    if (!enabled || activeIndex < 0) {
      return;
    }
    const frameId = window.requestAnimationFrame(centerActive);
    return () => window.cancelAnimationFrame(frameId);
  }, [activeIndex, centerActive, enabled, segments]);

  return { activeIndex, centerActive, refs };
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
