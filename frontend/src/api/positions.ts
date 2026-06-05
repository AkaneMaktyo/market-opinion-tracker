import type { KolPosition } from '../positionTypes';
import { json } from './http';

export const positionApi = {
  positions: (kolId = '', includeClosed = false) => {
    const params = new URLSearchParams();
    if (kolId) params.set('kolId', kolId);
    if (includeClosed) params.set('includeClosed', 'true');
    const query = params.toString() ? `?${params}` : '';
    return json<KolPosition[]>(`/api/positions${query}`);
  },
  openPosition: (body: {
    kolId: string;
    symbol: string;
    name?: string;
    market?: string;
    sector?: string;
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
