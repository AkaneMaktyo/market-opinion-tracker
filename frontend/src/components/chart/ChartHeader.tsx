import { Layers3, Radio } from 'lucide-react';
import type { ChartLiveStatus, MarketBar, Timeframe } from '../../types';

interface Props {
  symbol: string;
  timeframe: Timeframe;
  barsCount: number;
  lastBar?: MarketBar;
  previousBar?: MarketBar;
  liveStatus: ChartLiveStatus;
  lastRealtimeAt: number | null;
  showOpinions: boolean;
  showLevels: boolean;
  onTimeframeChange: (timeframe: Timeframe) => void;
  onToggleOpinions: () => void;
  onToggleLevels: () => void;
}

export function ChartHeader(props: Props) {
  const change = priceChange(props.lastBar, props.previousBar);
  return (
    <div className="chart-header">
      <div className="chart-identity">
        <div className="chart-title-row">
          <h2>{props.symbol || '暂无标的'}</h2>
          <span className={`live-badge ${props.liveStatus}`}>
            <i />{statusLabel(props.liveStatus)}
          </span>
        </div>
        <div className="chart-market-line">
          <strong>{formatPrice(props.lastBar?.close)}</strong>
          {change ? (
            <span className={change.value >= 0 ? 'price-up' : 'price-down'}>
              {signed(change.value)} · {signed(change.percent)}%
            </span>
          ) : null}
          <span>{props.barsCount.toLocaleString()} 根</span>
          {props.lastRealtimeAt ? <span>更新 {formatClock(props.lastRealtimeAt)}</span> : null}
        </div>
      </div>
      <div className="chart-tools">
        <div className="timeframe-switch" aria-label="K 线周期">
          {(['1H', '4H', '1D'] as Timeframe[]).map((item) => (
            <button
              className={props.timeframe === item ? 'timeframe-button active' : 'timeframe-button'}
              key={item}
              onClick={() => props.onTimeframeChange(item)}
              type="button"
            >
              {item === '1H' ? '1小时' : item === '4H' ? '4小时' : '日线'}
            </button>
          ))}
        </div>
        <button
          aria-pressed={props.showOpinions}
          className={`chart-layer-button ${props.showOpinions ? 'active' : ''}`}
          onClick={props.onToggleOpinions}
          type="button"
        >
          <Radio size={15} />观点
        </button>
        <button
          aria-pressed={props.showLevels}
          className={`chart-layer-button ${props.showLevels ? 'active' : ''}`}
          onClick={props.onToggleLevels}
          type="button"
        >
          <Layers3 size={15} />价位
        </button>
      </div>
    </div>
  );
}

function priceChange(last?: MarketBar, previous?: MarketBar) {
  if (!last || !previous || !Number(previous.close)) return null;
  const value = Number(last.close) - Number(previous.close);
  return { value, percent: value / Number(previous.close) * 100 };
}

function formatPrice(value?: number) {
  if (value == null) return '—';
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 4 }).format(Number(value));
}

function signed(value: number) {
  return `${value >= 0 ? '+' : ''}${value.toFixed(2)}`;
}

function formatClock(value: number) {
  return new Date(value).toLocaleTimeString('zh-CN', { hour12: false });
}

function statusLabel(status: ChartLiveStatus) {
  return {
    live: '实时',
    polling: '自动同步',
    connecting: '连接中',
    reconnecting: '自动恢复',
    delayed: '行情延迟',
  }[status];
}
