import type { ChartLiveStatus, MarketBar, Timeframe } from '../../types';

export const INITIAL_BAR_COUNT = 600;
export const HISTORY_PAGE_SIZE = 600;
export const BAR_RETRY_MS = 2400;
export const BAR_RETRY_LIMIT = 15;
export const MIN_USABLE_BARS = 20;
export const STREAM_ERROR_GRACE_MS = 5000;

export type StreamRequest = {
  kolId: string;
  symbol: string;
  frame: Timeframe;
};

export type BarCacheEntry = {
  bars: MarketBar[];
  hasMore: boolean;
};

export function streamUrl(apiBase: string, symbol: string, frame: Timeframe) {
  const params = new URLSearchParams({ timeframe: frame });
  return `${apiBase}/market/${encodeURIComponent(symbol)}/stream?${params}`;
}

export function requestKey(request: StreamRequest) {
  return `${request.symbol}:${request.frame}`;
}

export function sameRequest(left: StreamRequest | null, right: StreamRequest) {
  return left?.kolId === right.kolId
    && left.symbol === right.symbol
    && left.frame === right.frame;
}

export function mergeBars(current: MarketBar[], incoming: MarketBar[]) {
  if (incoming.length === 0) return current;
  if (incoming.length === 1 && current.length > 0) {
    const next = incoming[0];
    const last = current[current.length - 1];
    if (last.timeframe === next.timeframe && last.barTime === next.barTime) {
      return [...current.slice(0, -1), next];
    }
    if (last.barTime < next.barTime) return [...current, next];
  }
  const keyed = new Map(current.map((bar) => [barKey(bar), bar]));
  incoming.forEach((bar) => keyed.set(barKey(bar), bar));
  return [...keyed.values()].sort((left, right) => left.barTime.localeCompare(right.barTime));
}

export function liveStatus(value: string): ChartLiveStatus {
  if (value === 'live' || value === 'subscribed') return 'live';
  if (value === 'polling') return 'polling';
  if (value === 'error' || value === 'delayed' || value === 'parse_error') return 'delayed';
  if (value === 'closed') return 'delayed';
  return 'connecting';
}

function barKey(bar: MarketBar) {
  return `${bar.timeframe}:${bar.barTime}`;
}
