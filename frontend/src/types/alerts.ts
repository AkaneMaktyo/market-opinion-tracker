export interface PriceAlert {
  id: string;
  instrumentId: string;
  symbol: string;
  name?: string;
  alertType: 'RANGE' | 'POINT';
  lowerPrice: number;
  upperPrice: number;
  targetPrice?: number;
  status: 'ACTIVE' | 'DELIVERING' | 'TRIGGERED' | 'PAUSED' | 'ERROR';
  lastPrice?: number;
  lastCheckedAt?: string;
  triggeredAt?: string;
  notifyStatus: string;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface PriceAlertMonitorStatus {
  state: 'IDLE' | 'CONNECTING' | 'LIVE' | 'POLLING' | 'RECONNECTING' | 'ERROR';
  activeAlerts: number;
  subscribedSymbols: number;
  lastMessageAt?: string;
  lastError?: string;
  pushReady: boolean;
}
