export interface PriceAlert {
  id: string;
  instrumentId: string;
  symbol: string;
  name?: string;
  alertType: 'RANGE' | 'POINT';
  lowerPrice: number;
  upperPrice: number;
  targetPrice?: number;
  triggerDirection: PriceAlertTriggerDirection;
  status: 'ACTIVE' | 'DELIVERING' | 'TRIGGERED' | 'PAUSED' | 'ERROR';
  lastPrice?: number;
  lastCheckedAt?: string;
  triggeredAt?: string;
  notifyStatus: string;
  errorMessage?: string;
  sourceRecognitionId?: string;
  sourceCandidateId?: string;
  createdAt: string;
  updatedAt: string;
}

export type PriceAlertTriggerDirection = 'ANY' | 'UP' | 'DOWN';

export interface PriceAlertRecognitionCandidate {
  candidateId: string;
  instrumentName: string;
  symbol: string;
  market: string;
  alertType: 'RANGE' | 'POINT';
  lowerPrice?: number;
  upperPrice?: number;
  targetPrice?: number;
  triggerDirection: PriceAlertTriggerDirection;
  category: string;
  note?: string;
  sourceQuote: string;
  source: 'TEXT' | 'OCR';
  creationStatus?: string;
  creationMessage?: string;
}

export interface PriceAlertRecognitionResult {
  recognitionId: string;
  messageId: string;
  status: 'PROCESSING' | 'SUCCESS' | 'EMPTY' | 'FAILED';
  candidates: PriceAlertRecognitionCandidate[];
  warnings: string[];
  errorMessage?: string;
  updatedAt: string;
}

export interface PriceAlertBatchItem extends Pick<PriceAlertRecognitionCandidate,
  'candidateId' | 'instrumentName' | 'symbol' | 'market' | 'alertType' |
  'triggerDirection' | 'lowerPrice' | 'upperPrice' | 'targetPrice'> {}

export interface PriceAlertBatchItemResult {
  candidateId: string;
  status: 'CREATED' | 'EXISTS' | 'FAILED';
  alert?: PriceAlert;
  message: string;
}

export interface PriceAlertBatchResult {
  recognitionId: string;
  items: PriceAlertBatchItemResult[];
}

export interface PriceAlertMonitorStatus {
  state: 'IDLE' | 'CONNECTING' | 'LIVE' | 'POLLING' | 'RECONNECTING' | 'ERROR';
  activeAlerts: number;
  subscribedSymbols: number;
  lastMessageAt?: string;
  lastError?: string;
  pushReady: boolean;
}
