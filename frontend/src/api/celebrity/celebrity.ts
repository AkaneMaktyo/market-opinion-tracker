import type {
  CelebrityAlertSettings,
  CelebrityConsensus,
  CelebrityFeedItem,
  CelebrityHoldingChange,
  CelebrityInstrumentOwnership,
  CelebrityInvestorOverview,
  CelebrityPortfolio,
  CelebritySyncStatus,
  CelebrityWatchlistOverlap,
} from '../../celebrity/types';
import { json } from '../http';

export const celebrityApi = {
  celebrityInvestors: () => json<CelebrityInvestorOverview[]>('/api/celebrity/investors'),
  celebrityHoldings: (slug: string, limit = 80) =>
    json<CelebrityPortfolio>(`/api/celebrity/investors/${encodeURIComponent(slug)}/holdings?limit=${limit}`),
  celebrityChanges: (slug: string) =>
    json<CelebrityHoldingChange[]>(`/api/celebrity/investors/${encodeURIComponent(slug)}/changes`),
  celebrityFeed: (limit = 40) => json<CelebrityFeedItem[]>(`/api/celebrity/feed?limit=${limit}`),
  celebrityConsensus: (limit = 30) => json<CelebrityConsensus[]>(`/api/celebrity/consensus?limit=${limit}`),
  celebrityOwnership: (symbol: string) =>
    json<CelebrityInstrumentOwnership[]>(`/api/celebrity/instruments/${encodeURIComponent(symbol)}`),
  celebrityWatchlistOverlap: (kolId?: string, limit = 24) => {
    const params = new URLSearchParams({ limit: String(limit) });
    if (kolId) params.set('kolId', kolId);
    return json<CelebrityWatchlistOverlap[]>(`/api/celebrity/watchlist-overlap?${params}`);
  },
  celebrityAlertSettings: () => json<CelebrityAlertSettings>('/api/celebrity/alert-settings'),
  saveCelebrityAlertSettings: (body: Pick<CelebrityAlertSettings, 'enabled' | 'investorSlugs' | 'minimumReportedWeight'>) =>
    json<CelebrityAlertSettings>('/api/celebrity/alert-settings', { method: 'PUT', body: JSON.stringify(body) }),
  celebritySyncStatus: () => json<CelebritySyncStatus>('/api/celebrity/sync-status'),
  syncCelebrityData: () => json<CelebritySyncStatus>('/api/celebrity/sync', { method: 'POST' }),
};
