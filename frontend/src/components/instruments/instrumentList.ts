import type { Instrument } from '../../types';

export type SortMode = 'manual' | 'gain' | 'loss';

export const sortOptions = [
  { value: 'manual' as const, label: '自定' },
  { value: 'gain' as const, label: '涨幅' },
  { value: 'loss' as const, label: '跌幅' },
];

export function groupItems(items: Instrument[], groupOrder: string[] = []) {
  const groups = new Map<string, Instrument[]>();
  const ungrouped: Instrument[] = [];
  items.forEach((item) => {
    if (!item.groupName) {
      ungrouped.push(item);
      return;
    }
    const list = groups.get(item.groupName) || [];
    list.push(item);
    groups.set(item.groupName, list);
  });
  const orderedGroups = [
    ...groupOrder.filter((group) => groups.has(group)),
    ...[...groups.keys()].filter((group) => !groupOrder.includes(group)).sort((left, right) => left.localeCompare(right)),
  ];
  return [
    ...orderedGroups.map((group) => ({ group, items: groups.get(group) || [] })),
    { group: '', items: ungrouped },
  ].filter((entry) => entry.items.length > 0);
}

export function mergeDefaultInstrument(instruments: Instrument[]) {
  return instruments.some((item) => item.symbol === 'NVDA')
    ? [...instruments]
    : [{ id: 'default-nvda', symbol: 'NVDA', name: '示例' }, ...instruments];
}

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
