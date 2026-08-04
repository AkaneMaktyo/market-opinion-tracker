import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../../api/client';
import type {
  Instrument,
  Kol,
  LiveSession,
  MarketBackfillStatus,
  Timeframe,
} from '../../types';
import { useChartData } from './useChartData';
import { pickSelectedSymbol, readInstrumentCache, writeInstrumentCache } from './instrumentCache';

const DEFAULT_KOL = 'default';
const QUOTE_REFRESH_MS = 12000;

export function useDashboardData() {
  const [selected, setSelected] = useState('');
  const [selectedKol, setSelectedKol] = useState(DEFAULT_KOL);
  const [kols, setKols] = useState<Kol[]>([]);
  const [instruments, setInstruments] = useState<Instrument[]>([]);
  const [sessions, setSessions] = useState<LiveSession[]>([]);
  const [timeframe, setTimeframe] = useState<Timeframe>('1D');
  const [backfill, setBackfill] = useState<MarketBackfillStatus | null>(null);
  const [backfillBusy, setBackfillBusy] = useState(false);
  const [backfillError, setBackfillError] = useState('');
  const [instrumentGroups, setInstrumentGroups] = useState<string[]>([]);
  const chart = useChartData();
  const { bars, opinions, chartLoading, chartRefreshing, chartMessage, loadChart } = chart;

  const selectedRef = useRef(selected);
  const kolRef = useRef(selectedKol);
  const timeframeRef = useRef(timeframe);
  const shellSeq = useRef(0);
  const quoteRefreshInFlight = useRef(false);
  const selectionPinned = useRef(false);
  const instrumentCache = useRef(new Map<string, Instrument[]>());

  const setSelectedValue = useCallback((value: string) => {
    selectedRef.current = value;
    setSelected(value);
  }, []);

  const setKolValue = useCallback((value: string) => {
    kolRef.current = value;
    setSelectedKol(value);
  }, []);

  const setFrameValue = useCallback((value: Timeframe) => {
    timeframeRef.current = value;
    setTimeframe(value);
  }, []);

  const loadShell = useCallback(async (kolId = kolRef.current, requested = selectedRef.current) => {
    const seq = ++shellSeq.current;
    const cached = instrumentCache.current.get(kolId) || readInstrumentCache(kolId);
    if (cached) {
      instrumentCache.current.set(kolId, cached);
      const nextSelected = selectionPinned.current && selectedRef.current
        ? selectedRef.current
        : pickSelectedSymbol(cached, requested);
      setInstruments(cached);
      setSelectedValue(nextSelected);
      void loadChart(kolId, nextSelected, timeframeRef.current);
    }
    const instrumentsRequest = api.watchlist(kolId, false);
    const shellRequest = Promise.all([
      api.kols(),
      api.sessions(kolId),
      api.marketBackfill(),
      api.instrumentGroups(kolId),
    ]);
    const nextInstruments = await instrumentsRequest;
    if (seq !== shellSeq.current) return;
    const nextSelected = selectionPinned.current && selectedRef.current
      ? selectedRef.current
      : pickSelectedSymbol(nextInstruments, requested);
    instrumentCache.current.set(kolId, nextInstruments);
    writeInstrumentCache(kolId, nextInstruments);
    setInstruments(nextInstruments);
    setSelectedValue(nextSelected);
    void loadChart(kolId, nextSelected, timeframeRef.current);
    void api.watchlist(kolId).then((refreshed) => {
      if (seq !== shellSeq.current) return;
      instrumentCache.current.set(kolId, refreshed);
      writeInstrumentCache(kolId, refreshed);
      setInstruments(refreshed);
    }).catch(() => undefined);
    const [nextKols, nextSessions, nextBackfill, nextGroups] = await shellRequest;
    if (seq !== shellSeq.current) return;
    setKols(nextKols);
    setSessions(nextSessions);
    setBackfill(nextBackfill);
    setInstrumentGroups(nextGroups);
  }, [loadChart, setSelectedValue]);

  useEffect(() => {
    void loadShell(DEFAULT_KOL, '');
  }, [loadShell]);

  const refreshQuotes = useCallback(async () => {
    if (quoteRefreshInFlight.current) return;
    quoteRefreshInFlight.current = true;
    try {
      const nextInstruments = await api.watchlist(kolRef.current);
      instrumentCache.current.set(kolRef.current, nextInstruments);
      writeInstrumentCache(kolRef.current, nextInstruments);
      setInstruments(nextInstruments);
      const nextSelected = selectionPinned.current && selectedRef.current
        ? selectedRef.current
        : pickSelectedSymbol(nextInstruments, selectedRef.current);
      if (nextSelected && nextSelected !== selectedRef.current) {
        setSelectedValue(nextSelected);
      }
    } finally {
      quoteRefreshInFlight.current = false;
    }
  }, [setSelectedValue]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      if (document.hidden) return;
      void refreshQuotes().catch(() => undefined);
    }, QUOTE_REFRESH_MS);
    return () => window.clearInterval(timer);
  }, [refreshQuotes]);

  useEffect(() => {
    if (backfill?.state !== 'RUNNING') return;
    const timer = window.setInterval(async () => {
      try {
        const next = await api.marketBackfill();
        setBackfill(next);
        if (next.state !== 'RUNNING') {
          void loadChart(kolRef.current, selectedRef.current, timeframeRef.current);
        }
      } catch (error) {
        setBackfillError(error instanceof Error ? error.message : '读取回填状态失败');
      }
    }, 3000);
    return () => window.clearInterval(timer);
  }, [backfill?.state, loadChart]);

  const selectKol = useCallback((kolId: string) => {
    selectionPinned.current = false;
    setSelectedValue('');
    setKolValue(kolId);
    void loadShell(kolId, '');
  }, [loadShell, setKolValue, setSelectedValue]);

  const selectSymbol = useCallback((symbol: string) => {
    selectionPinned.current = true;
    setSelectedValue(symbol);
    void loadChart(kolRef.current, symbol, timeframeRef.current);
  }, [loadChart, setSelectedValue]);

  const changeTimeframe = useCallback((next: Timeframe) => {
    setFrameValue(next);
    void loadChart(kolRef.current, selectedRef.current, next);
  }, [loadChart, setFrameValue]);

  const reload = useCallback((symbol = selectedRef.current) => {
    setSelectedValue(symbol);
    void loadShell(kolRef.current, symbol);
  }, [loadShell, setSelectedValue]);

  const refreshOpinions = useCallback(() => {
    void loadChart(kolRef.current, selectedRef.current, timeframeRef.current);
  }, [loadChart]);

  async function startBackfillAll() {
    setBackfillBusy(true);
    setBackfillError('');
    try {
      setBackfill(await api.startMarketBackfill());
    } catch (error) {
      setBackfillError(error instanceof Error ? error.message : '启动全量回填失败');
    } finally {
      setBackfillBusy(false);
    }
  }

  async function startBackfillCurrent() {
    if (!selectedRef.current) {
      setBackfillError('当前 KOL 还没有相关标的');
      return;
    }
    setBackfillBusy(true);
    setBackfillError('');
    try {
      setBackfill(await api.startSymbolMarketBackfill(selectedRef.current));
    } catch (error) {
      setBackfillError(error instanceof Error ? error.message : '启动当前标的回填失败');
    } finally {
      setBackfillBusy(false);
    }
  }

  return {
    selected, selectedKol, kols, instruments, sessions, bars, timeframe, opinions,
    backfill, backfillBusy, backfillError, instrumentGroups, chartLoading,
    chartRefreshing, chartMessage, setKols, selectKol, selectSymbol, changeTimeframe,
    reload, refreshOpinions, startBackfillAll, startBackfillCurrent,
    chartLiveStatus: chart.chartLiveStatus,
    lastRealtimeAt: chart.lastRealtimeAt,
    historyLoading: chart.historyLoading,
    loadOlderBars: chart.loadOlderBars,
  };
}
