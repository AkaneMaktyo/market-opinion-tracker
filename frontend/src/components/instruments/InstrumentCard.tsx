import { GripVertical, Settings } from 'lucide-react';
import type { DragEvent } from 'react';
import type { Instrument } from '../../types';
import { InstrumentLogo } from './InstrumentLogo';

interface Props {
  dragging: string;
  item: Instrument;
  manualMode: boolean;
  selected: string;
  onManage: (item: Instrument) => void;
  onSelect: (symbol: string) => void;
  onDragEnd: () => void;
  onDragOver: (event: DragEvent<HTMLButtonElement>) => void;
  onDragStart: (symbol: string) => void;
  onDrop: (event: DragEvent<HTMLButtonElement>, symbol: string) => void;
}

export function InstrumentCard({
  dragging,
  item,
  manualMode,
  selected,
  onManage,
  onSelect,
  onDragEnd,
  onDragOver,
  onDragStart,
  onDrop,
}: Props) {
  return (
    <button
      className={symbolClass(item.symbol, selected, dragging)}
      draggable={manualMode}
      onClick={() => onSelect(item.symbol)}
      onDragEnd={onDragEnd}
      onDragOver={onDragOver}
      onDragStart={() => onDragStart(item.symbol)}
      onDrop={(event) => onDrop(event, item.symbol)}
      type="button"
    >
      <div className="symbol-top">
        <span className="symbol-title">
          {manualMode && <GripVertical className="symbol-grip" size={14} />}
          <InstrumentLogo symbol={item.symbol} logoUrl={item.logoUrl} />
          <strong>{item.symbol}</strong>
        </span>
        <span className="symbol-actions">
          {item.dayChangePct == null ? null : (
            <span className="symbol-change" style={changeStyle(item.dayChangePct)}>
              {changeText(item.dayChangePct)}
            </span>
          )}
          <button
            className="symbol-manage"
            onClick={(event) => {
              event.stopPropagation();
              onManage(item);
            }}
            title={`管理 ${item.symbol}`}
            type="button"
          >
            <Settings size={12} />
          </button>
        </span>
      </div>
      <span className="symbol-name">{item.name || item.sector || 'US'}</span>
      <QuoteRow item={item} />
    </button>
  );
}

function QuoteRow({ item }: { item: Instrument }) {
  if (item.dayClose == null || item.dayChangePct == null) {
    return <span className="symbol-meta">暂无数据</span>;
  }
  const tone = item.dayChangePct > 0 ? 'up' : item.dayChangePct < 0 ? 'down' : 'flat';
  return (
    <div className={`symbol-quotes ${tone}`}>
      <span>{formatClose(item.dayClose)}</span>
      <span>{formatDelta(item.dayClose, item.dayChangePct)}</span>
    </div>
  );
}

function formatClose(value: number) {
  return value.toLocaleString('en-US', { maximumFractionDigits: 2, minimumFractionDigits: 2 });
}

function formatDelta(close: number, pct: number) {
  if (pct <= -100) return '0.00';
  const delta = close - close / (1 + pct / 100);
  return delta.toLocaleString('en-US', { maximumFractionDigits: 2, minimumFractionDigits: 2 });
}

function changeText(value: number) {
  return `${value > 0 ? '+' : ''}${value.toFixed(2)}%`;
}

function changeStyle(value: number) {
  if (value === 0) return { backgroundColor: 'rgba(148, 163, 184, 0.18)', color: '#475569' };
  const alpha = 0.14 + (Math.min(Math.abs(value), 10) / 10) * 0.48;
  return value > 0
    ? { backgroundColor: `rgba(22, 163, 74, ${alpha})`, color: '#166534' }
    : { backgroundColor: `rgba(220, 38, 38, ${alpha})`, color: '#991b1b' };
}

function symbolClass(symbol: string, selected: string, dragging: string) {
  return ['symbol', selected === symbol ? 'active' : '', dragging === symbol ? 'dragging' : '']
    .filter(Boolean)
    .join(' ');
}
