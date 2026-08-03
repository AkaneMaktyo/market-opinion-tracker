import { Expand, LocateFixed, Shrink } from 'lucide-react';
import { useCallback, useEffect, useRef, useState } from 'react';
import { createChart, CrosshairMode, LineStyle, TrackingModeExitMode } from 'lightweight-charts';
import type {
  CandlestickData,
  HistogramData,
  IChartApi,
  IPriceLine,
  SeriesMarker,
  Time,
} from 'lightweight-charts';
import type { MarketBar, Timeframe } from '../../types';
import { barTime, formatTime } from '../chartTime';

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
  historyLoading: boolean;
  onLoadOlder: () => void;
}
type DataState = { viewKey: string; first: Time | null; count: number };
export function ChartCanvas(props: Props) {
  const hostRef = useRef<HTMLDivElement | null>(null);
  const chartRef = useRef<IChartApi | null>(null);
  const candleRef = useRef<ReturnType<IChartApi['addCandlestickSeries']> | null>(null);
  const volumeRef = useRef<ReturnType<IChartApi['addHistogramSeries']> | null>(null);
  const priceLinesRef = useRef<IPriceLine[]>([]);
  const dataState = useRef<DataState>({ viewKey: '', first: null, count: 0 });
  const loadOlderRef = useRef(props.onLoadOlder);
  const [hovered, setHovered] = useState<CandlestickData<Time> | null>(null);
  const [fullscreen, setFullscreen] = useState(false);
  const [atLatest, setAtLatest] = useState(true);
  loadOlderRef.current = props.onLoadOlder;
  const showLatest = useCallback(() => {
    const count = dataState.current.count;
    if (!chartRef.current || count === 0) return;
    chartRef.current.timeScale().setVisibleLogicalRange({
      from: Math.max(0, count - 120),
      to: count + 8,
    });
    setAtLatest(true);
  }, []);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    dataState.current = { viewKey: '', first: null, count: 0 };
    priceLinesRef.current = [];
    const chart = createChart(host, chartOptions(host));
    const candle = chart.addCandlestickSeries({
      upColor: '#22c55e', downColor: '#f43f5e', borderVisible: false,
      wickUpColor: '#4ade80', wickDownColor: '#fb7185',
    });
    const volume = chart.addHistogramSeries({
      priceFormat: { type: 'volume' }, priceScaleId: 'volume',
    });
    candle.priceScale().applyOptions({ scaleMargins: { top: 0.1, bottom: 0.25 } });
    volume.priceScale().applyOptions({ scaleMargins: { top: 0.82, bottom: 0 } });
    chartRef.current = chart;
    candleRef.current = candle;
    volumeRef.current = volume;
    chart.subscribeCrosshairMove((param) => {
      const value = param.seriesData.get(candle) as CandlestickData<Time> | undefined;
      setHovered(value && 'open' in value ? value : null);
    });
    chart.timeScale().subscribeVisibleLogicalRangeChange((range) => {
      if (!range) return;
      const latest = range.to >= dataState.current.count - 2;
      if (dataState.current.count > 0 && range.from < 30 && !latest) loadOlderRef.current();
      setAtLatest((current) => current === latest ? current : latest);
    });
    const resize = new ResizeObserver(() => chart.applyOptions({
      width: host.clientWidth || 800,
      height: host.clientHeight || 560,
    }));
    resize.observe(host);
    return () => {
      resize.disconnect();
      chart.remove();
      chartRef.current = null;
      candleRef.current = null;
      volumeRef.current = null;
      dataState.current = { viewKey: '', first: null, count: 0 };
      priceLinesRef.current = [];
    };
  }, []);

  useEffect(() => {
    const chart = chartRef.current;
    const candle = candleRef.current;
    const volume = volumeRef.current;
    if (!chart || !candle || !volume || props.bars.length === 0) return;
    const first = barTime(props.bars[0], props.timeframe);
    const previous = dataState.current;
    const replace = previous.viewKey !== props.viewKey
      || previous.first !== first
      || props.bars.length < previous.count
      || props.bars.length > previous.count + 1;
    if (replace) {
      const visible = previous.viewKey === props.viewKey ? chart.timeScale().getVisibleRange() : null;
      candle.setData(props.bars.map((bar) => candlePoint(bar, props.timeframe)));
      volume.setData(props.bars.map((bar) => volumePoint(bar, props.timeframe)));
      dataState.current = { viewKey: props.viewKey, first, count: props.bars.length };
      if (visible) chart.timeScale().setVisibleRange(visible);
      else showLatest();
      return;
    }
    const latest = props.bars[props.bars.length - 1];
    candle.update(candlePoint(latest, props.timeframe));
    volume.update(volumePoint(latest, props.timeframe));
    dataState.current = { viewKey: props.viewKey, first, count: props.bars.length };
  }, [props.bars, props.timeframe, props.viewKey, showLatest]);

  useEffect(() => {
    candleRef.current?.setMarkers(props.markers);
  }, [props.markers]);

  useEffect(() => {
    const candle = candleRef.current;
    if (!candle) return;
    priceLinesRef.current.forEach((line) => candle.removePriceLine(line));
    priceLinesRef.current = props.priceLines.map((line) => candle.createPriceLine({
      ...line, lineWidth: 1, lineStyle: LineStyle.Dashed, axisLabelVisible: true,
    }));
  }, [props.priceLines]);

  useEffect(() => {
    chartRef.current?.applyOptions({
      localization: { timeFormatter: (time: Time) => formatTime(time, props.timeframe) },
      timeScale: { timeVisible: props.timeframe !== '1D' },
    });
  }, [props.timeframe]);

  useEffect(() => {
    if (!fullscreen) return;
    const onKeyDown = (event: KeyboardEvent) => event.key === 'Escape' && setFullscreen(false);
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [fullscreen]);

  const latest = props.bars.length ? candlePoint(props.bars[props.bars.length - 1], props.timeframe) : null;
  const readout = hovered || latest;
  return (
    <div className={`chart-stage ${fullscreen ? 'fullscreen' : ''}`}>
      {readout ? <CandleReadout value={readout} /> : null}
      <div className="chart-quick-actions">
        {!atLatest ? <button onClick={showLatest} type="button"><LocateFixed size={15} />最新</button> : null}
        <button onClick={() => setFullscreen((value) => !value)} type="button">
          {fullscreen ? <Shrink size={15} /> : <Expand size={15} />}{fullscreen ? '退出' : '全屏'}
        </button>
      </div>
      {props.historyLoading ? <div className="chart-history-loading">正在载入更早行情…</div> : null}
      <div ref={hostRef} className="chart" />
    </div>
  );
}

function CandleReadout({ value }: { value: CandlestickData<Time> }) {
  const up = value.close >= value.open;
  return (
    <div className="candle-readout">
      <span>开 <b>{price(value.open)}</b></span><span>高 <b>{price(value.high)}</b></span>
      <span>低 <b>{price(value.low)}</b></span>
      <span className={up ? 'price-up' : 'price-down'}>收 <b>{price(value.close)}</b></span>
    </div>
  );
}

function candlePoint(bar: MarketBar, timeframe: Timeframe): CandlestickData<Time> {
  return { time: barTime(bar, timeframe), open: +bar.open, high: +bar.high, low: +bar.low, close: +bar.close };
}

function volumePoint(bar: MarketBar, timeframe: Timeframe): HistogramData<Time> {
  return { time: barTime(bar, timeframe), value: +bar.volume, color: +bar.close >= +bar.open ? '#22c55e55' : '#f43f5e55' };
}

function chartOptions(host: HTMLDivElement) {
  const nativeAndroid = document.body.classList.contains('native-android');
  return {
    width: host.clientWidth || 800, height: host.clientHeight || 560,
    layout: { background: { color: '#0b1220' }, textColor: '#94a3b8' },
    grid: { vertLines: { color: '#172033' }, horzLines: { color: '#172033' } },
    rightPriceScale: { borderColor: '#263247', autoScale: true },
    timeScale: { borderColor: '#263247', secondsVisible: false, rightOffset: 8, barSpacing: 8, minBarSpacing: 2 },
    crosshair: { mode: CrosshairMode.Normal },
    handleScroll: { mouseWheel: true, pressedMouseMove: true, horzTouchDrag: true, vertTouchDrag: false },
    handleScale: { axisPressedMouseMove: true, mouseWheel: true, pinch: true },
    trackingMode: {
      exitMode: nativeAndroid ? TrackingModeExitMode.OnTouchEnd : TrackingModeExitMode.OnNextTap,
    },
  };
}

function price(value: number) {
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 4 }).format(value);
}
