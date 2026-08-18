import { useEffect, useState } from 'react';
import type { DragEvent } from 'react';
import { api } from '../../api/client';
import { InstrumentDirectory } from '../../components/instruments/InstrumentDirectory';
import { InstrumentManager } from '../../components/instruments/InstrumentManager';
import { MobileWatchlistAdd } from './MobileWatchlistAdd';
import { parseMode, type SortMode } from '../../components/instruments/instrumentList';
import type { Instrument } from '../../types';
import type { DashboardModel } from '../screens/mobileTypes';

const storage = {
  order: 'market-opinion-instrument-order',
  sort: 'market-opinion-instrument-sort',
};

export function useMobileInstrumentList(dashboard: DashboardModel, customMode: boolean) {
  const [order, setOrder] = useState<string[]>([]);
  const [mode, setMode] = useState<SortMode>('manual');
  const [draggingItem, setDraggingItem] = useState('');
  const [managing, setManaging] = useState<Instrument | null>(null);
  const [directoryOpen, setDirectoryOpen] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [removing, setRemoving] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    try {
      setOrder(JSON.parse(window.localStorage.getItem(storage.order) || '[]'));
      setMode(parseMode(window.localStorage.getItem(storage.sort)));
    } catch {
      setOrder([]);
      setMode('manual');
    }
  }, []);

  function resetItemDrag() {
    setDraggingItem('');
  }

  function dropItemOn(event: DragEvent<HTMLDivElement>, target: string, items: Instrument[]) {
    event.preventDefault();
    if (!customMode || mode !== 'manual' || !draggingItem || draggingItem === target) return resetItemDrag();
    const current = items.map((item) => item.symbol).filter((symbol) => symbol !== draggingItem);
    const rect = event.currentTarget.getBoundingClientRect();
    const insertAt = current.indexOf(target) + (event.clientY > rect.top + rect.height / 2 ? 1 : 0);
    const next = [...current.slice(0, insertAt), draggingItem, ...current.slice(insertAt)];
    setOrder(next);
    window.localStorage.setItem(storage.order, JSON.stringify(next));
    resetItemDrag();
  }

  async function removeFromWatchlist(item: Instrument) {
    if (removing) return;
    setRemoving(item.id);
    setMessage('');
    try {
      const response = await api.updateInstrumentWatchlist(item.id, dashboard.selectedKol, false);
      setOrder((current) => {
        const next = current.filter((symbol) => symbol !== item.symbol);
        window.localStorage.setItem(storage.order, JSON.stringify(next));
        return next;
      });
      setMessage(response.message);
      dashboard.reload(dashboard.selected === item.symbol ? '' : dashboard.selected);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '移出自选表失败');
    } finally {
      setRemoving('');
    }
  }

  function renderOverlays() {
    return <>
      {directoryOpen ? <InstrumentDirectory
        instruments={dashboard.instruments}
        onChanged={dashboard.reload}
        onClose={() => setDirectoryOpen(false)}
        selected={dashboard.selected}
      /> : null}
      {addOpen ? <MobileWatchlistAdd
        kolId={dashboard.selectedKol}
        onAdded={(symbol) => dashboard.reload(symbol)}
        onClose={() => setAddOpen(false)}
      /> : null}
      {managing ? <InstrumentManager
        instrument={managing}
        instruments={dashboard.instruments}
        onChanged={(symbol) => { setManaging(null); dashboard.reload(symbol); }}
        onClose={() => setManaging(null)}
      /> : null}
    </>;
  }

  return {
    draggingItem, mode, order, storage,
    message, removing,
    setMode, resetItemDrag, dropItemOn,
    dragItemOver: (event: DragEvent<HTMLDivElement>) => customMode && mode === 'manual' && event.preventDefault(),
    startItemDrag: setDraggingItem, openDirectory: () => setDirectoryOpen(true), openAdd: () => setAddOpen(true),
    openManager: setManaging, removeFromWatchlist,
    renderOverlays,
  };
}
