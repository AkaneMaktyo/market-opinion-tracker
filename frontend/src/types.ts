export type Direction = 'BULLISH' | 'BEARISH' | 'RANGE' | 'WATCH';
export type Timeframe = '1H' | '4H' | '1D';

export interface Instrument {
  id: string;
  symbol: string;
  name?: string;
  market?: string;
  sector?: string;
  groupName?: string;
  logoUrl?: string;
  createdAt?: string;
  dayClose?: number;
  dayChangePct?: number;
  dayBarTime?: string;
}

export interface Kol {
  id: string;
  name: string;
  description?: string;
  createdAt: string;
}

export interface LiveSession {
  id: string;
  kolId: string;
  sessionDate: string;
  title: string;
  source?: string;
  rawText: string;
}

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

export interface PriceLevel {
  id?: string;
  opinionId?: string;
  levelType: string;
  price: number;
  note?: string;
}

export interface Opinion {
  id: string;
  sessionId: string;
  instrumentId: string;
  symbol: string;
  direction: Direction;
  horizon: string;
  thesis: string;
  triggerCondition?: string;
  invalidation?: string;
  confidence?: number;
  sourceQuote?: string;
  referencePrice?: number;
  rawDirection?: string;
  risksText?: string;
  catalystsText?: string;
  priceNotesText?: string;
  rawItemJson?: string;
  opinionTime: string;
  status: string;
}

export interface Review {
  id: string;
  opinionId: string;
  outcome: string;
  notes?: string;
  resultPrice?: number;
  reviewDate: string;
}

export interface OpinionView {
  opinion: Opinion;
  priceLevels: PriceLevel[];
  review?: Review | null;
}

export interface ImportCandidate {
  selected: boolean;
  symbol: string;
  displayName: string;
  direction: Direction;
  rawDirection?: string;
  horizon?: string;
  thesis: string;
  catalystsText?: string;
  triggerCondition?: string;
  risksText?: string;
  priceNotesText?: string;
  sourceQuote?: string;
  rawItemJson?: string;
  priceLevels: PriceLevel[];
}

export interface ImportPreview {
  summary: string[];
  mappingNotes: string[];
  candidates: ImportCandidate[];
  skipped: { name: string; reason: string }[];
}

export interface ImportCommitResult {
  sessionId: string;
  savedOpinions: number;
}
