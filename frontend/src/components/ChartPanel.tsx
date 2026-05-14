import { useEffect, useRef } from 'react';
import { createChart, CrosshairMode, LineStyle } from 'lightweight-charts';
import type { MarketBar, OpinionView } from '../types';

interface Props {
  symbol: string;
  bars: MarketBar[];
  opinions: OpinionView[];
}

const colors = {
  BULLISH: '#16a34a',
  BEARISH: '#dc2626',
  RANGE: '#ca8a04',
  WATCH: '#64748b',
};

export function ChartPanel({ symbol, bars, opinions }: Props) {
  const ref = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!ref.current || bars.length === 0) {
      return;
    }
    const chart = createChart(ref.current, {
      height: 520,
      layout: { background: { color: '#0f172a' }, textColor: '#cbd5e1' },
      grid: {
        vertLines: { color: '#1e293b' },
        horzLines: { color: '#1e293b' },
      },
      rightPriceScale: { borderColor: '#334155' },
      timeScale: { borderColor: '#334155', timeVisible: true },
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
      bars.map((bar) => ({
        time: bar.barTime.slice(0, 10),
        open: Number(bar.open),
        high: Number(bar.high),
        low: Number(bar.low),
        close: Number(bar.close),
      })),
    );
    series.setMarkers(
      opinions.map(({ opinion }) => {
        const bullish = opinion.direction === 'BULLISH';
        return {
          time: opinion.opinionTime.slice(0, 10),
          position: bullish ? 'belowBar' : 'aboveBar',
          color: colors[opinion.direction] || colors.WATCH,
          shape: bullish ? 'arrowUp' : opinion.direction === 'BEARISH' ? 'arrowDown' : 'circle',
          text: `${label(opinion.direction)} ${opinion.confidence || ''}`,
        };
      }),
    );
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
    const resize = new ResizeObserver(() => {
      chart.applyOptions({ width: ref.current?.clientWidth || 800 });
    });
    resize.observe(ref.current);
    return () => {
      resize.disconnect();
      chart.remove();
    };
  }, [bars, opinions]);

  return (
    <section className="chart-panel">
      <div className="chart-header">
        <div>
          <span className="eyebrow">K 线复盘</span>
          <h2>{symbol}</h2>
        </div>
        <div className="legend">
          <span>看多</span>
          <span>看空</span>
          <span>关键价位</span>
        </div>
      </div>
      <div ref={ref} className="chart" />
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
