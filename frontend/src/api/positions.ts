import type { KolPosition, KolPositionTrade, PositionStats, PositionView } from '../positionTypes';
import { json } from './http';

export interface RebuildResult {
  kolId: string;
  sourceInclude?: string | null;
  scannedOpinions: number;
  totalTrades: number;
  settledTrades: number;
  runningTrades: number;
  removedPositions: number;
}

export const positionApi = {
  positions: (kolId = '', includeClosed = false): Promise<PositionView[]> => {
    const params = new URLSearchParams();
    if (kolId) params.set('kolId', kolId);
    if (includeClosed) params.set('includeClosed', 'true');
    const query = params.toString() ? `?${params}` : '';
    return json<PositionView[]>(`/api/positions${query}`);
  },
  positionStats: (kolId = ''): Promise<PositionStats> => {
    const query = kolId ? `?kolId=${encodeURIComponent(kolId)}` : '';
    return json<PositionStats>(`/api/positions/stats${query}`);
  },
  positionTrades: (kolId = '', limit = 200): Promise<KolPositionTrade[]> => {
    const params = new URLSearchParams();
    if (kolId) params.set('kolId', kolId);
    params.set('limit', String(limit));
    return json<KolPositionTrade[]>(`/api/positions/trades?${params.toString()}`);
  },
  rebuildPositionTrades: (kolId = ''): Promise<RebuildResult> =>
    json<RebuildResult>(`/api/positions/rebuild?kolId=${encodeURIComponent(kolId)}`, {
      method: 'POST',
    }),
  openPosition: (body: {
    kolId: string;
    symbol: string;
    name?: string;
    market?: string;
    sector?: string;
    direction?: string;
    entryPrice?: number;
  }) =>
    json<KolPosition>('/api/positions', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  closePosition: (id: string) =>
    json<KolPosition>(`/api/positions/${encodeURIComponent(id)}/close`, {
      method: 'POST',
    }),
};
