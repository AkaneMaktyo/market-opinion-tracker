import type {
  ImportCandidate,
  ImportCommitResult,
  ImportPreview,
  Instrument,
  Kol,
  LiveSession,
  MarketBar,
  OpinionView,
  PriceLevel,
} from '../types';

async function json<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  if (!response.ok) {
    throw new Error(await response.text());
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
  bars: (symbol: string) => json<MarketBar[]>(`/api/market/${symbol}/bars?timeframe=1D`),
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
    json<ImportPreview>('/api/imports/preview', {
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
    json<ImportCommitResult>('/api/imports/commit', {
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
};
