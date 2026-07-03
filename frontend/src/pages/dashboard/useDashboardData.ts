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

const DEFAULT_KOL = 'kzg';

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
  const { bars, opinions, chartLoading, chartRefreshing, chartMessage, loadChart } = useChartData();

  const selectedRef = useRef(selected);
  const kolRef = useRef(selectedKol);
  const timeframeRef = useRef(timeframe);
  const shellSeq = useRef(0);

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
    const [nextKols, current, nextSessions, nextBackfill, nextGroups] = await Promise.all([
      api.kols(),
      api.instruments(kolId, 'current'),
      api.sessions(kolId),
      api.marketBackfill(),
      api.instrumentGroups(),
    ]);
    const nextInstruments = current.length ? current : await api.instruments(kolId, 'history');
    if (seq !== shellSeq.current) return;
    const nextSelected = pickSelectedSymbol(nextInstruments, requested);
    setKols(nextKols);
    setInstruments(nextInstruments);
    setSessions(nextSessions);
    setBackfill(nextBackfill);
    setInstrumentGroups(nextGroups);
    setSelectedValue(nextSelected);
    void loadChart(kolId, nextSelected, timeframeRef.current);
  }, [loadChart, setSelectedValue]);

  useEffect(() => {
    void loadShell(DEFAULT_KOL, '');
  }, [loadShell]);

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
    setKolValue(kolId);
    void loadShell(kolId, '');
  }, [loadShell, setKolValue]);

  const selectSymbol = useCallback((symbol: string) => {
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
      setBackfillError('当前 KOL 还没有持仓品种');
      return;
    }
    setBackfillBusy(true);
    setBackfillError('');
    try {
      setBackfill(await api.startSymbolMarketBackfill(selectedRef.current));
    } catch (error) {
      setBackfillError(error instanceof Error ? error.message : '启动当前品种回填失败');
    } finally {
      setBackfillBusy(false);
    }
  }

  return {
    selected, selectedKol, kols, instruments, sessions, bars, timeframe, opinions,
    backfill, backfillBusy, backfillError, instrumentGroups, chartLoading,
    chartRefreshing, chartMessage, setKols, selectKol, selectSymbol, changeTimeframe,
    reload, refreshOpinions, startBackfillAll, startBackfillCurrent,
  };
}

function pickSelectedSymbol(instruments: Instrument[], requested: string) {
  return requested && instruments.some((item) => item.symbol === requested) ? requested : instruments[0]?.symbol || '';
}
