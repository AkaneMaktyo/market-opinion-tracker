import { useEffect, useMemo, useRef } from 'react';
import { createChart, CrosshairMode, LineStyle } from 'lightweight-charts';
import type { IChartApi, IPriceLine, SeriesMarker, Time } from 'lightweight-charts';
import { barTime, formatTime } from '../chartTime';
import type { MarketBar, Timeframe } from '../../types';

export interface PriceLineView {
  price: number;
  color: string;
  title: string;
}

interface Props {
  bars: MarketBar[];
  markers: SeriesMarker<Time>[];
  priceLines: PriceLineView[];
  timeframe: Timeframe;
  viewKey: string;
}

export function ChartCanvas({ bars, markers, priceLines, timeframe, viewKey }: Props) {
  const ref = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const seriesRef = useRef<ReturnType<IChartApi['addCandlestickSeries']> | null>(null);
  const priceLinesRef = useRef<IPriceLine[]>([]);
  const lastViewKey = useRef('');

  const candleData = useMemo(
    () => bars.map((bar) => ({
      time: barTime(bar, timeframe),
      open: Number(bar.open),
      high: Number(bar.high),
      low: Number(bar.low),
      close: Number(bar.close),
    })),
    [bars, timeframe],
  );

  useEffect(() => {
    if (!ref.current) return;
    const chart = createChart(ref.current, {
      height: 520,
      layout: { background: { color: '#0f172a' }, textColor: '#cbd5e1' },
      grid: { vertLines: { color: '#1e293b' }, horzLines: { color: '#1e293b' } },
      rightPriceScale: { borderColor: '#334155' },
      timeScale: { borderColor: '#334155', secondsVisible: false },
      crosshair: { mode: CrosshairMode.Normal },
    });
    const series = chart.addCandlestickSeries({
      upColor: '#22c55e',
      downColor: '#ef4444',
      borderVisible: false,
      wickUpColor: '#86efac',
      wickDownColor: '#fca5a5',
    });
    chartRef.current = chart;
    seriesRef.current = series;

    const resize = new ResizeObserver(() => {
      chart.applyOptions({ width: ref.current?.clientWidth || 800 });
    });
    resize.observe(ref.current);

    return () => {
      resize.disconnect();
      chart.remove();
      chartRef.current = null;
      seriesRef.current = null;
      priceLinesRef.current = [];
    };
  }, []);

  useEffect(() => {
    chartRef.current?.applyOptions({
      localization: { timeFormatter: (time: Time) => formatTime(time, timeframe) },
      timeScale: { timeVisible: timeframe !== '1D' },
    });
  }, [timeframe]);

  useEffect(() => {
    const chart = chartRef.current;
    const series = seriesRef.current;
    if (!chart || !series) return;

    priceLinesRef.current.forEach((line) => series.removePriceLine(line));
    priceLinesRef.current = [];
    series.setMarkers(markers);
    priceLinesRef.current = priceLines.map((line) => series.createPriceLine({
      price: line.price,
      color: line.color,
      lineWidth: 1,
      lineStyle: LineStyle.Dashed,
      axisLabelVisible: true,
      title: line.title,
    }));

    const shouldFit = lastViewKey.current !== viewKey;
    lastViewKey.current = viewKey;
    series.setData(candleData);
    if (shouldFit || candleData.length > 1) {
      chart.timeScale().fitContent();
    }
  }, [candleData, markers, priceLines, viewKey]);

  return <div ref={ref} className="chart" />;
}
