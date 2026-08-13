import { ChevronDown, ChevronRight } from 'lucide-react';
import type { DragEvent } from 'react';
import type { Instrument } from '../../types';
import { InstrumentCard } from './InstrumentCard';

interface Props {
  collapsedGroups: Set<string>;
  draggingGroup: string;
  draggingItem: string;
  dropGroup: string;
  grouped: { group: string; items: Instrument[] }[];
  manualMode: boolean;
  selected: string;
  removingId?: string;
  onDragItemEnd: () => void;
  onDragItemOver: (event: DragEvent<HTMLDivElement>) => void;
  onDragItemStart: (symbol: string) => void;
  onDropGroup: (event: DragEvent<HTMLButtonElement>, group: string) => void;
  onDropItem: (event: DragEvent<HTMLDivElement>, symbol: string) => void;
  onManage: (item: Instrument) => void;
  onRemove?: (item: Instrument) => void;
  onSelect: (symbol: string) => void;
  onSetDraggingGroup: (group: string) => void;
  onSetDropGroup: (group: string) => void;
  onToggleGroup: (group: string) => void;
}

export function InstrumentGroupList(props: Props) {
  const {
    collapsedGroups,
    draggingGroup,
    draggingItem,
    dropGroup,
    grouped,
    manualMode,
    selected,
    removingId,
    onDragItemEnd,
    onDragItemOver,
    onDragItemStart,
    onDropGroup,
    onDropItem,
    onManage,
    onRemove,
    onSelect,
    onSetDraggingGroup,
    onSetDropGroup,
    onToggleGroup,
  } = props;

  return (
    <div className="rail-list">
      {grouped.length === 0 ? <div className="muted">当前 KOL 还没有相关标的</div> : null}
      {grouped.map(({ group, items }) => (
        <div className="instrument-group" key={group || '_ungrouped'}>
          {group ? (
            <GroupHeader
              collapsed={collapsedGroups.has(group)}
              draggingGroup={draggingGroup}
              draggingItem={draggingItem}
              dropGroup={dropGroup}
              group={group}
              itemCount={items.length}
              manualMode={manualMode}
              onDropGroup={onDropGroup}
              onSetDraggingGroup={onSetDraggingGroup}
              onSetDropGroup={onSetDropGroup}
              onToggleGroup={onToggleGroup}
            />
          ) : null}
          {!group || !collapsedGroups.has(group)
            ? items.map((item) => (
              <InstrumentCard
                dragEnabled={manualMode}
                dragging={draggingItem}
                item={item}
                key={item.id}
                manualMode={manualMode}
                onDragEnd={onDragItemEnd}
                onDragOver={onDragItemOver}
                onDragStart={onDragItemStart}
                onDrop={onDropItem}
                onManage={onManage}
                onRemove={onRemove}
                onSelect={onSelect}
                selected={selected}
                removing={removingId === item.id}
              />
            ))
            : null}
        </div>
      ))}
    </div>
  );
}

function GroupHeader({
  collapsed,
  draggingGroup,
  draggingItem,
  dropGroup,
  group,
  itemCount,
  manualMode,
  onDropGroup,
  onSetDraggingGroup,
  onSetDropGroup,
  onToggleGroup,
}: {
  collapsed: boolean;
  draggingGroup: string;
  draggingItem: string;
  dropGroup: string;
  group: string;
  itemCount: number;
  manualMode: boolean;
  onDropGroup: (event: DragEvent<HTMLButtonElement>, group: string) => void;
  onSetDraggingGroup: (group: string) => void;
  onSetDropGroup: (group: string) => void;
  onToggleGroup: (group: string) => void;
}) {
  return (
    <button
      className={groupHeaderClass(group, draggingGroup, dropGroup)}
      draggable={manualMode}
      onClick={() => onToggleGroup(group)}
      onDragEnd={() => onSetDraggingGroup('')}
      onDragLeave={() => dropGroup === group && onSetDropGroup('')}
      onDragOver={(event) => {
        if (!manualMode && !draggingItem) return;
        event.preventDefault();
        if (draggingItem && dropGroup !== group) onSetDropGroup(group);
      }}
      onDragStart={() => onSetDraggingGroup(group)}
      onDrop={(event) => onDropGroup(event, group)}
      title={manualMode ? `拖动分组 ${group} 排序` : undefined}
      type="button"
    >
      <span>{collapsed ? <ChevronRight size={14} /> : <ChevronDown size={14} />}</span>
      <span>{group}</span>
      <span className="group-count">{itemCount}</span>
    </button>
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
