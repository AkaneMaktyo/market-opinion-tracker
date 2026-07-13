export type Timeframe = '1H' | '4H' | '1D';

export type ChartLiveStatus =
  | 'connecting'
  | 'live'
  | 'polling'
  | 'reconnecting'
  | 'delayed';

export interface MarketBar {
  id: string;
  instrumentId: string;
  timeframe: string;
  barTime: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface MarketBackfillStatus {
  state: 'IDLE' | 'RUNNING' | 'DONE' | 'FAILED';
  total: number;
  processed: number;
  success: number;
  skipped: number;
  failed: number;
  fetchedBars: number;
  message: string;
  scope?: 'ALL' | 'SYMBOL';
  symbol?: string;
  startedAt?: string;
  finishedAt?: string;
}
