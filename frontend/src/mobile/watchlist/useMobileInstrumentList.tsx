import { useEffect, useMemo, useState } from 'react';
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
  groupOrder: 'market-opinion-group-order',
  legacyGroupOrder: 'market-opinion-instrument-group-order',
  sort: 'market-opinion-instrument-sort',
  collapsed: 'market-opinion-collapsed-groups',
};

export function useMobileInstrumentList(dashboard: DashboardModel, customMode: boolean) {
  const [order, setOrder] = useState<string[]>([]);
  const [groupOrder, setGroupOrder] = useState<string[]>([]);
  const [mode, setMode] = useState<SortMode>('manual');
  const [draggingItem, setDraggingItem] = useState('');
  const [draggingGroup, setDraggingGroup] = useState('');
  const [dropGroup, setDropGroup] = useState('');
  const [managing, setManaging] = useState<Instrument | null>(null);
  const [directoryOpen, setDirectoryOpen] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [removing, setRemoving] = useState('');
  const [message, setMessage] = useState('');
  const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set());
  const instrumentMap = useMemo(
    () => new Map(dashboard.instruments.map((item) => [item.symbol, item])),
    [dashboard.instruments],
  );

  useEffect(() => {
    try {
      setOrder(JSON.parse(window.localStorage.getItem(storage.order) || '[]'));
      setGroupOrder(JSON.parse(window.localStorage.getItem(storage.groupOrder)
        || window.localStorage.getItem(storage.legacyGroupOrder) || '[]'));
      setMode(parseMode(window.localStorage.getItem(storage.sort)));
      setCollapsedGroups(new Set(JSON.parse(window.localStorage.getItem(storage.collapsed) || '[]')));
    } catch {
      setOrder([]);
      setGroupOrder([]);
      setMode('manual');
      setCollapsedGroups(new Set());
    }
  }, []);

  useEffect(() => {
    if (!customMode) return;
    setGroupOrder((current) => {
      const next = [
        ...current.filter((group) => dashboard.instrumentGroups.includes(group)),
        ...dashboard.instrumentGroups.filter((group) => !current.includes(group)),
      ];
      if (next.length === current.length && next.every((group, index) => group === current[index])) return current;
      window.localStorage.setItem(storage.groupOrder, JSON.stringify(next));
      return next;
    });
  }, [customMode, dashboard.instrumentGroups]);

  function toggleGroup(group: string) {
    setCollapsedGroups((current) => {
      const next = new Set(current);
      next.has(group) ? next.delete(group) : next.add(group);
      window.localStorage.setItem(storage.collapsed, JSON.stringify([...next]));
      return next;
    });
  }

  function resetItemDrag() {
    setDraggingItem('');
    setDropGroup('');
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

  async function moveItemToGroup(group: string) {
    const item = instrumentMap.get(draggingItem);
    if (!customMode || !item || item.groupName === group) return resetItemDrag();
    try {
      await api.updateInstrumentGroup(item.id, dashboard.selectedKol, group);
      dashboard.reload(draggingItem);
    } finally {
      resetItemDrag();
    }
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

  function dropGroupOn(event: DragEvent<HTMLButtonElement>, target: string) {
    event.preventDefault();
    if (draggingItem) {
      void moveItemToGroup(target);
      return;
    }
    if (!customMode || mode !== 'manual' || !draggingGroup || draggingGroup === target) return setDraggingGroup('');
    const current = dashboard.instrumentGroups.filter((group) => group !== draggingGroup);
    const rect = event.currentTarget.getBoundingClientRect();
    const insertAt = current.indexOf(target) + (event.clientY > rect.top + rect.height / 2 ? 1 : 0);
    const next = [...current.slice(0, insertAt), draggingGroup, ...current.slice(insertAt)];
    setGroupOrder(next);
    window.localStorage.setItem(storage.groupOrder, JSON.stringify(next));
    setDraggingGroup('');
  }

  function renderOverlays() {
    return <>
      {directoryOpen ? <InstrumentDirectory
        groups={dashboard.instrumentGroups}
        instruments={dashboard.instruments}
        kolId={dashboard.selectedKol}
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
        groups={dashboard.instrumentGroups}
        instrument={managing}
        instruments={dashboard.instruments}
        kolId={dashboard.selectedKol}
        onChanged={(symbol) => { setManaging(null); dashboard.reload(symbol); }}
        onClose={() => setManaging(null)}
      /> : null}
    </>;
  }

  return {
    collapsedGroups, draggingGroup, draggingItem, dropGroup, groupOrder, mode, order, storage,
    message, removing,
    setDraggingGroup, setDropGroup, setMode, toggleGroup, resetItemDrag, dropItemOn, dropGroupOn,
    dragItemOver: (event: DragEvent<HTMLDivElement>) => customMode && mode === 'manual' && event.preventDefault(),
    startItemDrag: setDraggingItem, openDirectory: () => setDirectoryOpen(true), openAdd: () => setAddOpen(true),
    openManager: setManaging, removeFromWatchlist,
    renderOverlays,
  };
}
