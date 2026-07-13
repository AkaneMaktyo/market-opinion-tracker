import type { Instrument } from '../../types';

const PREFIX = 'mot-instruments:';

export function pickSelectedSymbol(instruments: Instrument[], requested: string) {
  return requested && instruments.some((item) => item.symbol === requested)
    ? requested
    : instruments[0]?.symbol || '';
}

export function readInstrumentCache(kolId: string) {
  try {
    const raw = window.localStorage.getItem(PREFIX + kolId);
    return raw ? JSON.parse(raw) as Instrument[] : undefined;
  } catch {
    return undefined;
  }
}

export function writeInstrumentCache(kolId: string, instruments: Instrument[]) {
  try {
    window.localStorage.setItem(PREFIX + kolId, JSON.stringify(instruments));
  } catch {
    // 浏览器禁用存储时仍使用当前页面内存缓存。
  }
}
