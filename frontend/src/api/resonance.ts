import { json } from './http';

export interface ResonanceCluster {
  id: string;
  symbol: string;
  direction: string;
  horizon: string;
  score: number;
  grade: string;
  action: string;
  summary?: string;
  triggerText?: string;
  invalidationText?: string;
  riskText?: string;
  catalystText?: string;
  sourceCount: number;
  supportCount: number;
  conflictCount: number;
  sourceNames?: string;
  lastOpinionAt: string;
  alertStatus: string;
  alertError?: string;
  lastAlertAt?: string;
}

export interface ResonanceItem {
  opinionId: string;
  role: 'SUPPORT' | 'CONFLICT';
  sourceName: string;
  direction: string;
  horizon: string;
  thesis?: string;
  sourceQuote?: string;
  opinionTime: string;
}

export interface ResonanceView {
  cluster: ResonanceCluster;
  items: ResonanceItem[];
}

export const resonanceApi = {
  list: (symbol = '', limit = 8) => {
    const params = new URLSearchParams();
    if (symbol) params.set('symbol', symbol);
    params.set('limit', String(limit));
    return json<ResonanceView[]>(`/api/resonance?${params}`);
  },
  refresh: (symbol: string) =>
    json<ResonanceView[]>(
      `/api/resonance/refresh?symbol=${encodeURIComponent(symbol)}`,
      { method: 'POST' },
    ),
};
