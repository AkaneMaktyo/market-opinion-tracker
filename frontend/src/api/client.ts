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
import { positionApi } from './positions';
import { wxpusherApi } from './wxpusher';

export const api = {
  ...wxpusherApi,
  ...positionApi,
  kols: () => json<Kol[]>('/api/kols'),
  createKol: (body: { name: string; description?: string }) =>
    json<Kol>('/api/kols', { method: 'POST', body: JSON.stringify(body) }),
  instruments: (kolId?: string, scope: 'history' | 'current' = 'history') => {
    const params = new URLSearchParams();
    if (kolId) params.set('kolId', kolId);
    if (scope) params.set('scope', scope);
    const query = params.toString() ? `?${params}` : '';
    return json<Instrument[]>(`/api/instruments${query}`);
  },
  sessions: (kolId?: string) => {
    const query = kolId ? `?kolId=${encodeURIComponent(kolId)}` : '';
    return json<LiveSession[]>(`/api/sessions${query}`);
  },
  bars: (symbol: string, timeframe: Timeframe) =>
    json<MarketBar[]>(`/api/market/${symbol}/bars?timeframe=${timeframe}`),
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
  updateInstrumentGroup: (id: string, groupName: string | null) =>
    json<Instrument>(`/api/instruments/${id}/group`, {
      method: 'PUT',
      body: JSON.stringify({ groupName }),
    }),
  updateInstrumentMarketProvider: (id: string, provider: string | null) =>
    json<Instrument>(`/api/instruments/${id}/market-provider`, {
      method: 'PUT',
      body: JSON.stringify({ provider }),
    }),
  instrumentGroups: () => json<string[]>('/api/instruments/groups'),
};
