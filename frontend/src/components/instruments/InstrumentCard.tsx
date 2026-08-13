import { Settings, Trash2 } from 'lucide-react';
import type { DragEvent, KeyboardEvent } from 'react';
import type { Instrument } from '../../types';
import { InstrumentLogo } from './InstrumentLogo';

interface Props {
  dragging: string;
  dragEnabled: boolean;
  item: Instrument;
  manualMode: boolean;
  selected: string;
  onManage: (item: Instrument) => void;
  onRemove?: (item: Instrument) => void;
  removing?: boolean;
  onSelect: (symbol: string) => void;
  onDragEnd: () => void;
  onDragOver: (event: DragEvent<HTMLDivElement>) => void;
  onDragStart: (symbol: string) => void;
  onDrop: (event: DragEvent<HTMLDivElement>, symbol: string) => void;
}

export function InstrumentCard({
  dragging,
  dragEnabled,
  item,
  manualMode,
  selected,
  onManage,
  onRemove,
  removing = false,
  onSelect,
  onDragEnd,
  onDragOver,
  onDragStart,
  onDrop,
}: Props) {
  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    event.preventDefault();
    onSelect(item.symbol);
  }

  const tone = item.dayChangePct == null ? 'no-data' : item.dayChangePct > 0 ? 'up' : item.dayChangePct < 0 ? 'down' : 'flat';

  return (
    <div
      className={symbolClass(item.symbol, selected, dragging, manualMode)}
      draggable={dragEnabled}
      onClick={() => onSelect(item.symbol)}
      onDragEnd={onDragEnd}
      onDragOver={onDragOver}
      onDragStart={() => onDragStart(item.symbol)}
      onDrop={(event) => onDrop(event, item.symbol)}
      onKeyDown={onKeyDown}
      role="button"
      tabIndex={0}
    >
      <span className="symbol-product">
        <InstrumentLogo symbol={item.symbol} logoUrl={item.logoUrl} />
        <span className="symbol-product-text">{productText(item)}</span>
      </span>
      <span className="symbol-value">{formatClose(item.dayClose)}</span>
      <span className={`symbol-value symbol-move ${tone}`}>{formatDelta(item.dayClose, item.dayChangePct)}</span>
      <span className={`symbol-value symbol-percent ${tone}`}>{formatPercent(item.dayChangePct)}</span>
      {onRemove ? (
        <button
          aria-label={`将 ${item.symbol} 移出自选表`}
          className="symbol-remove"
          disabled={removing}
          onClick={(event) => {
            event.stopPropagation();
            onRemove(item);
          }}
          title={`将 ${item.symbol} 移出自选表`}
          type="button"
        >
          <Trash2 size={14} />
        </button>
      ) : null}
      {!onRemove ? <button
        aria-label={`管理 ${item.symbol}`}
        className="symbol-manage"
        onClick={(event) => {
          event.stopPropagation();
          onManage(item);
        }}
        title={`管理 ${item.symbol}`}
        type="button"
      >
        <Settings size={12} />
      </button> : null}
    </div>
  );
}

function formatClose(value?: number) {
  return value == null ? '--' : value.toLocaleString('en-US', { maximumFractionDigits: 2, minimumFractionDigits: 2 });
}

function formatDelta(close?: number, pct?: number) {
  if (close == null || pct == null) return '--';
  if (pct <= -100) return '0.00';
  const delta = close - close / (1 + pct / 100);
  return delta.toLocaleString('en-US', { maximumFractionDigits: 2, minimumFractionDigits: 2 });
}

function formatPercent(value?: number) {
  return value == null ? '--' : `${value.toFixed(2)}%`;
}

function productText(item: Instrument) {
  return item.symbol;
}

function symbolClass(symbol: string, selected: string, dragging: string, manualMode: boolean) {
  return [
    'symbol',
    selected === symbol ? 'active' : '',
    dragging === symbol ? 'dragging' : '',
    manualMode ? 'manual' : 'movable',
  ]
    .filter(Boolean)
    .join(' ');
}
