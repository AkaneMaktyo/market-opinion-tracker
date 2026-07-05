import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../../api/client';
import { apiBase } from '../../api/http';
import type { MarketBar, OpinionView, Timeframe } from '../../types';

const BAR_RETRY_MS = 2400;
const BAR_RETRY_LIMIT = 15;

type StreamRequest = {
  kolId: string;
  symbol: string;
  frame: Timeframe;
};

export function useChartData() {
  const [bars, setBars] = useState<MarketBar[]>([]);
  const [opinions, setOpinions] = useState<OpinionView[]>([]);
  const [chartLoading, setChartLoading] = useState(false);
  const [chartRefreshing, setChartRefreshing] = useState(false);
  const [chartMessage, setChartMessage] = useState('');
  const [streamKey, setStreamKey] = useState('');
  const barsCache = useRef(new Map<string, MarketBar[]>());
  const streamRequest = useRef<StreamRequest | null>(null);
  const chartSeq = useRef(0);
  const retryTimer = useRef<number | null>(null);

  const clearRetry = useCallback(() => {
    if (retryTimer.current == null) return;
    window.clearTimeout(retryTimer.current);
    retryTimer.current = null;
  }, []);

  const loadChart = useCallback(async function runChartLoad(
    kolId: string,
    symbol: string,
    frame: Timeframe,
    attempt = 0,
  ) {
    clearRetry();
    const seq = ++chartSeq.current;
    if (!symbol) {
      streamRequest.current = null;
      setStreamKey('');
      setBars([]);
      setOpinions([]);
      setChartLoading(false);
      setChartRefreshing(false);
      setChartMessage('');
      return;
    }

    streamRequest.current = { kolId, symbol, frame };
    setStreamKey(`${kolId}:${symbol}:${frame}`);
    const cacheKey = `${symbol}:${frame}`;
    const cached = barsCache.current.get(cacheKey);
    setBars(cached || []);
    setChartLoading(!cached);
    setChartRefreshing(Boolean(cached));
    setChartMessage(cached ? '正在刷新 K 线' : '正在加载 K 线');

    try {
      const [nextBars, nextOpinions] = await Promise.all([
        api.bars(symbol, frame),
        api.opinions(kolId, symbol),
      ]);
      if (seq !== chartSeq.current) return;
      barsCache.current.set(cacheKey, nextBars);
      setBars(nextBars);
      setOpinions(nextOpinions);
      setChartLoading(false);
      setChartRefreshing(false);
      handleEmptyBars(runChartLoad, kolId, symbol, frame, attempt, nextBars.length);
    } catch (error) {
      if (seq !== chartSeq.current) return;
      setChartLoading(false);
      setChartRefreshing(false);
      setChartMessage(error instanceof Error ? error.message : 'K 线加载失败');
    }
  }, [clearRetry]);

  function handleEmptyBars(
    runChartLoad: typeof loadChart,
    kolId: string,
    symbol: string,
    frame: Timeframe,
    attempt: number,
    count: number,
  ) {
    if (count > 0) {
      setChartMessage('');
      return;
    }
    if (attempt < BAR_RETRY_LIMIT) {
      setChartMessage('正在后台加载 K 线，稍后自动刷新');
      retryTimer.current = window.setTimeout(
        () => void runChartLoad(kolId, symbol, frame, attempt + 1),
        BAR_RETRY_MS,
      );
      return;
    }
    setChartMessage('暂时没有 K 线数据，可以尝试回填当前品种');
  }

  useEffect(() => {
    if (!streamKey) return;
    const request = streamRequest.current;
    if (!request) return;
    const source = new EventSource(streamUrl(request.symbol, request.frame));
    let closed = false;
    source.addEventListener('bar', (event) => {
      if (closed || !sameStreamRequest(streamRequest.current, request)) return;
      const nextBar = JSON.parse(event.data) as MarketBar;
      setBars((current) => {
        const nextBars = mergeBar(current, nextBar);
        barsCache.current.set(`${request.symbol}:${request.frame}`, nextBars);
        return nextBars;
      });
      setChartMessage('');
    });
    source.onerror = () => {
      if (!closed && sameStreamRequest(streamRequest.current, request)) {
        setChartMessage('实时 K 线连接中断，正在重连');
      }
    };
    return () => {
      closed = true;
      source.close();
    };
  }, [streamKey]);

  useEffect(() => clearRetry, [clearRetry]);

  return { bars, opinions, chartLoading, chartRefreshing, chartMessage, loadChart };
}

function streamUrl(symbol: string, frame: Timeframe) {
  const params = new URLSearchParams({ timeframe: frame });
  return `${apiBase}/market/${encodeURIComponent(symbol)}/stream?${params}`;
}

function mergeBar(current: MarketBar[], bar: MarketBar) {
  const index = current.findIndex((item) =>
    item.timeframe === bar.timeframe && item.barTime === bar.barTime);
  if (index >= 0) {
    return current.map((item, itemIndex) => (itemIndex === index ? bar : item));
  }
  return [...current, bar].sort((left, right) => left.barTime.localeCompare(right.barTime));
}

function sameStreamRequest(current: StreamRequest | null, request: StreamRequest) {
  return current?.kolId === request.kolId
    && current.symbol === request.symbol
    && current.frame === request.frame;
}
