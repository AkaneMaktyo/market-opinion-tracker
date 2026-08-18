import type { Instrument } from '../../types';

export type SortMode = 'manual' | 'gain' | 'loss';

export const sortOptions = [
  { value: 'manual' as const, label: '自定' },
  { value: 'gain' as const, label: '涨幅' },
  { value: 'loss' as const, label: '跌幅' },
];

export function applyManualOrder(instruments: Instrument[], order: string[]) {
  const bySymbol = new Map(instruments.map((item) => [item.symbol, item]));
  const ordered = order.flatMap((symbol) => (bySymbol.get(symbol) ? [bySymbol.get(symbol)!] : []));
  const used = new Set(ordered.map((item) => item.symbol));
  return [...ordered, ...instruments.filter((item) => !used.has(item.symbol)).sort(compareSymbol)];
}

export function sortItems(items: Instrument[], mode: SortMode) {
  if (mode === 'manual') return items;
  return [...items].sort((left, right) => {
    if (left.dayChangePct == null && right.dayChangePct == null) return compareSymbol(left, right);
    if (left.dayChangePct == null) return 1;
    if (right.dayChangePct == null) return -1;
    return mode === 'gain' ? right.dayChangePct - left.dayChangePct : left.dayChangePct - right.dayChangePct;
  });
}

export function parseMode(value: string | null): SortMode {
  return value === 'gain' || value === 'loss' || value === 'manual' ? value : 'manual';
}

function compareSymbol(left: Instrument, right: Instrument) {
  return left.symbol.localeCompare(right.symbol);
}
