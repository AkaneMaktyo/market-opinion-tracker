import { MessageSquareText, PenLine, RefreshCw } from 'lucide-react';
import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { api } from '../../api/client';
import { InstrumentLogo } from '../../components/instruments/InstrumentLogo';
import type { Instrument, MarketBar } from '../../types';
import type { PriceAlert } from '../../types/alerts';
import type { DashboardModel } from './mobileTypes';
import { priceAlertProximity } from './priceAlertProximity';

interface Props {
  dashboard: DashboardModel;
  onFocusSymbol: (symbol: string) => void;
  onOpenMessage: (messageId?: string) => void;
  onQuickAdd: () => void;
}

const ALERT_REFRESH_MS = 30000;

export function MobileOverview({ dashboard, onFocusSymbol, onOpenMessage, onQuickAdd }: Props) {
  const [alerts, setAlerts] = useState<PriceAlert[]>([]);
  const [alertsLoaded, setAlertsLoaded] = useState(false);
  const [selectedAlertId, setSelectedAlertId] = useState('');
  const reminders = useMemo(() => uniqueAlerts(alerts, dashboard.instruments), [alerts, dashboard.instruments]);
  const selected = dashboard.instruments.find((item) => item.symbol === dashboard.selected);
  const selectedAlert = alerts.find((item) => item.id === selectedAlertId && item.symbol === dashboard.selected)
    ?? reminders.find((item) => item.symbol === dashboard.selected);
  const chartBars = dashboard.bars.filter((bar) => bar.timeframe?.toUpperCase() === dashboard.timeframe);
  const latestBar = chartBars[chartBars.length - 1];
  const quote = latestBar?.close ?? selected?.dayClose ?? selectedAlert?.lastPrice;
  const change = quoteChange(selected, chartBars);
  const updatedAt = latestBar
    ? dashboard.lastRealtimeAt ?? timeValue(latestBar.barTime)
    : dashboard.lastQuoteAt ?? timeValue(selected?.dayBarTime) ?? timeValue(selectedAlert?.lastCheckedAt);

  useEffect(() => {
    let disposed = false;
    async function load() {
      try {
        const next = await api.priceAlerts();
        if (!disposed) setAlerts(next);
      } catch {
        // 价格提醒入口仍可单独重试，概览加载失败时保留当前页面内容。
      } finally {
        if (!disposed) setAlertsLoaded(true);
      }
    }
    void load();
    const refreshWhenVisible = () => {
      if (!document.hidden) void load();
    };
    const timer = window.setInterval(refreshWhenVisible, ALERT_REFRESH_MS);
    document.addEventListener('visibilitychange', refreshWhenVisible);
    return () => {
      disposed = true;
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', refreshWhenVisible);
    };
  }, []);

  return (
    <div className="mobile-screen-content mobile-overview">
      <section className="mobile-card mobile-focus-card">
        <div className="mobile-section-head">
          <div className="mobile-focus-title">
            <small>正在关注</small>
            <h2>{dashboard.selected || '请选择标的'} <span>{selected?.name || selectedAlert?.name || ''}</span></h2>
          </div>
          <select
            aria-label="切换关注标的"
            className="mobile-symbol-select"
            onChange={(event) => dashboard.selectSymbol(event.target.value)}
            value={dashboard.selected}
          >
            {!dashboard.instruments.some((item) => item.symbol === dashboard.selected) && dashboard.selected ? <option value={dashboard.selected}>{dashboard.selected}</option> : null}
            {dashboard.instruments.map((item) => <option key={item.id} value={item.symbol}>{item.symbol}</option>)}
          </select>
        </div>
        <div className="mobile-quote-row">
          <div>
            <strong>{formatPrice(quote)}</strong>
            <span className={change >= 0 ? 'mobile-up' : 'mobile-down'}>{formatPct(change)}</span>
          </div>
          <button aria-label="立即刷新行情" disabled={dashboard.quotesRefreshing} onClick={() => void dashboard.refreshQuotes()} type="button">
            <RefreshCw className={dashboard.quotesRefreshing ? 'spinning' : ''} size={17} />
          </button>
        </div>
        <div className="mobile-quote-meta"><span className={`mobile-live-dot status-${dashboard.chartLiveStatus}`} />{quoteStatus(dashboard.chartLiveStatus)}<time>{formatQuoteTime(updatedAt)}</time></div>
        <Sparkline bars={chartBars} />
        <div className="mobile-dual-actions">
          <button
            disabled={!selectedAlert?.sourceMessageId}
            onClick={() => selectedAlert?.sourceMessageId && onOpenMessage(selectedAlert.sourceMessageId)}
            title={selectedAlert?.sourceMessageId ? '查看创建当前价格提醒的原始消息' : '当前提醒没有智能识别来源消息'}
            type="button"
          ><MessageSquareText size={18} />查看消息</button>
          <button className="mobile-primary-action" onClick={onQuickAdd} type="button"><PenLine size={18} />记一条</button>
        </div>
      </section>

      <section className="mobile-stat-strip" aria-label="数据概览">
        <Stat label="提醒标的" value={reminders.length} />
        <Stat label="当前观点" value={dashboard.opinions.length} />
        <Stat label="直播记录" value={dashboard.sessions.length} />
      </section>

      <section className="mobile-card mobile-watchlist-card mobile-reminder-card">
        <div className="mobile-section-head"><div><h3>价格提醒标的</h3><small>点按标的可切换上方行情</small></div><span>{reminders.length} 个</span></div>
        {!alertsLoaded ? <p className="mobile-empty">正在读取价格提醒…</p> : null}
        {alertsLoaded && reminders.length === 0 ? <p className="mobile-empty">还没有设置价格提醒</p> : null}
        {reminders.map((alert) => (
          <ReminderRow
            alert={alert}
            instrument={dashboard.instruments.find((item) => item.symbol === alert.symbol)}
            key={alert.symbol}
            onSelect={() => {
              setSelectedAlertId(alert.id);
              onFocusSymbol(alert.symbol);
            }}
            selected={alert.symbol === dashboard.selected}
          />
        ))}
      </section>

    </div>
  );
}

function ReminderRow({ alert, instrument, onSelect, selected }: { alert: PriceAlert; instrument?: Instrument; onSelect: () => void; selected: boolean }) {
  const price = currentPrice(alert, instrument);
  const proximity = priceAlertProximity(alert, price);
  const showInsideRange = alert.alertType === 'RANGE' && proximity.state === 'inside';
  const showProximity = alert.status === 'ACTIVE' || showInsideRange;
  const proximityClass = showProximity ? ` proximity-${proximity.state}` : '';
  const rowStyle = { '--reminder-closeness': proximity.intensity } as CSSProperties;
  return (
    <button
      aria-current={selected ? 'true' : undefined}
      className={`mobile-stock-row mobile-reminder-row${selected ? ' selected' : ''}${proximityClass}`}
      onClick={onSelect}
      style={rowStyle}
      type="button"
    >
      <InstrumentLogo logoUrl={instrument?.logoUrl} size={38} symbol={alert.symbol} />
      <span className="mobile-stock-name"><strong>{alert.symbol}</strong><small>{alertCondition(alert)}</small></span>
      <span className="mobile-stock-price"><strong>{formatAlertPrice(price)}</strong><small>当前价</small></span>
      {showProximity ? (
        <span className="mobile-reminder-status mobile-proximity-status">
          <strong>{proximity.label}</strong>
          <small>{formatDistance(proximity.distancePercent, showInsideRange ? alert.status : undefined)}</small>
        </span>
      ) : <span className={`mobile-reminder-status status-${alert.status.toLowerCase()}`}>{alertStatus(alert.status)}</span>}
      {showProximity && proximity.state !== 'unavailable' ? <span aria-hidden="true" className="mobile-reminder-proximity-bar"><i /></span> : null}
    </button>
  );
}

function Stat({ label, value }: { label: string; value: number }) {
  return <div><small>{label}</small><strong>{value}</strong></div>;
}

function Sparkline({ bars }: { bars: MarketBar[] }) {
  const values = bars.slice(-36).map((bar) => bar.close).filter(Number.isFinite);
  if (values.length < 2) return <div className="mobile-chart-empty">行情数据准备中…</div>;
  const min = Math.min(...values);
  const max = Math.max(...values);
  const spread = max - min || 1;
  const points = values.map((value, index) => {
    const x = 4 + (index / (values.length - 1)) * 322;
    const y = 102 - ((value - min) / spread) * 88;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(' ');
  const rising = values[values.length - 1] >= values[0];
  const lastPoint = points.split(' ')[values.length - 1].split(',');
  return (
    <svg className={rising ? 'mobile-sparkline mobile-spark-up' : 'mobile-sparkline mobile-spark-down'} viewBox="0 0 330 112" role="img" aria-label="近期行情走势">
      <path d="M4 20H326M4 56H326M4 92H326" />
      <polyline points={points} />
      <circle cx={lastPoint[0]} cy={lastPoint[1]} r="4" />
    </svg>
  );
}

function uniqueAlerts(items: PriceAlert[], instruments: Instrument[]) {
  const grouped = new Map<string, PriceAlert>();
  items.forEach((item) => {
    const current = grouped.get(item.symbol);
    if (!current) {
      grouped.set(item.symbol, item);
      return;
    }
    const instrument = instruments.find((candidate) => candidate.symbol === item.symbol);
    const preferred = preferredAlert(item, current, instrument);
    grouped.set(item.symbol, preferred);
  });
  return [...grouped.values()];
}

function preferredAlert(candidate: PriceAlert, current: PriceAlert, instrument?: Instrument) {
  const candidatePriority = alertPriority(candidate);
  const currentPriority = alertPriority(current);
  if (candidatePriority !== currentPriority) return candidatePriority > currentPriority ? candidate : current;
  const candidateDistance = priceAlertProximity(candidate, currentPrice(candidate, instrument)).distancePercent;
  const currentDistance = priceAlertProximity(current, currentPrice(current, instrument)).distancePercent;
  if (Number.isFinite(candidateDistance) !== Number.isFinite(currentDistance)) {
    return Number.isFinite(candidateDistance) ? candidate : current;
  }
  if (Number.isFinite(candidateDistance) && Number.isFinite(currentDistance) && candidateDistance !== currentDistance) {
    return candidateDistance! < currentDistance! ? candidate : current;
  }
  return candidate.sourceMessageId && !current.sourceMessageId ? candidate : current;
}

function alertPriority(alert: PriceAlert) {
  return ({ ACTIVE: 50, DELIVERING: 40, ERROR: 30, PAUSED: 20, TRIGGERED: 10 })[alert.status];
}

function currentPrice(alert: PriceAlert, instrument?: Instrument) {
  return alert.status === 'ACTIVE' || alert.status === 'DELIVERING'
    ? alert.lastPrice ?? instrument?.dayClose
    : instrument?.dayClose ?? alert.lastPrice;
}

function quoteChange(instrument: Instrument | undefined, bars: MarketBar[]) {
  const last = bars[bars.length - 1]?.close;
  const previous = bars[bars.length - 2]?.close;
  if (Number.isFinite(last) && Number.isFinite(previous) && previous !== 0) {
    return ((last - previous) / previous) * 100;
  }
  return Number.isFinite(instrument?.dayChangePct) ? instrument!.dayChangePct! : 0;
}

function alertCondition(alert: PriceAlert) {
  return alert.alertType === 'POINT'
    ? `点位 ${formatAlertPrice(alert.targetPrice ?? alert.lowerPrice)}`
    : `${formatAlertPrice(alert.lowerPrice)} ～ ${formatAlertPrice(alert.upperPrice)}`;
}

function alertStatus(status: PriceAlert['status']) {
  return ({ ACTIVE: '监控中', DELIVERING: '发送中', TRIGGERED: '已触发', PAUSED: '已暂停', ERROR: '待恢复' })[status];
}

function formatDistance(distance?: number, insideStatus?: PriceAlert['status']) {
  if (insideStatus) return insideStatus === 'ACTIVE' ? '当前已进入' : alertStatus(insideStatus);
  return Number.isFinite(distance) ? `距离 ${distance!.toFixed(2)}%` : '等待当前价';
}

function quoteStatus(status: DashboardModel['chartLiveStatus']) {
  return ({ live: '实时行情', polling: '轮询行情', connecting: '正在连接', reconnecting: '自动恢复', delayed: '延迟行情' })[status];
}

function timeValue(value?: string) {
  if (!value) return null;
  const time = new Date(value).getTime();
  return Number.isNaN(time) ? null : time;
}

function formatQuoteTime(value: number | null) {
  if (!value) return '等待更新';
  return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(value);
}

export function formatPrice(value?: number) {
  return Number.isFinite(value) ? `$${value!.toLocaleString('en-US', { maximumFractionDigits: 2, minimumFractionDigits: 2 })}` : '--';
}

function formatAlertPrice(value?: number) {
  return Number.isFinite(value) ? value!.toLocaleString('zh-CN', { maximumFractionDigits: 8 }) : '--';
}

export function formatPct(value?: number) {
  if (!Number.isFinite(value)) return '--';
  return `${value! >= 0 ? '+' : ''}${value!.toFixed(2)}%`;
}

export function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date);
}
