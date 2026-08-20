export type CelebritySourceType = 'SEC_13F' | 'ARK_DAILY';
export type CelebrityCostConfidence = 'MEDIUM' | 'LOW' | 'UNKNOWN';

export interface CelebrityInvestorOverview {
  slug: string;
  displayName: string;
  managerName: string;
  sourceType: CelebritySourceType;
  sourceUrl: string;
  reportDate?: string;
  filedAt?: string;
  syncedAt?: string;
  disclosureDelayDays: number;
  holdingCount: number;
  reportedPortfolioValue: number;
}

export interface CelebrityHolding {
  holdingKey: string;
  symbol?: string;
  symbolConfidence?: 'HIGH' | 'MEDIUM' | 'LOW' | 'UNKNOWN';
  cusip?: string;
  issuerName: string;
  titleClass?: string;
  putCall?: string;
  shares: number;
  reportedValue: number;
  reportedWeight?: number;
  reportedUnitValue?: number;
  currentPrice?: number;
  currentValue?: number;
  currentWeight?: number;
  estimatedAverageCost?: number;
  estimatedCostLow?: number;
  estimatedCostHigh?: number;
  estimatedTotalCost?: number;
  estimatedPnl?: number;
  estimatedPnlPercent?: number;
  costMethod: string;
  costConfidence: CelebrityCostConfidence;
  costNote: string;
  reportDate: string;
  filedAt?: string;
  sourceUrl: string;
  priceUpdatedAt?: string;
}

export interface CelebrityPortfolio {
  investor: CelebrityInvestorOverview;
  holdings: CelebrityHolding[];
  message: string;
}

export interface CelebrityHoldingChange {
  holdingKey: string;
  symbol?: string;
  issuerName: string;
  action: 'NEW' | 'ADDED' | 'REDUCED' | 'EXITED';
  currentShares: number;
  previousShares: number;
  sharesDelta: number;
  sharesChangePercent?: number;
  reportedValue: number;
  reportedWeight?: number;
  reportDate: string;
  filedAt?: string;
  sourceUrl: string;
}

export interface CelebritySyncStatus {
  running: boolean;
  enabled: boolean;
  lastStartedAt?: string;
  lastCompletedAt?: string;
  lastOutcome?: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'PARTIAL' | 'FAILED';
  lastError?: string;
  investorsSynced: number;
  filingsSynced: number;
  holdingsSynced: number;
}

export interface CelebrityFeedItem {
  id: string;
  investorSlug: string;
  investorName: string;
  sourceType: CelebritySourceType;
  symbol?: string;
  cusip?: string;
  issuerName: string;
  action: CelebrityHoldingChange['action'];
  sharesDelta: number;
  sharesChangePercent?: number;
  reportedWeight?: number;
  reportedValue?: number;
  reportDate: string;
  filedAt?: string;
  sourceUrl: string;
}

export interface CelebrityConsensusHolder {
  investorSlug: string;
  investorName: string;
  sourceType: CelebritySourceType;
  reportedWeight?: number;
  reportedValue?: number;
  reportDate: string;
}

export interface CelebrityConsensus {
  key: string;
  symbol?: string;
  cusip?: string;
  issuerName: string;
  investorCount: number;
  combinedReportedValue: number;
  holders: CelebrityConsensusHolder[];
}

export interface CelebrityInstrumentOwnership {
  investorSlug: string;
  investorName: string;
  sourceType: CelebritySourceType;
  symbol?: string;
  issuerName: string;
  shares: number;
  reportedWeight?: number;
  reportedValue?: number;
  reportDate: string;
  filedAt?: string;
  sourceUrl: string;
}

export interface CelebrityWatchlistOverlap {
  symbol: string;
  name?: string;
  consensus: CelebrityConsensus;
}

export interface CelebrityAlertSettings {
  enabled: boolean;
  investorSlugs: string[];
  minimumReportedWeight: number;
  updatedAt?: string;
}
