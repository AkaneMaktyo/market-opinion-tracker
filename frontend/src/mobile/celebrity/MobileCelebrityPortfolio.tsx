import {
  ArrowLeft,
  Clock3,
  ExternalLink,
  RefreshCw,
  Star,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import { celebritySyncHealth } from '../../celebrity/syncHealth';
import type {
  CelebrityHolding,
  CelebrityHoldingChange,
  CelebrityInvestorOverview,
  CelebrityPortfolio,
  CelebritySyncStatus,
} from '../../celebrity/types';
import { MobileCelebrityDiscovery } from './MobileCelebrityDiscovery';

interface Props {
  onBack: () => void;
}

type MobileCelebrityTab = 'holdings' | 'changes' | 'discover';
const FOLLOWED_KEY = 'celebrity-followed-investors';

export function MobileCelebrityPortfolio({ onBack }: Props) {
  const [investors, setInvestors] = useState<CelebrityInvestorOverview[]>([]);
  const [selectedSlug, setSelectedSlug] = useState('');
  const [portfolio, setPortfolio] = useState<CelebrityPortfolio>();
  const [changes, setChanges] = useState<CelebrityHoldingChange[]>([]);
  const [status, setStatus] = useState<CelebritySyncStatus>();
  const [followed, setFollowed] = useState<string[]>(readFollowed);
  const [tab, setTab] = useState<MobileCelebrityTab>('holdings');
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [quoteRetries, setQuoteRetries] = useState(0);

  const reload = useCallback(async (slug: string) => {
    if (!slug) return;
    setLoading(true);
    try {
      const [nextPortfolio, nextChanges, nextStatus] = await Promise.all([
        api.celebrityHoldings(slug, 28),
        api.celebrityChanges(slug),
        api.celebritySyncStatus(),
      ]);
      setPortfolio(nextPortfolio);
      setChanges(nextChanges);
      setStatus(nextStatus);
      setMessage('');
    } catch {
      setMessage('公开披露暂时无法读取，已保留最近一次可用内容。');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void (async () => {
      try {
        const [nextInvestors, nextStatus] = await Promise.all([
          api.celebrityInvestors(),
          api.celebritySyncStatus(),
        ]);
        setInvestors(nextInvestors);
        setStatus(nextStatus);
        setSelectedSlug((current) => current || nextInvestors[0]?.slug || '');
      } catch {
        setMessage('名人列表暂时无法读取，请稍后刷新。');
      }
    })();
  }, []);

  useEffect(() => {
    void reload(selectedSlug);
    setQuoteRetries(0);
  }, [reload, selectedSlug]);

  useEffect(() => {
    const activePortfolio = portfolio?.investor.slug === selectedSlug ? portfolio : undefined;
    const needsRefresh = activePortfolio?.holdings.some((item) => !item.symbol || item.currentPrice == null);
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
      setMessage(next.running ? '已开始刷新，完成前继续展示最近有效快照。' : '刷新任务已经在运行。');
    } catch {
      setMessage('暂时无法启动刷新，请稍后再试。');
    }
  }

  function toggleFollow(slug: string) {
    setFollowed((current) => {
      const next = current.includes(slug)
        ? current.filter((item) => item !== slug)
        : [...current, slug];
      localStorage.setItem(FOLLOWED_KEY, JSON.stringify(next));
      return next;
    });
  }

  const investor = investors.find((item) => item.slug === selectedSlug)
    ?? (portfolio?.investor.slug === selectedSlug ? portfolio.investor : undefined);
  const activePortfolio = portfolio?.investor.slug === selectedSlug ? portfolio : undefined;
  const syncHealth = celebritySyncHealth(status);
  const recentSync = status?.lastCompletedAt ?? investor?.syncedAt;

  return (
    <div className="mobile-screen-content mobile-celebrity-screen">
      <header className="mobile-celebrity-sticky-head">
        <button aria-label="返回概览" className="mobile-celebrity-icon-button" onClick={onBack} type="button">
          <ArrowLeft size={19} />
        </button>
        <div>
          <h2>名人持仓雷达</h2>
          <small>最近同步 {formatRecentTime(recentSync)}</small>
        </div>
        <button
          aria-label={status?.running ? '正在刷新公开披露' : '刷新公开披露'}
          className="mobile-celebrity-icon-button refresh"
          disabled={status?.running || status?.enabled === false}
          onClick={() => void sync()}
          type="button"
        >
          <RefreshCw className={status?.running ? 'spinning' : ''} size={18} />
        </button>
      </header>

      <section className={`mobile-celebrity-health ${syncHealth.tone}`} aria-live="polite">
        <div className="mobile-celebrity-health-badges">
          <span>{syncHealth.label}</span>
          {investor?.sourceType === 'SEC_13F' && investor.reportDate && !status?.running
            ? <b>SEC 当前最新</b>
            : null}
        </div>
        <strong>{status?.running ? '公开披露正在刷新' : syncHealth.title}</strong>
        <p>{selectedHealthMessage(investor, syncHealth.message, status?.running)}</p>
      </section>

      <section className="mobile-celebrity-investors" aria-label="切换投资人">
        {investors.map((item) => {
          const isSelected = item.slug === selectedSlug;
          const isFollowed = followed.includes(item.slug);
          return (
            <article className={`mobile-celebrity-investor-card${isSelected ? ' selected' : ''}`} key={item.slug}>
              <button
                aria-current={isSelected ? 'page' : undefined}
                className="mobile-celebrity-investor-select"
                onClick={() => setSelectedSlug(item.slug)}
                type="button"
              >
                <span><small>{sourceShortLabel(item.sourceType)}</small><em>{item.reportDate ? `报告期 ${item.reportDate}` : '等待披露'}</em></span>
                <strong>{item.displayName}</strong>
                <small>{item.holdingCount ? `${item.holdingCount} 个公开仓位` : '暂无公开仓位'}</small>
              </button>
              <button
                aria-label={isFollowed ? `取消关注${item.displayName}` : `关注${item.displayName}`}
                className={`mobile-celebrity-investor-follow${isFollowed ? ' followed' : ''}`}
                onClick={() => toggleFollow(item.slug)}
                type="button"
              >
                <Star fill={isFollowed ? 'currentColor' : 'none'} size={14} />
              </button>
            </article>
          );
        })}
        {investors.length === 0 ? <div className="mobile-celebrity-investor-skeleton">正在读取投资人…</div> : null}
      </section>

      {investor ? <PortfolioSnapshot investor={investor} followed={followed.includes(investor.slug)} onToggleFollow={() => toggleFollow(investor.slug)} /> : null}

      <nav className="mobile-celebrity-tabs" role="tablist" aria-label="名人持仓内容">
        <button aria-selected={tab === 'holdings'} onClick={() => setTab('holdings')} role="tab" type="button">持仓</button>
        <button aria-selected={tab === 'changes'} onClick={() => setTab('changes')} role="tab" type="button">最近变动 <span>{changes.length}</span></button>
        <button aria-selected={tab === 'discover'} onClick={() => setTab('discover')} role="tab" type="button">发现</button>
      </nav>

      {tab === 'holdings' ? <HoldingsPanel loading={loading} portfolio={activePortfolio} /> : null}
      {tab === 'changes' ? <ChangesPanel changes={changes} loading={loading} ready={Boolean(activePortfolio)} /> : null}
      {tab === 'discover' ? <MobileCelebrityDiscovery investors={investors} /> : null}
      {message ? <p className="mobile-celebrity-message">{message}</p> : null}
    </div>
  );
}

function PortfolioSnapshot({ investor, followed, onToggleFollow }: {
  investor: CelebrityInvestorOverview;
  followed: boolean;
  onToggleFollow: () => void;
}) {
  return (
    <section className="mobile-card mobile-celebrity-snapshot">
      <div className="mobile-celebrity-snapshot-head">
        <div><small>{sourceLabel(investor.sourceType)}</small><h3>{investor.displayName}</h3><p>{investor.managerName}</p></div>
        <button className={followed ? 'followed' : ''} onClick={onToggleFollow} type="button">
          <Star fill={followed ? 'currentColor' : 'none'} size={15} />{followed ? '已关注' : '关注'}
        </button>
      </div>
      <div className="mobile-celebrity-snapshot-value"><small>报告组合市值</small><strong>{formatCompactMoney(investor.reportedPortfolioValue)}</strong></div>
      <div className="mobile-celebrity-snapshot-grid">
        <Metric label="公开仓位" value={investor.holdingCount ? `${investor.holdingCount} 个` : '--'} />
        <Metric label="报告期" value={formatDate(investor.reportDate)} />
        <Metric label="披露日" value={formatDate(investor.filedAt)} />
        <Metric label="信息滞后" value={delayLabel(investor)} />
      </div>
      <a href={investor.sourceUrl} rel="noreferrer" target="_blank"><ExternalLink size={14} />查看原始披露</a>
    </section>
  );
}

function HoldingsPanel({ loading, portfolio }: { loading: boolean; portfolio?: CelebrityPortfolio }) {
  return (
    <section className="mobile-celebrity-list" aria-label="名人公开持仓">
      <div className="mobile-celebrity-panel-head"><div><h3>公开持仓</h3><small>{portfolio?.message || '报告权重优先；行情缺失不会按 0 处理。'}</small></div><b>{portfolio?.holdings.length || 0}</b></div>
      {loading && !portfolio ? <EmptyState title="正在读取公开持仓" message="首次加载可能需要几秒。" /> : null}
      {!loading && portfolio?.holdings.length === 0 ? <EmptyState title="暂无公开持仓" message="可刷新公开披露，页面会保留最近一次有效快照。" /> : null}
      {!loading && !portfolio ? <EmptyState title="尚未取得持仓快照" message="请稍后刷新，或切换其他投资人。" /> : null}
      {portfolio?.holdings.map((holding) => <HoldingCard holding={holding} key={holding.holdingKey} />)}
    </section>
  );
}

function HoldingCard({ holding }: { holding: CelebrityHolding }) {
  return (
    <article className="mobile-celebrity-row">
      <div className="mobile-celebrity-row-head">
        <span><strong>{holding.symbol || holding.cusip || '待映射'}</strong><small>{holding.issuerName}{holding.symbol && holding.symbolConfidence !== 'HIGH' ? ' · 代码待核验' : ''}</small></span>
        <b>{formatPercent(holding.reportedWeight)}<small>报告权重</small></b>
      </div>
      <div className="mobile-celebrity-row-primary">
        <Metric label="报告市值" value={formatCompactMoney(holding.reportedValue)} />
        <Metric className={pnlClass(holding.estimatedPnl)} label="估算盈亏" value={formatSignedPercent(holding.estimatedPnlPercent)} />
      </div>
      <div className="mobile-celebrity-row-secondary">
        <span>当前价 <b>{formatMoney(holding.currentPrice)}</b></span>
        <span>{formatShares(holding.shares)} 股</span>
        <span className={`mobile-celebrity-confidence ${holding.costConfidence.toLowerCase()}`}>{confidenceLabel(holding.costConfidence)}</span>
      </div>
      <details className="mobile-celebrity-row-details">
        <summary>查看估算与披露明细</summary>
        <div>
          <Metric label="报告单价" value={formatMoney(holding.reportedUnitValue)} />
          <Metric label="估算均价" value={formatMoney(holding.estimatedAverageCost)} />
          <Metric label="当前市值" value={formatCompactMoney(holding.currentValue)} />
          <Metric label="报告期" value={formatDate(holding.reportDate)} />
        </div>
        <p>{holding.costNote || '成本为公开报告重建估算，不代表实际成交价。'}</p>
      </details>
    </article>
  );
}

function ChangesPanel({ changes, loading, ready }: { changes: CelebrityHoldingChange[]; loading: boolean; ready: boolean }) {
  return (
    <section className="mobile-celebrity-list" aria-label="名人最近公开变动">
      <div className="mobile-celebrity-panel-head"><div><h3>最近公开变动</h3><small>比较相邻可比报告期，不代表实时交易。</small></div><b>{changes.length}</b></div>
      {loading && !ready ? <EmptyState title="正在比较报告期" message="正在读取最近两期公开披露。" /> : null}
      {!loading && changes.length === 0 ? <EmptyState title="暂无可比变动" message="同步两期可比披露后，这里会显示新增、加仓、减仓与退出。" /> : null}
      {changes.map((change) => (
        <article className="mobile-celebrity-change" key={`${change.holdingKey}-${change.reportDate}`}>
          <div className="mobile-celebrity-change-head"><span className={change.action.toLowerCase()}>{actionLabel(change.action)}</span><div><strong>{change.symbol || change.issuerName}</strong><small>{change.issuerName}</small></div><b>{formatPercent(change.reportedWeight)}</b></div>
          <div className="mobile-celebrity-change-metrics"><Metric label="股数变化" value={formatSignedShares(change.sharesDelta)} /><Metric label="变化幅度" value={formatSignedPercent(change.sharesChangePercent)} /><Metric label="报告市值" value={formatCompactMoney(change.reportedValue)} /></div>
          <footer><span><Clock3 size={12} />报告期 {formatDate(change.reportDate)}</span><a href={change.sourceUrl} rel="noreferrer" target="_blank">原始披露<ExternalLink size={12} /></a></footer>
        </article>
      ))}
    </section>
  );
}

function EmptyState({ title, message }: { title: string; message: string }) {
  return <div className="mobile-celebrity-empty-state"><span>公开披露</span><strong>{title}</strong><p>{message}</p></div>;
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

function selectedHealthMessage(investor: CelebrityInvestorOverview | undefined, fallback: string, running?: boolean) {
  if (running) return '完成前继续展示最近一次有效快照，无需停留等待。';
  if (investor?.sourceType === 'SEC_13F' && investor.reportDate) {
    return `${investor.displayName} 当前展示已取得的最新公开申报，报告期 ${investor.reportDate}。`;
  }
  if (investor?.sourceType === 'ARK_DAILY' && investor.reportDate) {
    return `当前展示 ARK 官方 ${investor.reportDate} 日度披露。`;
  }
  return fallback;
}

function sourceLabel(source: string) { return source === 'ARK_DAILY' ? 'ARK 官方日度披露' : 'SEC 13F 季度披露'; }
function sourceShortLabel(source: string) { return source === 'ARK_DAILY' ? 'ARK 日度' : 'SEC 13F'; }
function confidenceLabel(value: string) { return value === 'MEDIUM' ? '中置信估算' : value === 'LOW' ? '低置信估算' : '成本待估'; }
function actionLabel(value: CelebrityHoldingChange['action']) { return ({ NEW: '新增', ADDED: '加仓', REDUCED: '减仓', EXITED: '退出' })[value]; }
function pnlClass(value?: number) { return value == null ? '' : value >= 0 ? 'pnl-pos' : 'pnl-neg'; }
function delayLabel(investor: CelebrityInvestorOverview) { return investor.reportDate ? `${Math.max(0, investor.disclosureDelayDays)} 天` : '--'; }
function formatDate(value?: string) { return value ? value.slice(0, 10) : '--'; }
function formatRecentTime(value?: string) {
  if (!value) return '等待首次完成';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return formatDate(value);
  return date.toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}
function formatMoney(value?: number) { return Number.isFinite(value) ? `$${value!.toLocaleString('en-US', { maximumFractionDigits: 2 })}` : '--'; }
function formatCompactMoney(value?: number) { return Number.isFinite(value) ? `$${new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 2 }).format(value!)}` : '--'; }
function formatPercent(value?: number) { return Number.isFinite(value) ? `${(value! * 100).toFixed(2)}%` : '--'; }
function formatSignedPercent(value?: number) { return Number.isFinite(value) ? `${value! >= 0 ? '+' : ''}${value!.toFixed(2)}%` : '--'; }
function formatShares(value?: number) { return Number.isFinite(value) ? value!.toLocaleString('en-US', { maximumFractionDigits: 0 }) : '--'; }
function formatSignedShares(value?: number) { return Number.isFinite(value) ? `${value! >= 0 ? '+' : ''}${formatShares(value)}` : '--'; }
