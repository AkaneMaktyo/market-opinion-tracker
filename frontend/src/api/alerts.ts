import { json } from './http';
import type { PriceAlert, PriceAlertMonitorStatus } from '../types/alerts';

export const priceAlertApi = {
  priceAlerts: () => json<PriceAlert[]>('/api/price-alerts'),
  priceAlertStatus: () => json<PriceAlertMonitorStatus>('/api/price-alerts/status'),
  createPriceAlert: (body: PriceAlertDraft) =>
    json<PriceAlert>('/api/price-alerts', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updatePriceAlert: (id: string, body: PriceAlertDraft) =>
    json<PriceAlert>(`/api/price-alerts/${encodeURIComponent(id)}`, {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  setPriceAlertEnabled: (id: string, enabled: boolean) =>
    json<PriceAlert>(`/api/price-alerts/${encodeURIComponent(id)}/active`, {
      method: 'PUT',
      body: JSON.stringify({ enabled }),
    }),
  deletePriceAlert: (id: string) =>
    json<{ status: string }>(`/api/price-alerts/${encodeURIComponent(id)}`, {
      method: 'DELETE',
    }),
};

export interface PriceAlertDraft {
  symbol: string;
  alertType: 'RANGE' | 'POINT';
  lowerPrice?: number;
  upperPrice?: number;
  targetPrice?: number;
}
