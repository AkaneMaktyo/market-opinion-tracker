import { useCallback, useEffect, useState } from 'react';
import { api } from './api/client';
import { ChartPanel } from './components/ChartPanel';
import { InstrumentRail } from './components/InstrumentRail';
import { JsonImportPanel } from './components/JsonImportPanel';
import { KolPicker } from './components/KolPicker';
import { OpinionList } from './components/OpinionList';
import type { Instrument, Kol, LiveSession, MarketBar, OpinionView } from './types';

export default function App() {
  const [selected, setSelected] = useState('NVDA');
  const [selectedKol, setSelectedKol] = useState('default');
  const [kols, setKols] = useState<Kol[]>([]);
  const [instruments, setInstruments] = useState<Instrument[]>([]);
  const [sessions, setSessions] = useState<LiveSession[]>([]);
  const [bars, setBars] = useState<MarketBar[]>([]);
  const [opinions, setOpinions] = useState<OpinionView[]>([]);

  const load = useCallback(async (kolId = selectedKol, symbol = selected) => {
    const [nextKols, nextInstruments, nextSessions, nextBars, nextOpinions] = await Promise.all([
      api.kols(),
      api.instruments(kolId),
      api.sessions(kolId),
      api.bars(symbol),
      api.opinions(kolId, symbol),
    ]);
    setKols(nextKols);
    setInstruments(nextInstruments);
    setSessions(nextSessions);
    setBars(nextBars);
    setOpinions(nextOpinions);
  }, [selected, selectedKol]);

  useEffect(() => {
    load();
  }, [load]);

  function select(symbol: string) {
    setSelected(symbol);
    load(selectedKol, symbol);
  }

  function changeKol(kolId: string) {
    setSelectedKol(kolId);
    load(kolId, selected);
  }

  return (
    <main className="app">
      <header className="topbar">
        <div>
          <span className="eyebrow">Market Opinion Tracker</span>
          <h1>美股直播观点追踪</h1>
        </div>
        <KolPicker
          kols={kols}
          selectedId={selectedKol}
          onChange={changeKol}
          onCreated={(kol) => {
            setKols((items) => [...items, kol]);
            changeKol(kol.id);
          }}
        />
        <div className="stats">
          <span>{instruments.length} 个品种</span>
          <span>{opinions.length} 条当前观点</span>
        </div>
      </header>
      <div className="workspace">
        <InstrumentRail instruments={instruments} selected={selected} onSelect={select} />
        <div className="center">
          <ChartPanel symbol={selected} bars={bars} opinions={opinions} />
          <JsonImportPanel
            kolId={selectedKol}
            onImported={(symbol) => {
              setSelected(symbol);
              load(selectedKol, symbol);
            }}
          />
        </div>
        <OpinionList opinions={opinions} onChanged={() => load(selected)} />
      </div>
    </main>
  );
}
