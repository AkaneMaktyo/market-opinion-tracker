export type PositionAction = 'OPEN' | 'CLOSE' | 'IGNORE';
export type PositionStatus = 'ACTIVE' | 'CLOSED';

export interface KolPosition {
  id: string;
  kolId: string;
  instrumentId: string;
  symbol: string;
  instrumentName?: string;
  status: PositionStatus;
  openedAt?: string;
  closedAt?: string;
  lastOpinionId?: string;
  lastAction: string;
  createdAt: string;
  updatedAt: string;
}
