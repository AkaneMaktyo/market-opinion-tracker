import { useCallback, useEffect, useState } from 'react';
import { api } from './api/client';
import { AppBrand } from './components/brand/AppBrand';
import { ChartPanel } from './components/ChartPanel';
import { JsonImportPanel } from './components/JsonImportPanel';
import { KolPicker } from './components/KolPicker';
import { MarketSummaryPanel } from './components/MarketSummaryPanel';
import { OpinionList } from './components/OpinionList';
import { InstrumentRail } from './components/instruments/InstrumentRail';
import { ResonancePanel } from './components/resonance/ResonancePanel';
import { SourceManagerButton } from './components/sources/SourceManagerButton';
import type {
  Instrument,
  Kol,
  LiveSession,
  MarketBackfillStatus,
  MarketBar,
  OpinionView,
  Timeframe,
} from './types';

export default function App() {
  const [selected, setSelected] = useState('');
  const [selectedKol, setSelectedKol] = useState('kzg');
  const [kols, setKols] = useState<Kol[]>([]);
  const [instruments, setInstruments] = useState<Instrument[]>([]);
  const [sessions, setSessions] = useState<LiveSession[]>([]);
  const [bars, setBars] = useState<MarketBar[]>([]);
  const [timeframe, setTimeframe] = useState<Timeframe>('1D');
  const [opinions, setOpinions] = useState<OpinionView[]>([]);
  const [backfill, setBackfill] = useState<MarketBackfillStatus | null>(null);
  const [backfillBusy, setBackfillBusy] = useState(false);
  const [backfillError, setBackfillError] = useState('');
  const [instrumentGroups, setInstrumentGroups] = useState<string[]>([]);

  const load = useCallback(async (
    kolId = selectedKol,
    symbol = selected,
    frame = timeframe,
  ) => {
    const [nextKols, nextInstruments, nextSessions, nextBackfill, nextGroups] = await Promise.all([
      api.kols(),
      api.instruments(kolId, 'current'),
      api.sessions(kolId),
      api.marketBackfill(),
      api.instrumentGroups(),
    ]);
    const nextSelected = pickSelectedSymbol(nextInstruments, symbol);
    const [nextBars, nextOpinions] = nextSelected
      ? await Promise.all([
        api.bars(nextSelected, frame),
        api.opinions(kolId, nextSelected),
      ])
      : [[], []];
    setKols(nextKols);
    setInstruments(nextInstruments);
    setSessions(nextSessions);
    setBars(nextBars);
    setOpinions(nextOpinions);
    setBackfill(nextBackfill);
    setInstrumentGroups(nextGroups);
    setSelected(nextSelected);
  }, [selected, selectedKol, timeframe]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (backfill?.state !== 'RUNNING') {
      return;
    }
    const timer = window.setInterval(async () => {
      try {
        const next = await api.marketBackfill();
        setBackfill(next);
        if (next.state !== 'RUNNING') {
          load(selectedKol, selected, timeframe);
        }
      } catch (error) {
        setBackfillError(error instanceof Error ? error.message : '读取回填状态失败');
      }
    }, 3000);
    return () => window.clearInterval(timer);
  }, [backfill?.state, load, selected, selectedKol, timeframe]);

  function select(symbol: string) {
    setSelected(symbol);
    load(selectedKol, symbol, timeframe);
  }

  function changeKol(kolId: string) {
    setSelectedKol(kolId);
    load(kolId, selected, timeframe);
  }

  function changeTimeframe(next: Timeframe) {
    setTimeframe(next);
    load(selectedKol, selected, next);
  }

  function refreshSelected(nextSelected = selected) {
    setSelected(nextSelected);
    load(selectedKol, nextSelected, timeframe);
  }

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
    if (!selected) {
      setBackfillError('当前 KOL 还没有持仓品种');
      return;
    }
    setBackfillBusy(true);
    setBackfillError('');
    try {
      setBackfill(await api.startSymbolMarketBackfill(selected));
    } catch (error) {
      setBackfillError(error instanceof Error ? error.message : '启动当前品种回填失败');
    } finally {
      setBackfillBusy(false);
    }
  }

  return (
    <main className="app">
      <header className="topbar">
        <AppBrand />
        <KolPicker
          kols={kols}
          selectedId={selectedKol}
          onChange={changeKol}
          onCreated={(kol) => {
            setKols((items) => [...items, kol]);
            changeKol(kol.id);
          }}
        />
        <JsonImportPanel
          kolId={selectedKol}
          onImported={(symbol) => {
            setSelected(symbol);
            load(selectedKol, symbol, timeframe);
          }}
        />
        <SourceManagerButton onChanged={() => void load(selectedKol, selected, timeframe)} />
        <div className="stats">
          <span>{instruments.length} 个当前持仓</span>
          <span>{opinions.length} 条当前观点</span>
          <span>{sessions.length} 场记录</span>
        </div>
      </header>
      <div className="workspace">
        <InstrumentRail
          groups={instrumentGroups}
          instruments={instruments}
          onChanged={refreshSelected}
          onSelect={select}
          selected={selected}
        />
        <div className="center">
          <ChartPanel
            backfill={backfill}
            backfillBusy={backfillBusy}
            backfillError={backfillError}
            bars={bars}
            onBackfillAll={startBackfillAll}
            onBackfillCurrent={startBackfillCurrent}
            onTimeframeChange={changeTimeframe}
            opinions={opinions}
            symbol={selected}
            timeframe={timeframe}
          />
        </div>
        <div className="right-column">
          <ResonancePanel symbol={selected} />
          <MarketSummaryPanel sessions={sessions} />
          <OpinionList
            opinions={opinions}
            onChanged={() => load(selectedKol, selected, timeframe)}
            symbol={selected}
          />
        </div>
      </div>
    </main>
  );
}

const pickSelectedSymbol = (instruments: Instrument[], requested: string) =>
  requested && instruments.some((item) => item.symbol === requested) ? requested : instruments[0]?.symbol || '';
