import { ArrowLeft, ExternalLink, RefreshCw, Star } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import { celebritySyncHealth } from '../../celebrity/syncHealth';
import type { CelebrityInvestorOverview, CelebrityPortfolio, CelebritySyncStatus } from '../../celebrity/types';
import { MobileCelebrityDiscovery } from './MobileCelebrityDiscovery';

interface Props {
  onBack: () => void;
}

const FOLLOWED_KEY = 'celebrity-followed-investors';

export function MobileCelebrityPortfolio({ onBack }: Props) {
  const [investors, setInvestors] = useState<CelebrityInvestorOverview[]>([]);
  const [selectedSlug, setSelectedSlug] = useState('');
  const [portfolio, setPortfolio] = useState<CelebrityPortfolio>();
  const [status, setStatus] = useState<CelebritySyncStatus>();
  const [followed, setFollowed] = useState<string[]>(readFollowed);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [quoteRetries, setQuoteRetries] = useState(0);

  const reload = useCallback(async (slug: string) => {
    if (!slug) return;
    setLoading(true);
    try {
      const [next, nextStatus] = await Promise.all([api.celebrityHoldings(slug, 28), api.celebritySyncStatus()]);
      setPortfolio(next);
      setStatus(nextStatus);
      setMessage('');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '名人持仓暂时不可用');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void (async () => {
      try {
        const [nextInvestors, nextStatus] = await Promise.all([api.celebrityInvestors(), api.celebritySyncStatus()]);
        setInvestors(nextInvestors);
        setStatus(nextStatus);
        setSelectedSlug((current) => current || nextInvestors[0]?.slug || '');
      } catch (error) {
        setMessage(error instanceof Error ? error.message : '读取名人列表失败');
      }
    })();
  }, []);

  useEffect(() => { void reload(selectedSlug); setQuoteRetries(0); }, [reload, selectedSlug]);

  useEffect(() => {
    const needsRefresh = portfolio?.holdings.some((item) => !item.symbol || item.currentPrice == null);
    if (!needsRefresh || !selectedSlug || quoteRetries >= 4) return;
    const timer = window.setTimeout(() => {
      setQuoteRetries((current) => current + 1);
      void reload(selectedSlug);
    }, 8_000);
    return () => window.clearTimeout(timer);
  }, [portfolio, quoteRetries, reload, selectedSlug]);

  useEffect(() => {
    if (!status?.running || !selectedSlug) return;
    const timer = window.setInterval(() => void reload(selectedSlug), 10_000);
    return () => window.clearInterval(timer);
  }, [reload, selectedSlug, status?.running]);

  async function sync() {
    try {
      const next = await api.syncCelebrityData();
      setStatus(next);
      setMessage(next.running ? '已开始同步，稍后会自动刷新。' : '同步任务已经在运行。');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '无法启动同步');
    }
  }

  function toggleFollow() {
    if (!selectedSlug) return;
    setFollowed((current) => {
      const next = current.includes(selectedSlug) ? current.filter((item) => item !== selectedSlug) : [...current, selectedSlug];
      localStorage.setItem(FOLLOWED_KEY, JSON.stringify(next));
      return next;
    });
  }

  const investor = portfolio?.investor ?? investors.find((item) => item.slug === selectedSlug);
  const isFollowed = followed.includes(selectedSlug);
  const syncHealth = celebritySyncHealth(status);
  return (
    <div className="mobile-screen-content mobile-celebrity-screen">
      <section className="mobile-card mobile-celebrity-top-card">
        <button className="mobile-celebrity-back" onClick={onBack} type="button"><ArrowLeft size={17} />返回概览</button>
        <div className="mobile-section-head"><div><small>公开披露 · 只读跟踪</small><h2>名人持仓雷达</h2></div><span className={`mobile-celebrity-sync ${syncHealth.tone}`}>{syncHealth.label}</span></div>
        <p>季度 13F 与 ARK 官方日度持仓。成本、盈亏是估算，不是实际成交单。</p>
        <div className="mobile-celebrity-controls">
          <select aria-label="选择投资人" onChange={(event) => setSelectedSlug(event.target.value)} value={selectedSlug}>
            {investors.map((item) => <option key={item.slug} value={item.slug}>{item.displayName} · {sourceLabel(item.sourceType)}</option>)}
          </select>
          <button disabled={status?.running || status?.enabled === false} onClick={() => void sync()} type="button"><RefreshCw className={status?.running ? 'spinning' : ''} size={16} />刷新</button>
        </div>
        {['partial', 'failed', 'muted'].includes(syncHealth.tone) ? <div className={`mobile-celebrity-sync-note ${syncHealth.tone}`}><strong>{syncHealth.title}</strong><span>{syncHealth.message}</span></div> : null}
      </section>

      {investor ? (
        <section className="mobile-card mobile-celebrity-summary">
          <div className="mobile-section-head"><div><small>{sourceLabel(investor.sourceType)}</small><h3>{investor.displayName}</h3><p>{investor.managerName}</p></div><button aria-label={isFollowed ? '取消关注' : '关注'} className={isFollowed ? 'followed' : ''} onClick={toggleFollow} type="button"><Star fill={isFollowed ? 'currentColor' : 'none'} size={17} /></button></div>
          <div className="mobile-celebrity-metrics"><div><small>报告期</small><strong>{investor.reportDate || '--'}</strong></div><div><small>报告仓位</small><strong>{investor.holdingCount || '--'} 个</strong></div><div><small>报告市值</small><strong>{formatCompactMoney(investor.reportedPortfolioValue)}</strong></div></div>
          <a href={investor.sourceUrl} rel="noreferrer" target="_blank"><ExternalLink size={14} />查看原始披露</a>
        </section>
      ) : null}

      <section className="mobile-celebrity-list" aria-label="名人公开持仓">
        <div className="mobile-section-head"><div><h3>持仓明细</h3><small>{portfolio?.message || '仅展示公开披露；行情空值不会按 0 处理。'}</small></div><span>{portfolio?.holdings.length || 0} 个</span></div>
        {loading && !portfolio ? <p className="mobile-empty">正在读取公开持仓…</p> : null}
        {!loading && portfolio?.holdings.length === 0 ? <p className="mobile-empty">还没有数据，点击刷新后会异步导入官方披露。</p> : null}
        {portfolio?.holdings.map((holding) => (
          <article className="mobile-celebrity-row" key={holding.holdingKey}>
            <div className="mobile-celebrity-row-head"><span><strong>{holding.symbol || holding.cusip || '待映射'}</strong><small>{holding.issuerName}{holding.symbol && holding.symbolConfidence !== 'HIGH' ? ' · 代码待核验' : ''}</small></span><b>{formatPercent(holding.reportedWeight)}</b></div>
            <div className="mobile-celebrity-row-grid"><Metric label="报告市值" value={formatCompactMoney(holding.reportedValue)} /><Metric label="当前价" value={formatMoney(holding.currentPrice)} /><Metric label="估算成本" value={formatMoney(holding.estimatedAverageCost)} /><Metric className={pnlClass(holding.estimatedPnl)} label="估算盈亏" value={formatSignedPercent(holding.estimatedPnlPercent)} /></div>
            <div className="mobile-celebrity-row-foot"><span>{formatShares(holding.shares)} 股</span><span className={`mobile-celebrity-confidence ${holding.costConfidence.toLowerCase()}`}>{confidenceLabel(holding.costConfidence)}</span></div>
          </article>
        ))}
      </section>
      <MobileCelebrityDiscovery investors={investors} />
      {message ? <p className="mobile-celebrity-message">{message}</p> : null}
    </div>
  );
}

function Metric({ label, value, className = '' }: { label: string; value: string; className?: string }) {
  return <span className={className}><small>{label}</small><strong>{value}</strong></span>;
}

function readFollowed() {
  try {
    const value = JSON.parse(localStorage.getItem(FOLLOWED_KEY) || '[]');
    return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
  } catch { return []; }
}

function sourceLabel(source: string) { return source === 'ARK_DAILY' ? 'ARK 官方日度' : 'SEC 13F 季度'; }
function confidenceLabel(value: string) { return value === 'MEDIUM' ? '中置信估算' : value === 'LOW' ? '低置信估算' : '成本待估'; }
function pnlClass(value?: number) { return value == null ? '' : value >= 0 ? 'pnl-pos' : 'pnl-neg'; }
function formatMoney(value?: number) { return Number.isFinite(value) ? `$${value!.toLocaleString('en-US', { maximumFractionDigits: 2 })}` : '--'; }
function formatCompactMoney(value?: number) { return Number.isFinite(value) ? `$${new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 2 }).format(value!)}` : '--'; }
function formatPercent(value?: number) { return Number.isFinite(value) ? `${(value! * 100).toFixed(2)}%` : '--'; }
function formatSignedPercent(value?: number) { return Number.isFinite(value) ? `${value! >= 0 ? '+' : ''}${value!.toFixed(2)}%` : '--'; }
function formatShares(value?: number) { return Number.isFinite(value) ? value!.toLocaleString('en-US', { maximumFractionDigits: 0 }) : '--'; }
