import { ChevronDown, ChevronRight, FolderTree, ListOrdered, TrendingDown, TrendingUp } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import type { DragEvent } from 'react';
import type { Instrument } from '../../types';
import { InstrumentCard } from './InstrumentCard';
import { InstrumentDirectory } from './InstrumentDirectory';
import { InstrumentManager } from './InstrumentManager';
import {
  applyManualOrder,
  groupItems,
  mergeDefaultInstrument,
  parseMode,
  sortItems,
  sortOptions,
  type SortMode,
} from './instrumentList';

const ORDER_STORAGE_KEY = 'market-opinion-instrument-order';
const SORT_STORAGE_KEY = 'market-opinion-instrument-sort';
const COLLAPSED_GROUPS_KEY = 'market-opinion-collapsed-groups';

interface Props {
  instruments: Instrument[];
  selected: string;
  groups: string[];
  onSelect: (symbol: string) => void;
  onChanged: (nextSelected?: string) => void;
}

const sortIcons = { manual: ListOrdered, gain: TrendingUp, loss: TrendingDown };

export function InstrumentRail({ instruments, selected, groups, onSelect, onChanged }: Props) {
  const [order, setOrder] = useState<string[]>([]);
  const [mode, setMode] = useState<SortMode>('manual');
  const [dragging, setDragging] = useState('');
  const [managing, setManaging] = useState<Instrument | null>(null);
  const [directoryOpen, setDirectoryOpen] = useState(false);
  const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set());
  const manualItems = applyManualOrder(mergeDefaultInstrument(instruments), order);
  const grouped = useMemo(() => groupItems(sortItems(manualItems, mode)), [manualItems, mode]);

  useEffect(() => {
    try {
      setOrder(JSON.parse(window.localStorage.getItem(ORDER_STORAGE_KEY) || '[]'));
      setMode(parseMode(window.localStorage.getItem(SORT_STORAGE_KEY)));
      setCollapsedGroups(new Set(JSON.parse(window.localStorage.getItem(COLLAPSED_GROUPS_KEY) || '[]')));
    } catch {
      setOrder([]);
      setMode('manual');
      setCollapsedGroups(new Set());
    }
  }, []);

  function toggleGroup(group: string) {
    setCollapsedGroups((current) => {
      const next = new Set(current);
      next.has(group) ? next.delete(group) : next.add(group);
      window.localStorage.setItem(COLLAPSED_GROUPS_KEY, JSON.stringify([...next]));
      return next;
    });
  }

  function dropOn(event: DragEvent<HTMLButtonElement>, target: string) {
    event.preventDefault();
    if (mode !== 'manual' || !dragging || dragging === target) return setDragging('');
    const current = manualItems.map((item) => item.symbol).filter((symbol) => symbol !== dragging);
    const rect = event.currentTarget.getBoundingClientRect();
    const insertAt = current.indexOf(target) + (event.clientY > rect.top + rect.height / 2 ? 1 : 0);
    const next = [...current.slice(0, insertAt), dragging, ...current.slice(insertAt)];
    setOrder(next);
    window.localStorage.setItem(ORDER_STORAGE_KEY, JSON.stringify(next));
    setDragging('');
  }

  return (
    <aside className="rail">
      <div className="rail-head">
        <div><div className="panel-title">品种</div><span className="rail-note">{mode === 'manual' ? '拖动排序' : '行情排序'}</span></div>
        <button className="rail-manage" onClick={() => setDirectoryOpen(true)} type="button"><FolderTree size={14} /><span>管理</span></button>
      </div>
      <div className="rail-sort">
        {sortOptions.map((option) => {
          const Icon = sortIcons[option.value];
          return (
            <button
              className={mode === option.value ? 'sort-button active' : 'sort-button'}
              key={option.value}
              onClick={() => {
                setMode(option.value);
                window.localStorage.setItem(SORT_STORAGE_KEY, option.value);
              }}
              type="button"
            >
              <Icon size={14} />
              <span>{option.label}</span>
            </button>
          );
        })}
      </div>
      <div className="rail-list">
        {grouped.map(({ group, items }) => (
          <div className="instrument-group" key={group || '_ungrouped'}>
            {group ? <button className="group-header" onClick={() => toggleGroup(group)} type="button">
              {collapsedGroups.has(group) ? <ChevronRight size={14} /> : <ChevronDown size={14} />}
              <span>{group}</span><span className="group-count">{items.length}</span>
            </button> : null}
            {!group || !collapsedGroups.has(group) ? items.map((item) => (
              <InstrumentCard
                dragging={dragging}
                item={item}
                key={item.id}
                manualMode={mode === 'manual'}
                onDragEnd={() => setDragging('')}
                onDragOver={(event) => mode === 'manual' && event.preventDefault()}
                onDragStart={setDragging}
                onDrop={dropOn}
                onManage={setManaging}
                onSelect={onSelect}
                selected={selected}
              />
            )) : null}
          </div>
        ))}
      </div>
      {directoryOpen ? <InstrumentDirectory
        groups={groups}
        instruments={instruments}
        onChanged={onChanged}
        onClose={() => setDirectoryOpen(false)}
        selected={selected}
      /> : null}
      {managing ? <InstrumentManager
        groups={groups}
        instrument={managing}
        instruments={instruments}
        onChanged={(nextSelected) => {
          setManaging(null);
          onChanged(nextSelected);
        }}
        onClose={() => setManaging(null)}
      /> : null}
    </aside>
  );
}
