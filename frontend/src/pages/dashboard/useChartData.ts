import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../../api/client';
import { apiBase } from '../../api/http';
import type { ChartLiveStatus, MarketBar, OpinionView, Timeframe } from '../../types';
import {
  BAR_RETRY_LIMIT,
  BAR_RETRY_MS,
  HISTORY_PAGE_SIZE,
  INITIAL_BAR_COUNT,
  MIN_USABLE_BARS,
  liveStatus,
  mergeBars,
  requestKey,
  sameRequest,
  streamUrl,
} from './chartDataUtils';
import type { BarCacheEntry, StreamRequest } from './chartDataUtils';

export function useChartData() {
  const [bars, setBars] = useState<MarketBar[]>([]);
  const [opinions, setOpinions] = useState<OpinionView[]>([]);
  const [chartLoading, setChartLoading] = useState(false);
  const [chartRefreshing, setChartRefreshing] = useState(false);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [hasMoreBars, setHasMoreBars] = useState(false);
  const [chartMessage, setChartMessage] = useState('');
  const [chartLiveStatus, setChartLiveStatus] = useState<ChartLiveStatus>('connecting');
  const [lastRealtimeAt, setLastRealtimeAt] = useState<number | null>(null);
  const [streamKey, setStreamKey] = useState('');
  const cache = useRef(new Map<string, BarCacheEntry>());
  const requestRef = useRef<StreamRequest | null>(null);
  const requestSeq = useRef(0);
  const retryTimer = useRef<number | null>(null);
  const olderInFlight = useRef(false);

  const clearRetry = useCallback(() => {
    if (retryTimer.current == null) return;
    window.clearTimeout(retryTimer.current);
    retryTimer.current = null;
  }, []);

  const fetchBars = useCallback(async function run(
    request: StreamRequest,
    seq: number,
    attempt = 0,
  ) {
    try {
      const nextBars = await api.bars(request.symbol, request.frame, { limit: INITIAL_BAR_COUNT });
      if (seq !== requestSeq.current || !sameRequest(requestRef.current, request)) return;
      const entry = { bars: nextBars, hasMore: nextBars.length === INITIAL_BAR_COUNT };
      cache.current.set(requestKey(request), entry);
      setBars(nextBars);
      setHasMoreBars(entry.hasMore);
      setChartLoading(false);
      setChartRefreshing(false);
      setLastRealtimeAt(Date.now());
      if (nextBars.length >= MIN_USABLE_BARS) {
        setChartMessage('');
      } else if (attempt < BAR_RETRY_LIMIT) {
        setChartMessage('正在后台补全历史行情，无需手动刷新');
        retryTimer.current = window.setTimeout(
          () => void run(request, seq, attempt + 1),
          BAR_RETRY_MS,
        );
      } else {
        setChartMessage(nextBars.length ? '可用历史行情较短' : '暂时没有行情数据');
      }
    } catch (error) {
      if (seq !== requestSeq.current) return;
      setChartLoading(false);
      setChartRefreshing(false);
      setChartMessage(error instanceof Error ? error.message : 'K 线加载失败');
    }
  }, []);

  const loadChart = useCallback(async (kolId: string, symbol: string, frame: Timeframe) => {
    clearRetry();
    const seq = ++requestSeq.current;
    if (!symbol) {
      requestRef.current = null;
      setStreamKey('');
      setBars([]);
      setOpinions([]);
      setChartLoading(false);
      setChartMessage('');
      return;
    }
    const request = { kolId, symbol, frame };
    requestRef.current = request;
    setStreamKey(`${kolId}:${symbol}:${frame}`);
    setChartLiveStatus('connecting');
    const cached = cache.current.get(requestKey(request));
    setBars(cached?.bars || []);
    setHasMoreBars(cached?.hasMore || false);
    setChartLoading(!cached);
    setChartRefreshing(Boolean(cached));
    setChartMessage('');
    void api.opinions(kolId, symbol).then((items) => {
      if (seq === requestSeq.current) setOpinions(items);
    }).catch(() => undefined);
    await fetchBars(request, seq);
  }, [clearRetry, fetchBars]);

  const loadOlderBars = useCallback(async () => {
    const request = requestRef.current;
    if (!request || olderInFlight.current || !hasMoreBars || bars.length === 0) return;
    olderInFlight.current = true;
    setHistoryLoading(true);
    const before = bars[0].barTime;
    try {
      const older = await api.bars(request.symbol, request.frame, {
        before,
        limit: HISTORY_PAGE_SIZE,
      });
      if (!sameRequest(requestRef.current, request)) return;
      setBars((current) => {
        const next = mergeBars(current, older);
        cache.current.set(requestKey(request), {
          bars: next,
          hasMore: older.length === HISTORY_PAGE_SIZE,
        });
        return next;
      });
      setHasMoreBars(older.length === HISTORY_PAGE_SIZE);
    } finally {
      olderInFlight.current = false;
      setHistoryLoading(false);
    }
  }, [bars, hasMoreBars]);

  useEffect(() => {
    if (!streamKey) return;
    const request = requestRef.current;
    if (!request) return;
    const source = new EventSource(streamUrl(apiBase, request.symbol, request.frame));
    let closed = false;
    source.onopen = () => !closed && setChartLiveStatus('connecting');
    source.addEventListener('status', (event) => {
      if (!closed) setChartLiveStatus(liveStatus((event as MessageEvent).data));
    });
    source.addEventListener('bar', (event) => {
      if (closed || !sameRequest(requestRef.current, request)) return;
      const nextBar = JSON.parse((event as MessageEvent).data) as MarketBar;
      setBars((current) => {
        const next = mergeBars(current, [nextBar]);
        const prior = cache.current.get(requestKey(request));
        cache.current.set(requestKey(request), { bars: next, hasMore: prior?.hasMore || false });
        return next;
      });
      setLastRealtimeAt(Date.now());
      setChartLiveStatus('live');
      setChartMessage('');
    });
    source.onerror = () => !closed && setChartLiveStatus('reconnecting');
    return () => {
      closed = true;
      source.close();
    };
  }, [streamKey]);

  useEffect(() => clearRetry, [clearRetry]);

  return {
    bars, opinions, chartLoading, chartRefreshing, chartMessage, chartLiveStatus,
    lastRealtimeAt, historyLoading, loadChart, loadOlderBars,
  };
}
