import type {
  ImportCandidate,
  ImportCommitResult,
  ImportPreview,
  Instrument,
  Kol,
  LiveSession,
  MarketBackfillStatus,
  MarketBar,
  OpinionView,
  PriceLevel,
  Timeframe,
} from '../types';
import { json } from './http';
import { priceAlertApi } from './alerts';
import { llmApi } from './llm';
import { positionApi } from './positions';
import { bitgetTradingApi } from './trading/bitget';
import { signalTradingApi } from './trading/signalTrading';
import { wxpusherApi } from './wxpusher';
import { youtubeApi } from './youtube';
import { celebrityApi } from './celebrity/celebrity';

async function fetchWatchlist(kolId: string, quotes = true) {
  const [history, current] = await Promise.all([
    api.instruments(kolId, 'history', quotes),
    api.instruments(kolId, 'current', quotes),
  ]);
  return [...new Map([...history, ...current].map((item) => [item.symbol, item])).values()];
}

export const api = {
  ...priceAlertApi,
  ...llmApi,
  ...wxpusherApi,
  ...youtubeApi,
  ...positionApi,
  ...signalTradingApi,
  ...bitgetTradingApi,
  ...celebrityApi,
  kols: () => json<Kol[]>('/api/kols'),
  createKol: (body: { name: string; description?: string }) =>
    json<Kol>('/api/kols', { method: 'POST', body: JSON.stringify(body) }),
  instruments: (
    kolId?: string,
    scope: 'history' | 'current' = 'history',
    quotes = true,
  ) => {
    const params = new URLSearchParams();
    if (kolId) params.set('kolId', kolId);
    if (scope) params.set('scope', scope);
    params.set('quotes', String(quotes));
    const query = params.toString() ? `?${params}` : '';
    return json<Instrument[]>(`/api/instruments${query}`);
  },
  watchlist: fetchWatchlist,
  createInstrument: (body: {
    symbol: string;
    name?: string;
    market?: string;
    sector?: string;
    kolId: string;
    addToWatchlist: boolean;
  }) => json<Instrument>('/api/instruments', {
    method: 'POST',
    body: JSON.stringify(body),
  }),
  sessions: (kolId?: string) => {
    const query = kolId ? `?kolId=${encodeURIComponent(kolId)}` : '';
    return json<LiveSession[]>(`/api/sessions${query}`);
  },
  session: (id: string) =>
    json<LiveSession>(`/api/sessions/${encodeURIComponent(id)}`),
  bars: (
    symbol: string,
    timeframe: Timeframe,
    options: { limit?: number; before?: string } = {},
  ) => {
    const params = new URLSearchParams({ timeframe });
    if (options.limit) params.set('limit', String(options.limit));
    if (options.before) params.set('before', options.before);
    return json<MarketBar[]>(`/api/market/${encodeURIComponent(symbol)}/bars?${params}`);
  },
  marketBackfill: () => json<MarketBackfillStatus>('/api/market/backfill'),
  startMarketBackfill: () =>
    json<MarketBackfillStatus>('/api/market/backfill', { method: 'POST' }),
  startSymbolMarketBackfill: (symbol: string) =>
    json<MarketBackfillStatus>(
      `/api/market/${encodeURIComponent(symbol)}/backfill`,
      { method: 'POST' },
    ),
  opinions: (kolId?: string, symbol?: string) => {
    const params = new URLSearchParams();
    if (kolId) params.set('kolId', kolId);
    if (symbol) params.set('symbol', symbol);
    const query = params.toString() ? `?${params}` : '';
    return json<OpinionView[]>(`/api/opinions${query}`);
  },
  createSession: (body: Partial<LiveSession>) =>
    json<LiveSession>('/api/sessions', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  createOpinion: (body: {
    sessionId: string;
    symbol: string;
    instrumentName?: string;
    market?: string;
    sector?: string;
    direction: string;
    positionAction?: string;
    horizon: string;
    thesis: string;
    triggerCondition?: string;
    invalidation?: string;
    confidence?: number;
    sourceQuote?: string;
    referencePrice?: number;
    opinionTime?: string;
    priceLevels: PriceLevel[];
  }) =>
    json<OpinionView>('/api/opinions', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  previewImport: (body: {
    kolId: string;
    title: string;
    sessionDate: string;
    rawJson: string;
  }) =>
    json<ImportPreview>('/api/json/preview', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  commitImport: (body: {
    kolId: string;
    title: string;
    sessionDate: string;
    rawJson: string;
    items: ImportCandidate[];
  }) =>
    json<ImportCommitResult>('/api/json/commit', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  review: (id: string, body: { outcome: string; notes?: string }) =>
    json<OpinionView>(`/api/opinions/${id}/review`, {
      method: 'PATCH',
      body: JSON.stringify({
        ...body,
        reviewDate: new Date().toISOString().slice(0, 10),
      }),
    }),
  renameInstrument: (
    id: string,
    body: { symbol: string; name?: string; logoUrl?: string | null },
  ) =>
    json<Instrument>(`/api/instruments/${id}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  mergeInstrument: (id: string, targetId: string) =>
    json<{ status: string; message: string }>(`/api/instruments/${id}/merge`, {
      method: 'POST',
      body: JSON.stringify({ targetId }),
    }),
  deleteInstrument: (id: string) =>
    json<{ status: string; message: string }>(`/api/instruments/${id}`, {
      method: 'DELETE',
    }),
  updateInstrumentWatchlist: (id: string, kolId: string, included: boolean) =>
    json<{ status: string; message: string }>(`/api/instruments/${id}/watchlist`, {
      method: 'PUT',
      body: JSON.stringify({ kolId, included }),
    }),
  updateInstrumentMarketProvider: (id: string, provider: string | null) =>
    json<Instrument>(`/api/instruments/${id}/market-provider`, {
      method: 'PUT',
      body: JSON.stringify({ provider }),
    }),
};
