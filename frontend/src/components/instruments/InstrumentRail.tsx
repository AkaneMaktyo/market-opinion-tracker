import { FolderTree } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import type { DragEvent } from 'react';
import { api } from '../../api/client';
import type { Instrument } from '../../types';
import { InstrumentDirectory } from './InstrumentDirectory';
import { InstrumentGroupList } from './InstrumentGroupList';
import { InstrumentManager } from './InstrumentManager';
import { InstrumentSortBar } from './InstrumentSortBar';
import { applyManualOrder, groupItems, mergeDefaultInstrument, parseMode, sortItems, type SortMode } from './instrumentList';

const ORDER_STORAGE_KEY = 'market-opinion-instrument-order';
const GROUP_ORDER_STORAGE_KEY = 'market-opinion-group-order';
const LEGACY_GROUP_ORDER_STORAGE_KEY = 'market-opinion-instrument-group-order';
const SORT_STORAGE_KEY = 'market-opinion-instrument-sort';
const COLLAPSED_GROUPS_KEY = 'market-opinion-collapsed-groups';
const DEFAULT_KOL = 'default';

interface Props {
  instruments: Instrument[]; selected: string; groups: string[]; kolId: string;
  onSelect: (symbol: string) => void; onChanged: (nextSelected?: string) => void;
}

export function InstrumentRail({ instruments, selected, groups, kolId, onSelect, onChanged }: Props) {
  const [order, setOrder] = useState<string[]>([]);
  const [groupOrder, setGroupOrder] = useState<string[]>([]);
  const [mode, setMode] = useState<SortMode>('manual');
  const [draggingItem, setDraggingItem] = useState('');
  const [draggingGroup, setDraggingGroup] = useState('');
  const [dropGroup, setDropGroup] = useState('');
  const [managing, setManaging] = useState<Instrument | null>(null);
  const [directoryOpen, setDirectoryOpen] = useState(false);
  const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set());
  const customMode = kolId === DEFAULT_KOL;
  const railItems = customMode ? applyManualOrder(mergeDefaultInstrument(instruments), order) : instruments;
  const grouped = useMemo(() => (customMode ? groupItems(sortItems(railItems, mode), groupOrder) : [{ group: '', items: railItems }]), [customMode, groupOrder, railItems, mode]);
  const instrumentMap = useMemo(() => new Map(instruments.map((item) => [item.symbol, item])), [instruments]);

  useEffect(() => {
    try {
      setOrder(JSON.parse(window.localStorage.getItem(ORDER_STORAGE_KEY) || '[]'));
      setGroupOrder(JSON.parse(
        window.localStorage.getItem(GROUP_ORDER_STORAGE_KEY)
          || window.localStorage.getItem(LEGACY_GROUP_ORDER_STORAGE_KEY)
          || '[]',
      ));
      setMode(parseMode(window.localStorage.getItem(SORT_STORAGE_KEY)));
      setCollapsedGroups(new Set(JSON.parse(window.localStorage.getItem(COLLAPSED_GROUPS_KEY) || '[]')));
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
        ...current.filter((group) => groups.includes(group)),
        ...groups.filter((group) => !current.includes(group)),
      ];
      if (next.length === current.length && next.every((group, index) => group === current[index])) {
        return current;
      }
      window.localStorage.setItem(GROUP_ORDER_STORAGE_KEY, JSON.stringify(next));
      return next;
    });
  }, [customMode, groups]);

  function toggleGroup(group: string) {
    setCollapsedGroups((current) => {
      const next = new Set(current);
      next.has(group) ? next.delete(group) : next.add(group);
      window.localStorage.setItem(COLLAPSED_GROUPS_KEY, JSON.stringify([...next]));
      return next;
    });
  }

  function resetItemDrag() {
    setDraggingItem('');
    setDropGroup('');
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

  async function moveItemToGroup(group: string) {
    const item = instrumentMap.get(draggingItem);
    if (!customMode) return resetItemDrag();
    if (!item || item.groupName === group) return resetItemDrag();
    try {
      await api.updateInstrumentGroup(item.id, group);
      onChanged(draggingItem);
    } finally {
      resetItemDrag();
    }
  }

  function dropGroupOn(event: DragEvent<HTMLButtonElement>, target: string) {
    event.preventDefault();
    if (!customMode || mode !== 'manual' || !draggingGroup || draggingGroup === target) return setDraggingGroup('');
    const current = grouped.map((entry) => entry.group).filter(Boolean);
    const withoutDragging = current.filter((group) => group !== draggingGroup);
    const rect = event.currentTarget.getBoundingClientRect();
    const insertAt = withoutDragging.indexOf(target) + (event.clientY > rect.top + rect.height / 2 ? 1 : 0);
    const next = [...withoutDragging.slice(0, insertAt), draggingGroup, ...withoutDragging.slice(insertAt)];
    setGroupOrder(next);
    window.localStorage.setItem(GROUP_ORDER_STORAGE_KEY, JSON.stringify(next));
    setDraggingGroup('');
  }

  function onGroupDrop(event: DragEvent<HTMLButtonElement>, group: string) {
    event.preventDefault();
    if (draggingItem) {
      void moveItemToGroup(group);
      return;
    }
    dropGroupOn(event, group);
  }

  return (
    <aside className="rail">
      <div className="rail-head">
        <div>
          <div className="panel-title">相关标的</div>
          <span className="rail-note">{customMode ? (mode === 'manual' ? '拖动排序' : '行情排序') : '最新观点排序'}</span>
        </div>
        <button className="rail-manage" onClick={() => setDirectoryOpen(true)} type="button">
          <FolderTree size={14} />
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
      <InstrumentGroupList
        collapsedGroups={collapsedGroups}
        draggingGroup={draggingGroup}
        draggingItem={draggingItem}
        dropGroup={dropGroup}
        grouped={grouped}
        manualMode={customMode && mode === 'manual'}
        onDragItemEnd={resetItemDrag}
        onDragItemOver={(event) => customMode && mode === 'manual' && event.preventDefault()}
        onDragItemStart={setDraggingItem}
        onDropGroup={onGroupDrop}
        onDropItem={dropItemOn}
        onManage={setManaging}
        onSelect={onSelect}
        onSetDraggingGroup={setDraggingGroup}
        onSetDropGroup={setDropGroup}
        onToggleGroup={toggleGroup}
        selected={selected}
      />
      {directoryOpen ? (
        <InstrumentDirectory
          groups={groups}
          instruments={instruments}
          onChanged={onChanged}
          onClose={() => setDirectoryOpen(false)}
          selected={selected}
        />
      ) : null}
      {managing ? (
        <InstrumentManager
          groups={groups}
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
