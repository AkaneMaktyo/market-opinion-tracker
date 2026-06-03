import { ChevronDown, ChevronRight, FolderTree, ListOrdered, TrendingDown, TrendingUp } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import type { DragEvent } from 'react';
import { api } from '../../api/client';
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
const GROUP_ORDER_STORAGE_KEY = 'market-opinion-group-order';
const LEGACY_GROUP_ORDER_STORAGE_KEY = 'market-opinion-instrument-group-order';
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
  const [groupOrder, setGroupOrder] = useState<string[]>([]);
  const [mode, setMode] = useState<SortMode>('manual');
  const [draggingItem, setDraggingItem] = useState('');
  const [draggingGroup, setDraggingGroup] = useState('');
  const [dropGroup, setDropGroup] = useState('');
  const [managing, setManaging] = useState<Instrument | null>(null);
  const [directoryOpen, setDirectoryOpen] = useState(false);
  const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set());
  const manualItems = applyManualOrder(mergeDefaultInstrument(instruments), order);
  const grouped = useMemo(
    () => groupItems(sortItems(manualItems, mode), groupOrder),
    [groupOrder, manualItems, mode],
  );
  const instrumentMap = useMemo(
    () => new Map(instruments.map((item) => [item.symbol, item])),
    [instruments],
  );

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
  }, [groups]);

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
    if (mode !== 'manual' || !draggingItem || draggingItem === target) return resetItemDrag();
    const current = manualItems.map((item) => item.symbol).filter((symbol) => symbol !== draggingItem);
    const rect = event.currentTarget.getBoundingClientRect();
    const insertAt = current.indexOf(target) + (event.clientY > rect.top + rect.height / 2 ? 1 : 0);
    const next = [...current.slice(0, insertAt), draggingItem, ...current.slice(insertAt)];
    setOrder(next);
    window.localStorage.setItem(ORDER_STORAGE_KEY, JSON.stringify(next));
    resetItemDrag();
  }

  async function moveItemToGroup(group: string) {
    const item = instrumentMap.get(draggingItem);
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
    if (mode !== 'manual' || !draggingGroup || draggingGroup === target) return setDraggingGroup('');
    const current = grouped.map((entry) => entry.group).filter(Boolean);
    const withoutDragging = current.filter((group) => group !== draggingGroup);
    const rect = event.currentTarget.getBoundingClientRect();
    const insertAt = withoutDragging.indexOf(target) + (event.clientY > rect.top + rect.height / 2 ? 1 : 0);
    const next = [
      ...withoutDragging.slice(0, insertAt),
      draggingGroup,
      ...withoutDragging.slice(insertAt),
    ];
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
          <div className="panel-title">品种</div>
          <span className="rail-note">{mode === 'manual' ? '拖动排序' : '行情排序'}</span>
        </div>
        <button className="rail-manage" onClick={() => setDirectoryOpen(true)} type="button">
          <FolderTree size={14} />
          <span>管理</span>
        </button>
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
      <div className="rail-table-head">
        <span>商品</span>
        <span>最新价</span>
        <span>涨跌</span>
        <span>涨跌%</span>
      </div>
      <div className="rail-list">
        {grouped.length === 0 ? (
          <div className="muted">当前 KOL 还没有已入库品种</div>
        ) : null}
        {grouped.map(({ group, items }) => (
          <div className="instrument-group" key={group || '_ungrouped'}>
            {group ? (
              <button
                className={groupHeaderClass(group, draggingGroup, dropGroup)}
                draggable={mode === 'manual'}
                onClick={() => toggleGroup(group)}
                onDragEnd={() => setDraggingGroup('')}
                onDragLeave={() => dropGroup === group && setDropGroup('')}
                onDragOver={(event) => {
                  if (mode !== 'manual' && !draggingItem) return;
                  event.preventDefault();
                  if (draggingItem && dropGroup !== group) setDropGroup(group);
                }}
                onDragStart={() => setDraggingGroup(group)}
                onDrop={(event) => onGroupDrop(event, group)}
                title={mode === 'manual' ? `拖动分组 ${group} 排序` : undefined}
                type="button"
              >
                <span>{collapsedGroups.has(group) ? <ChevronRight size={14} /> : <ChevronDown size={14} />}</span>
                <span>{group}</span>
                <span className="group-count">{items.length}</span>
              </button>
            ) : null}
            {!group || !collapsedGroups.has(group) ? items.map((item) => (
              <InstrumentCard
                dragEnabled
                dragging={draggingItem}
                item={item}
                key={item.id}
                manualMode={mode === 'manual'}
                onDragEnd={resetItemDrag}
                onDragOver={(event) => mode === 'manual' && event.preventDefault()}
                onDragStart={setDraggingItem}
                onDrop={dropItemOn}
                onManage={setManaging}
                onSelect={onSelect}
                selected={selected}
              />
            )) : null}
          </div>
        ))}
      </div>
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

function groupHeaderClass(group: string, draggingGroup: string, dropGroup: string) {
  return [
    'group-header',
    draggingGroup === group ? 'dragging' : '',
    dropGroup === group ? 'drop-target' : '',
  ]
    .filter(Boolean)
    .join(' ');
}
