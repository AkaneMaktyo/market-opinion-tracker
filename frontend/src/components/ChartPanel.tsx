import { useMemo } from 'react';
import { BackfillControls } from './BackfillControls';
import { ChartCanvas } from './chart/ChartCanvas';
import { compareTime, markerTime } from './chartTime';
import type { PriceLineView } from './chart/ChartCanvas';
import type { SeriesMarker, Time } from 'lightweight-charts';
import type { MarketBackfillStatus, MarketBar, OpinionView, Timeframe } from '../types';

interface Props {
  symbol: string;
  timeframe: Timeframe;
  bars: MarketBar[];
  opinions: OpinionView[];
  loading: boolean;
  refreshing: boolean;
  message: string;
  backfill?: MarketBackfillStatus | null;
  backfillBusy: boolean;
  backfillError?: string;
  onTimeframeChange: (timeframe: Timeframe) => void;
  onBackfillCurrent: () => void;
  onBackfillAll: () => void;
}

const colors = {
  BULLISH: '#16a34a',
  BEARISH: '#dc2626',
  RANGE: '#ca8a04',
  WATCH: '#64748b',
};

export function ChartPanel({
  symbol,
  timeframe,
  bars,
  opinions,
  loading,
  refreshing,
  message,
  backfill,
  backfillBusy,
  backfillError,
  onTimeframeChange,
  onBackfillCurrent,
  onBackfillAll,
}: Props) {
  const chartBars = useMemo(
    () => bars.filter((bar) => bar.timeframe?.toUpperCase() === timeframe),
    [bars, timeframe],
  );
  const markers = useMemo(
    () => opinions.map(({ opinion }): SeriesMarker<Time> => {
      const bullish = opinion.direction === 'BULLISH';
      const messageItem = opinion.status === 'MESSAGE';
      return {
        time: markerTime(opinion.opinionTime, timeframe),
        position: bullish ? 'belowBar' : 'aboveBar',
        color: colors[opinion.direction] || colors.WATCH,
        shape: bullish ? 'arrowUp' : opinion.direction === 'BEARISH' ? 'arrowDown' : 'circle',
        text: messageItem ? '消息' : `${label(opinion.direction)} ${opinion.confidence || ''}`,
      };
    }).sort((left, right) => compareTime(left.time, right.time)),
    [opinions, timeframe],
  );
  const priceLines = useMemo<PriceLineView[]>(
    () => opinions.flatMap((item) => item.priceLevels.map((level) => ({
      price: Number(level.price),
      color: levelColor(level.levelType),
      title: level.levelType,
    }))),
    [opinions],
  );

  return (
    <section className="chart-panel">
      <div className="chart-header">
        <div>
          <span className="eyebrow">K 线复盘</span>
          <h2>{symbol || '暂无标的'}</h2>
        </div>
        <div className="chart-tools">
          <div className="timeframe-switch">
            {(['1H', '4H', '1D'] as Timeframe[]).map((item) => (
              <button
                className={timeframe === item ? 'timeframe-button active' : 'timeframe-button'}
                key={item}
                onClick={() => onTimeframeChange(item)}
                type="button"
              >
                {item === '1H' ? '1小时' : item === '4H' ? '4小时' : '日线'}
              </button>
            ))}
          </div>
          <div className="legend">
            <span>看多</span>
            <span>看空</span>
            <span>关键价位</span>
          </div>
        </div>
      </div>
      <BackfillControls
        status={backfill}
        busy={backfillBusy}
        error={backfillError}
        symbol={symbol}
        onCurrent={onBackfillCurrent}
        onAll={onBackfillAll}
      />
      <div className="chart-frame">
        {renderChartBody(symbol, chartBars, timeframe, markers, priceLines)}
        {(loading || refreshing) ? (
          <div className="chart-loading">
            <span className="chart-spinner" />
            {loading ? '加载中' : '刷新中'}
          </div>
        ) : null}
        {message && !loading ? <div className="chart-note">{message}</div> : null}
      </div>
    </section>
  );
}

function renderChartBody(
  symbol: string,
  chartBars: MarketBar[],
  timeframe: Timeframe,
  markers: SeriesMarker<Time>[],
  priceLines: PriceLineView[],
) {
  if (!symbol) {
    return <div className="chart chart-empty"><span>当前 KOL 还没有相关标的</span></div>;
  }
  if (chartBars.length === 0) {
    return <div className="chart chart-empty"><span>等待 K 线数据</span></div>;
  }
  return (
    <ChartCanvas
      bars={chartBars}
      markers={markers}
      priceLines={priceLines}
      timeframe={timeframe}
      viewKey={`${symbol}:${timeframe}`}
    />
  );
}

function label(direction: string) {
  return {
    BULLISH: '看多',
    BEARISH: '看空',
    RANGE: '震荡',
    WATCH: '观望',
  }[direction] || '观点';
}

function levelColor(type: string) {
  return {
    SUPPORT: '#22c55e',
    RESISTANCE: '#f97316',
    TARGET: '#38bdf8',
    STOP: '#ef4444',
  }[type] || '#94a3b8';
}
