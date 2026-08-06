import type { useDashboardData } from '../../pages/dashboard/useDashboardData';

export type DashboardModel = ReturnType<typeof useDashboardData>;
export type MobileTab = 'overview' | 'opinions' | 'transcript' | 'profile';
