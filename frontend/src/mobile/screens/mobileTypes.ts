import type { useDashboardData } from '../../pages/dashboard/useDashboardData';

export type DashboardModel = ReturnType<typeof useDashboardData>;
export type MobileTab = 'opinions' | 'overview' | 'watchlist' | 'positions' | 'transcript' | 'profile';
