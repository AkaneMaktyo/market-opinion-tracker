import type {
  PositionPortfolio,
  SignalTradePlan,
  SignalTradingStatus,
} from '../../types/trading';
import { json } from '../http';

export const signalTradingApi = {
  signalTradingStatus: () =>
    json<SignalTradingStatus>('/api/trading/signals/status'),
  signalTradePlans: () =>
    json<SignalTradePlan[]>('/api/trading/signals/plans'),
  saveSignalTradePlan: (
    alertId: string,
    body: { totalCost: number; batchCount: number },
  ) => json<SignalTradePlan>(
    `/api/trading/signals/alerts/${encodeURIComponent(alertId)}/plan`,
    { method: 'PUT', body: JSON.stringify(body) },
  ),
  spotPositions: (refresh = false) =>
    json<PositionPortfolio>(`/api/trading/signals/positions${refresh ? '?refresh=true' : ''}`),
  setPositionAverageCost: (provider: string, symbol: string, averageCost: number) =>
    json<PositionPortfolio>(
      `/api/trading/signals/positions/${encodeURIComponent(provider)}/${encodeURIComponent(symbol)}/cost`,
      { method: 'PUT', body: JSON.stringify({ averageCost }) },
    ),
  clearPositionAverageCost: (provider: string, symbol: string) =>
    json<PositionPortfolio>(
      `/api/trading/signals/positions/${encodeURIComponent(provider)}/${encodeURIComponent(symbol)}/cost`,
      { method: 'DELETE' },
    ),
};
