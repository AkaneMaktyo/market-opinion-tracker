import { Settings } from 'lucide-react';
import { useEffect, useState } from 'react';
import type { DragEvent } from 'react';
import type { Instrument } from '../../types';
import { InstrumentDirectory } from './InstrumentDirectory';
import { FlatInstrumentList } from './FlatInstrumentList';
import { InstrumentManager } from './InstrumentManager';
import { InstrumentSortBar } from './InstrumentSortBar';
import { applyManualOrder, parseMode, sortItems, type SortMode } from './instrumentList';

const ORDER_STORAGE_KEY = 'market-opinion-instrument-order';
const SORT_STORAGE_KEY = 'market-opinion-instrument-sort';
const DEFAULT_KOL = 'default';

interface Props {
  instruments: Instrument[]; selected: string; kolId: string;
  onSelect: (symbol: string) => void; onChanged: (nextSelected?: string) => void;
}

export function InstrumentRail({ instruments, selected, kolId, onSelect, onChanged }: Props) {
  const [order, setOrder] = useState<string[]>([]);
  const [mode, setMode] = useState<SortMode>('manual');
  const [draggingItem, setDraggingItem] = useState('');
  const [managing, setManaging] = useState<Instrument | null>(null);
  const [directoryOpen, setDirectoryOpen] = useState(false);
  const customMode = kolId === DEFAULT_KOL;
  const railItems = customMode ? applyManualOrder(instruments, order) : instruments;
  const visibleItems = customMode ? sortItems(railItems, mode) : railItems;

  useEffect(() => {
    try {
      setOrder(JSON.parse(window.localStorage.getItem(ORDER_STORAGE_KEY) || '[]'));
      setMode(parseMode(window.localStorage.getItem(SORT_STORAGE_KEY)));
    } catch {
      setOrder([]);
      setMode('manual');
    }
  }, []);

  function resetItemDrag() {
    setDraggingItem('');
  }

  function dropItemOn(event: DragEvent<HTMLDivElement>, target: string) {
    event.preventDefault();
    if (!customMode || mode !== 'manual' || !draggingItem || draggingItem === target) return resetItemDrag();
    const current = railItems.map((item) => item.symbol).filter((symbol) => symbol !== draggingItem);
    const rect = event.currentTarget.getBoundingClientRect();
    const insertAt = current.indexOf(target) + (event.clientY > rect.top + rect.height / 2 ? 1 : 0);
    const next = [...current.slice(0, insertAt), draggingItem, ...current.slice(insertAt)];
    setOrder(next);
    window.localStorage.setItem(ORDER_STORAGE_KEY, JSON.stringify(next));
    resetItemDrag();
  }

  return (
    <aside className="rail">
      <div className="rail-head">
        <div>
          <div className="panel-title">自选表</div>
          <span className="rail-note">{customMode ? (mode === 'manual' ? '拖动排序' : '行情排序') : '最新观点排序'}</span>
        </div>
        <button className="rail-manage" onClick={() => setDirectoryOpen(true)} type="button">
          <Settings size={14} />
          <span>管理</span>
        </button>
      </div>
      {customMode ? <InstrumentSortBar mode={mode} onChange={(nextMode) => {
        setMode(nextMode);
        window.localStorage.setItem(SORT_STORAGE_KEY, nextMode);
      }} /> : null}
      <div className="rail-table-head">
        <span>商品</span>
        <span>最新价</span>
        <span>涨跌</span>
        <span>涨跌%</span>
      </div>
      <FlatInstrumentList
        draggingItem={draggingItem}
        items={visibleItems}
        manualMode={customMode && mode === 'manual'}
        onDragItemEnd={resetItemDrag}
        onDragItemOver={(event) => customMode && mode === 'manual' && event.preventDefault()}
        onDragItemStart={setDraggingItem}
        onDropItem={dropItemOn}
        onManage={setManaging}
        onSelect={onSelect}
        selected={selected}
      />
      {directoryOpen ? (
        <InstrumentDirectory
          instruments={instruments}
          onChanged={onChanged}
          onClose={() => setDirectoryOpen(false)}
          selected={selected}
        />
      ) : null}
      {managing ? (
        <InstrumentManager
          instrument={managing}
          instruments={instruments}
          onChanged={(nextSelected) => {
            setManaging(null);
            onChanged(nextSelected);
          }}
          onClose={() => setManaging(null)}
        />
      ) : null}
    </aside>
  );
}
