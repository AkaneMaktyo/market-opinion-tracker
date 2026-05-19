import { useEffect, useMemo, useRef } from 'react';
import { createChart, CrosshairMode, LineStyle } from 'lightweight-charts';
import { BackfillControls } from './BackfillControls';
import { barTime, compareTime, formatTime, markerTime } from './chartTime';
import type { SeriesMarker, Time } from 'lightweight-charts';
import type { MarketBackfillStatus, MarketBar, OpinionView, Timeframe } from '../types';

interface Props {
  symbol: string;
  timeframe: Timeframe;
  bars: MarketBar[];
  opinions: OpinionView[];
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
  backfill,
  backfillBusy,
  backfillError,
  onTimeframeChange,
  onBackfillCurrent,
  onBackfillAll,
}: Props) {
  const ref = useRef<HTMLDivElement | null>(null);
  const chartBars = useMemo(
    () => bars.filter((bar) => bar.timeframe?.toUpperCase() === timeframe),
    [bars, timeframe],
  );
  const markers = useMemo(
    () => opinions.map(({ opinion }): SeriesMarker<Time> => {
      const bullish = opinion.direction === 'BULLISH';
      return {
        time: markerTime(opinion.opinionTime, timeframe),
        position: bullish ? 'belowBar' : 'aboveBar',
        color: colors[opinion.direction] || colors.WATCH,
        shape: bullish ? 'arrowUp' : opinion.direction === 'BEARISH' ? 'arrowDown' : 'circle',
        text: `${label(opinion.direction)} ${opinion.confidence || ''}`,
      };
    }).sort((left, right) => compareTime(left.time, right.time)),
    [opinions, timeframe],
  );

  useEffect(() => {
    if (!ref.current || chartBars.length === 0) {
      return;
    }
    let chart: ReturnType<typeof createChart> | null = null;
    let resize: ResizeObserver | null = null;
    try {
      chart = createChart(ref.current, {
        height: 520,
        layout: { background: { color: '#0f172a' }, textColor: '#cbd5e1' },
        grid: {
          vertLines: { color: '#1e293b' },
          horzLines: { color: '#1e293b' },
        },
        rightPriceScale: { borderColor: '#334155' },
        localization: { timeFormatter: (time: Time) => formatTime(time, timeframe) },
        timeScale: {
          borderColor: '#334155',
          timeVisible: timeframe !== '1D',
          secondsVisible: false,
        },
        crosshair: { mode: CrosshairMode.Normal },
      });
      const series = chart.addCandlestickSeries({
        upColor: '#22c55e',
        downColor: '#ef4444',
        borderVisible: false,
        wickUpColor: '#86efac',
        wickDownColor: '#fca5a5',
      });
      series.setData(
        chartBars.map((bar) => ({
          time: barTime(bar, timeframe),
          open: Number(bar.open),
          high: Number(bar.high),
          low: Number(bar.low),
          close: Number(bar.close),
        })),
      );
      series.setMarkers(markers);
      opinions.flatMap((item) => item.priceLevels).forEach((level) => {
        series.createPriceLine({
          price: Number(level.price),
          color: levelColor(level.levelType),
          lineWidth: 1,
          lineStyle: LineStyle.Dashed,
          axisLabelVisible: true,
          title: level.levelType,
        });
      });
      chart.timeScale().fitContent();
      const currentChart = chart;
      resize = new ResizeObserver(() => {
        currentChart.applyOptions({ width: ref.current?.clientWidth || 800 });
      });
      resize.observe(ref.current);
    } catch (error) {
      console.error('图表渲染失败', error);
      chart?.remove();
      chart = null;
    }
    return () => {
      resize?.disconnect();
      chart?.remove();
    };
  }, [chartBars, markers, opinions, timeframe]);

  return (
    <section className="chart-panel">
      <div className="chart-header">
        <div>
          <span className="eyebrow">K 线复盘</span>
          <h2>{symbol}</h2>
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
      {chartBars.length === 0 ? (
        <div className="chart chart-empty">
          <span>暂无 K 线数据</span>
        </div>
      ) : (
        <div ref={ref} className="chart" />
      )}
    </section>
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
