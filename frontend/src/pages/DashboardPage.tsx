import { AppBrand } from '../components/brand/AppBrand';
import { ChartPanel } from '../components/ChartPanel';
import { JsonImportPanel } from '../components/JsonImportPanel';
import { KolPicker } from '../components/KolPicker';
import { ManualPositionForm } from '../components/ManualPositionForm';
import { MarketSummaryPanel } from '../components/MarketSummaryPanel';
import { OpinionList } from '../components/OpinionList';
import { InstrumentRail } from '../components/instruments/InstrumentRail';
import { ResonancePanel } from '../components/resonance/ResonancePanel';
import { SourceManagerButton } from '../components/sources/SourceManagerButton';
import { YouTubePageButton } from '../components/sources/YouTubePageButton';
import { useDashboardData } from './dashboard/useDashboardData';

export function DashboardPage() {
  const dashboard = useDashboardData();

  return (
    <main className="app">
      <header className="topbar">
        <AppBrand />
        <KolPicker
          kols={dashboard.kols}
          selectedId={dashboard.selectedKol}
          onChange={dashboard.selectKol}
          onCreated={(kol) => {
            dashboard.setKols((items) => [...items, kol]);
            dashboard.selectKol(kol.id);
          }}
        />
        <JsonImportPanel kolId={dashboard.selectedKol} onImported={dashboard.reload} />
        <SourceManagerButton onChanged={() => dashboard.reload()} />
        <YouTubePageButton />
        <ManualPositionForm
          defaultSymbol={dashboard.selected}
          kolId={dashboard.selectedKol}
          onAdded={(next) => dashboard.reload(next || dashboard.selected)}
        />
        <div className="stats">
          <span>{dashboard.instruments.length} 个相关标的</span>
          <span>{dashboard.opinions.length} 条观点与消息</span>
          <span>{dashboard.sessions.length} 场记录</span>
        </div>
      </header>

      <div className="workspace">
        <InstrumentRail
          groups={dashboard.instrumentGroups}
          instruments={dashboard.instruments}
          onChanged={(next) => dashboard.reload(next || dashboard.selected)}
          onSelect={dashboard.selectSymbol}
          selected={dashboard.selected}
        />
        <div className="center">
          <ChartPanel
            backfill={dashboard.backfill}
            backfillBusy={dashboard.backfillBusy}
            backfillError={dashboard.backfillError}
            bars={dashboard.bars}
            loading={dashboard.chartLoading}
            message={dashboard.chartMessage}
            onBackfillAll={() => void dashboard.startBackfillAll()}
            onBackfillCurrent={() => void dashboard.startBackfillCurrent()}
            onTimeframeChange={dashboard.changeTimeframe}
            opinions={dashboard.opinions}
            refreshing={dashboard.chartRefreshing}
            symbol={dashboard.selected}
            timeframe={dashboard.timeframe}
          />
        </div>
        <div className="right-column">
          <ResonancePanel symbol={dashboard.selected} />
          <MarketSummaryPanel sessions={dashboard.sessions} />
          <OpinionList
            opinions={dashboard.opinions}
            onChanged={dashboard.refreshOpinions}
            symbol={dashboard.selected}
          />
        </div>
      </div>
    </main>
  );
}
