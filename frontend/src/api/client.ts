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

const apiBase = (
  import.meta.env.VITE_API_BASE_URL ||
  `${import.meta.env.BASE_URL.replace(/\/$/, '')}/api`
).replace(/\/$/, '');

function normalizeUrl(url: string): string {
  return url.startsWith('/api') ? `${apiBase}${url.slice(4)}` : url;
}

async function json<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(normalizeUrl(url), {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!response.ok) {
    const text = await response.text();
    let message = text;
    try {
      const payload = JSON.parse(text) as { message?: string };
      message = payload.message || text;
    } catch {
      message = text;
    }
    throw new Error(message);
  }
  return response.json() as Promise<T>;
}

export const api = {
  kols: () => json<Kol[]>('/api/kols'),
  createKol: (body: { name: string; description?: string }) =>
    json<Kol>('/api/kols', { method: 'POST', body: JSON.stringify(body) }),
  instruments: (kolId?: string) => {
    const query = kolId ? `?kolId=${encodeURIComponent(kolId)}` : '';
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
    sector?: string;
    direction: string;
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
