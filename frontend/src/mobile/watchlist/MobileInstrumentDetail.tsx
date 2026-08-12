import { ArrowLeft } from 'lucide-react';
import { ChartPanel } from '../../components/ChartPanel';
import type { DashboardModel } from '../screens/mobileTypes';

interface Props {
  dashboard: DashboardModel;
  onBack: () => void;
}

export function MobileInstrumentDetail({ dashboard, onBack }: Props) {
  return (
    <div className="mobile-instrument-detail">
      <div className="mobile-detail-bar">
        <button aria-label="返回自选表" onClick={onBack} type="button"><ArrowLeft size={19} /></button>
        <div><small>自选表 / {dashboard.selected}</small><strong>标的详情</strong></div>
      </div>
      <ChartPanel
        backfill={dashboard.backfill}
        backfillBusy={dashboard.backfillBusy}
        backfillError={dashboard.backfillError}
        bars={dashboard.bars}
        historyLoading={dashboard.historyLoading}
        lastRealtimeAt={dashboard.lastRealtimeAt}
        liveStatus={dashboard.chartLiveStatus}
        loading={dashboard.chartLoading}
        message={dashboard.chartMessage}
        onBackfillAll={() => void dashboard.startBackfillAll()}
        onBackfillCurrent={() => void dashboard.startBackfillCurrent()}
        onLoadOlder={dashboard.loadOlderBars}
        onTimeframeChange={dashboard.changeTimeframe}
        opinions={dashboard.opinions}
        refreshing={dashboard.chartRefreshing}
        symbol={dashboard.selected}
        timeframe={dashboard.timeframe}
      />
    </div>
  );
}
