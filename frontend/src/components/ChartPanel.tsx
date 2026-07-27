import { useMemo, useState } from 'react';
import type { ChartLiveStatus, MarketBackfillStatus, MarketBar, OpinionView, Timeframe } from '../types';
import { BackfillControls } from './BackfillControls';
import { ChartCanvas } from './chart/ChartCanvas';
import { ChartHeader } from './chart/ChartHeader';
import { buildMarkers, buildPriceLines } from './chart/chartViews';

interface Props {
  symbol: string;
  timeframe: Timeframe;
  bars: MarketBar[];
  opinions: OpinionView[];
  loading: boolean;
  refreshing: boolean;
  historyLoading: boolean;
  liveStatus: ChartLiveStatus;
  lastRealtimeAt: number | null;
  message: string;
  backfill?: MarketBackfillStatus | null;
  backfillBusy: boolean;
  backfillError?: string;
  onTimeframeChange: (timeframe: Timeframe) => void;
  onLoadOlder: () => void;
  onBackfillCurrent: () => void;
  onBackfillAll: () => void;
}

export function ChartPanel(props: Props) {
  const [showOpinions, setShowOpinions] = useState(false);
  const [showLevels, setShowLevels] = useState(false);
  const chartBars = useMemo(
    () => props.bars.filter((bar) => bar.timeframe?.toUpperCase() === props.timeframe),
    [props.bars, props.timeframe],
  );
  const markers = useMemo(
    () => showOpinions ? buildMarkers(props.opinions, props.timeframe) : [],
    [props.opinions, props.timeframe, showOpinions],
  );
  const priceLines = useMemo(
    () => showLevels ? buildPriceLines(props.opinions) : [],
    [props.opinions, showLevels],
  );
  const lastBar = chartBars[chartBars.length - 1];
  const previousBar = chartBars[chartBars.length - 2];

  return (
    <section className="chart-panel">
      <ChartHeader
        barsCount={chartBars.length}
        lastBar={lastBar}
        lastRealtimeAt={props.lastRealtimeAt}
        liveStatus={props.liveStatus}
        onTimeframeChange={props.onTimeframeChange}
        onToggleLevels={() => setShowLevels((value) => !value)}
        onToggleOpinions={() => setShowOpinions((value) => !value)}
        previousBar={previousBar}
        showLevels={showLevels}
        showOpinions={showOpinions}
        symbol={props.symbol}
        timeframe={props.timeframe}
      />
      <BackfillControls
        status={props.backfill}
        busy={props.backfillBusy}
        error={props.backfillError}
        symbol={props.symbol}
        onCurrent={props.onBackfillCurrent}
        onAll={props.onBackfillAll}
      />
      <div className="chart-frame">
        {renderBody(props, chartBars, markers, priceLines)}
        {(props.loading || props.refreshing) ? (
          <div className="chart-loading"><span className="chart-spinner" />
            {props.loading ? '载入行情' : '同步最新行情'}
          </div>
        ) : null}
        {props.message && !props.loading ? <div className="chart-note">{props.message}</div> : null}
      </div>
    </section>
  );
}

function renderBody(
  props: Props,
  bars: MarketBar[],
  markers: ReturnType<typeof buildMarkers>,
  priceLines: ReturnType<typeof buildPriceLines>,
) {
  if (!props.symbol) {
    return <div className="chart chart-empty"><span>选择一个标的开始复盘</span></div>;
  }
  if (bars.length === 0) {
    return <div className="chart chart-empty"><span>正在准备行情数据…</span></div>;
  }
  return (
    <ChartCanvas
      bars={bars}
      historyLoading={props.historyLoading}
      markers={markers}
      onLoadOlder={props.onLoadOlder}
      priceLines={priceLines}
      timeframe={props.timeframe}
      viewKey={`${props.symbol}:${props.timeframe}`}
    />
  );
}
