export interface SignalTradingStatus {
  binanceEnabled: boolean;
  binanceConfigured: boolean;
  paper: boolean;
  liveReady: boolean;
  environment: string;
  stockBrokerConfigured: boolean;
  cryptoMessage: string;
  stockMessage: string;
}

export interface SignalTradeOrder {
  id: string;
  planId: string;
  batchNo: number;
  exchangeSymbol: string;
  side: 'BUY';
  orderType: 'LIMIT';
  price: number;
  plannedCost: number;
  quantity: number;
  clientOrderId: string;
  exchangeOrderId?: string;
  status: string;
  executedQuantity: number;
  cumulativeQuote: number;
  averagePrice: number;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface SignalTradePlan {
  id: string;
  alertId: string;
  instrumentId: string;
  assetClass: 'CRYPTO' | 'STOCK';
  provider: 'BINANCE' | 'BINANCE_STOCKS';
  exchangeSymbol: string;
  baseAsset: string;
  quoteAsset: string;
  side: 'BUY';
  totalCost: number;
  batchCount: number;
  environment: string;
  paper: boolean;
  status: string;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
  orders: SignalTradeOrder[];
}

export interface SpotPosition {
  assetClass: 'CRYPTO' | 'STOCK' | 'CASH';
  provider: 'BINANCE' | 'BINANCE_SPOT' | 'BINANCE_FUNDING' | 'BINANCE_STOCKS';
  asset: string;
  symbol: string;
  quantity: number;
  freeQuantity: number;
  lockedQuantity: number;
  currentPrice: number;
  marketValue: number;
  costKnown: boolean;
  costSource: 'UNKNOWN' | 'MANUAL' | 'MANUAL_REVIEW_REQUIRED' | 'TRADES';
  cost?: number;
  averageCost?: number;
  pnl?: number;
  pnlPercent?: number;
}

export interface PositionPortfolio {
  accountReady: boolean;
  paper: boolean;
  message: string;
  valuationCurrency: string;
  marketValue: number;
  knownCost: number;
  knownPnl: number;
  knownPnlPercent: number;
  updatedAt: string;
  positions: SpotPosition[];
}

export interface FuturesPosition {
  symbol: string;
  marginCoin: string;
  side: string;
  isolated: boolean;
  leverage?: number;
  size?: number;
  openPriceAvg?: number;
  markPrice?: number;
  liquidationPrice?: number;
  margin?: number;
  unrealizedPL?: number;
  returnRate?: number;
}

export interface FuturesPortfolio {
  accountReady: boolean;
  demo: boolean;
  productType: string;
  marginCoin: string;
  message: string;
  accountEquity?: number;
  available?: number;
  positionCount: number;
  totalMargin?: number;
  totalUnrealizedPL?: number;
  totalReturnRate?: number;
  updatedAt: string;
  positions: FuturesPosition[];
}
