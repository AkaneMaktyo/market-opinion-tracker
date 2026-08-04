import { ArrowRight, MessageSquareText, PenLine } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../../api/client';
import { InstrumentLogo } from '../../components/instruments/InstrumentLogo';
import type { Instrument, MarketBar, OpinionView } from '../../types';
import type { PriceAlert } from '../../types/alerts';
import type { DashboardModel } from './mobileTypes';

interface Props {
  dashboard: DashboardModel;
  onOpenOpinions: () => void;
  onQuickAdd: () => void;
}

const ALERT_REFRESH_MS = 30000;

export function MobileOverview({ dashboard, onOpenOpinions, onQuickAdd }: Props) {
  const [alerts, setAlerts] = useState<PriceAlert[]>([]);
  const [alertsLoaded, setAlertsLoaded] = useState(false);
  const defaultReminderSelected = useRef(false);
  const reminders = useMemo(() => uniqueAlerts(alerts), [alerts]);
  const selected = dashboard.instruments.find((item) => item.symbol === dashboard.selected);
  const selectedAlert = reminders.find((item) => item.symbol === dashboard.selected);
  const chartBars = dashboard.bars.filter((bar) => bar.timeframe?.toUpperCase() === dashboard.timeframe);
  const latestOpinion = latestOpinionOf(dashboard.opinions);
  const quote = selectedAlert?.lastPrice ?? selected?.dayClose ?? chartBars[chartBars.length - 1]?.close;
  const change = quoteChange(selected, chartBars);

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
    const timer = window.setInterval(() => {
      if (!document.hidden) void load();
    }, ALERT_REFRESH_MS);
    return () => {
      disposed = true;
      window.clearInterval(timer);
    };
  }, []);

  useEffect(() => {
    if (!alertsLoaded || defaultReminderSelected.current) return;
    defaultReminderSelected.current = true;
    const symbol = reminders[0]?.symbol;
    if (symbol && symbol !== dashboard.selected) dashboard.selectSymbol(symbol);
  }, [alertsLoaded, dashboard.selected, dashboard.selectSymbol, reminders]);

  return (
    <div className="mobile-screen-content mobile-overview">
      <section className="mobile-stat-strip" aria-label="数据概览">
        <Stat label="提醒标的" value={reminders.length} />
        <Stat label="当前观点" value={dashboard.opinions.length} />
        <Stat label="直播记录" value={dashboard.sessions.length} />
      </section>

      <section className="mobile-card mobile-focus-card">
        <div className="mobile-section-head">
          <div>
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
          <strong>{formatPrice(quote)}</strong>
          <span className={change >= 0 ? 'mobile-up' : 'mobile-down'}>{formatPct(change)}</span>
        </div>
        <Sparkline bars={chartBars} />
        <div className="mobile-dual-actions">
          <button onClick={onOpenOpinions} type="button"><MessageSquareText size={18} />查看消息</button>
          <button className="mobile-primary-action" onClick={onQuickAdd} type="button"><PenLine size={18} />记一条</button>
        </div>
      </section>

      <section className="mobile-card mobile-watchlist-card mobile-reminder-card">
        <div className="mobile-section-head"><h3>价格提醒标的</h3><span>{reminders.length} 个</span></div>
        {!alertsLoaded ? <p className="mobile-empty">正在读取价格提醒…</p> : null}
        {alertsLoaded && reminders.length === 0 ? <p className="mobile-empty">还没有设置价格提醒</p> : null}
        {reminders.map((alert) => (
          <ReminderRow
            alert={alert}
            instrument={dashboard.instruments.find((item) => item.symbol === alert.symbol)}
            key={alert.symbol}
            onSelect={() => dashboard.selectSymbol(alert.symbol)}
          />
        ))}
      </section>

      <section className="mobile-card mobile-latest-card">
        <div className="mobile-section-head"><h3>最新观点</h3><button onClick={onOpenOpinions} type="button">最新消息</button></div>
        {latestOpinion ? <LatestOpinion item={latestOpinion} /> : <p className="mobile-empty">当前标的还没有观点</p>}
      </section>
    </div>
  );
}

function ReminderRow({ alert, instrument, onSelect }: { alert: PriceAlert; instrument?: Instrument; onSelect: () => void }) {
  return (
    <button className="mobile-stock-row mobile-reminder-row" onClick={onSelect} type="button">
      <InstrumentLogo logoUrl={instrument?.logoUrl} size={38} symbol={alert.symbol} />
      <span className="mobile-stock-name"><strong>{alert.symbol}</strong><small>{alertCondition(alert)}</small></span>
      <span className="mobile-stock-price"><strong>{formatAlertPrice(alert.lastPrice)}</strong><small className={`mobile-reminder-status status-${alert.status.toLowerCase()}`}>{alertStatus(alert.status)}</small></span>
      <ArrowRight aria-hidden="true" size={17} />
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

function LatestOpinion({ item }: { item: OpinionView }) {
  const opinion = item.opinion;
  return (
    <article className="mobile-opinion-preview">
      <span className="mobile-avatar">观</span>
      <div><strong>{opinion.symbol} · {directionLabel(opinion.direction)}</strong><p>{opinion.thesis}</p><small>{formatDate(opinion.opinionTime)}</small></div>
      <b className={`mobile-direction mobile-direction-${opinion.direction.toLowerCase()}`}>{directionLabel(opinion.direction)}</b>
    </article>
  );
}

function uniqueAlerts(items: PriceAlert[]) {
  return [...new Map(items.map((item) => [item.symbol, item])).values()];
}

function latestOpinionOf(items: OpinionView[]) {
  return [...items].sort((left, right) => right.opinion.opinionTime.localeCompare(left.opinion.opinionTime))[0];
}

function quoteChange(instrument: Instrument | undefined, bars: MarketBar[]) {
  if (Number.isFinite(instrument?.dayChangePct)) return instrument!.dayChangePct!;
  const last = bars[bars.length - 1]?.close;
  const previous = bars[bars.length - 2]?.close;
  return last && previous ? ((last - previous) / previous) * 100 : 0;
}

function alertCondition(alert: PriceAlert) {
  return alert.alertType === 'POINT'
    ? `点位 ${formatAlertPrice(alert.targetPrice ?? alert.lowerPrice)}`
    : `${formatAlertPrice(alert.lowerPrice)} ～ ${formatAlertPrice(alert.upperPrice)}`;
}

function alertStatus(status: PriceAlert['status']) {
  return ({ ACTIVE: '监控中', DELIVERING: '发送中', TRIGGERED: '已触发', PAUSED: '已暂停', ERROR: '待恢复' })[status];
}

export function directionLabel(value: string) {
  return ({ BULLISH: '看多', BEARISH: '看空', RANGE: '震荡', WATCH: '观望' } as Record<string, string>)[value] || value;
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
