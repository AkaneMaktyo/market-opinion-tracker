import type { DragEvent } from 'react';
import type { Instrument } from '../../types';
import { InstrumentCard } from './InstrumentCard';

interface Props {
  draggingItem: string;
  items: Instrument[];
  manualMode: boolean;
  selected: string;
  removingId?: string;
  onDragItemEnd: () => void;
  onDragItemOver: (event: DragEvent<HTMLDivElement>) => void;
  onDragItemStart: (symbol: string) => void;
  onDropItem: (event: DragEvent<HTMLDivElement>, symbol: string) => void;
  onManage: (item: Instrument) => void;
  onRemove?: (item: Instrument) => void;
  onSelect: (symbol: string) => void;
}

export function FlatInstrumentList({
  draggingItem,
  items,
  manualMode,
  selected,
  removingId,
  onDragItemEnd,
  onDragItemOver,
  onDragItemStart,
  onDropItem,
  onManage,
  onRemove,
  onSelect,
}: Props) {
  return (
    <div className="rail-list">
      {items.length === 0 ? <div className="muted">当前 KOL 还没有相关标的</div> : null}
      {items.map((item) => (
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
          removing={removingId === item.id}
          selected={selected}
        />
      ))}
    </div>
  );
}
