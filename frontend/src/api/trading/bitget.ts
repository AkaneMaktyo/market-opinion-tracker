import type { FuturesPortfolio } from '../../types/trading';
import { json } from '../http';

export const bitgetTradingApi = {
  futuresPortfolio: () =>
    json<FuturesPortfolio>('/api/trading/bitget/futures-portfolio'),
};
