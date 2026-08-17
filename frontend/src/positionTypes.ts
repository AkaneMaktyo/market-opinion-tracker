export type PositionAction = 'OPEN' | 'CLOSE' | 'IGNORE';
export type PositionStatus = 'ACTIVE' | 'CLOSED';
export type PositionDirection = 'LONG' | 'SHORT';

export interface KolPosition {
  id: string;
  kolId: string;
  instrumentId: string;
  symbol: string;
  instrumentName?: string;
  status: PositionStatus;
  direction?: PositionDirection;
  entryPrice?: number | null;
  exitPrice?: number | null;
  exitReason?: string | null;
  openedAt?: string;
  closedAt?: string;
  lastOpinionId?: string;
  lastAction: string;
  createdAt: string;
  updatedAt: string;
}

export interface PositionView {
  position: KolPosition;
  lastPrice?: number | null;
  pnlPct?: number | null;
}

export interface PositionStats {
  kolId: string;
  totalTrades: number;
  settledTrades: number;
  wins: number;
  losses: number;
  winRate?: number | null;
  avgPnlPct?: number | null;
  bestPnlPct?: number | null;
  worstPnlPct?: number | null;
  totalPnlPct?: number | null;
  activeCount: number;
}

export interface KolPositionTrade {
  id: string;
  kolId: string;
  instrumentId: string;
  symbol: string;
  direction: PositionDirection;
  entryPrice?: number | null;
  entryAt?: string | null;
  exitPrice?: number | null;
  exitAt?: string | null;
  exitReason?: string | null;
  pnlPct?: number | null;
  createdAt: string;
}
