import {
  CheckCircle2,
  CircleAlert,
  ExternalLink,
  RefreshCw,
  Star,
} from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import { CelebrityAlertSettingsPanel, CelebrityDiscoveryPanel } from './CelebrityDiscoveryPanel';
import type {
  CelebrityHolding,
  CelebrityHoldingChange,
  CelebrityInvestorOverview,
  CelebrityPortfolio,
  CelebritySyncStatus,
} from '../../celebrity/types';

type ViewTab = 'holdings' | 'changes' | 'discover';
const FOLLOWED_KEY = 'celebrity-followed-investors';

export function CelebrityPortfolioWorkspace() {
  const [investors, setInvestors] = useState<CelebrityInvestorOverview[]>([]);
  const [selectedSlug, setSelectedSlug] = useState('');
  const [portfolio, setPortfolio] = useState<CelebrityPortfolio>();
  const [changes, setChanges] = useState<CelebrityHoldingChange[]>([]);
  const [status, setStatus] = useState<CelebritySyncStatus>();
  const [followed, setFollowed] = useState<string[]>(readFollowed);
  const [tab, setTab] = useState<ViewTab>('holdings');
  const [loading, setLoading] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [message, setMessage] = useState('');
  const [quoteRetries, setQuoteRetries] = useState(0);

  const loadOverview = useCallback(async () => {
    const [nextInvestors, nextStatus] = await Promise.all([api.celebrityInvestors(), api.celebritySyncStatus()]);
    setInvestors(nextInvestors);
    setStatus(nextStatus);
    setSelectedSlug((current) => current || nextInvestors[0]?.slug || '');
  }, []);

  const loadPortfolio = useCallback(async (slug: string) => {
    if (!slug) return;
    setLoading(true);
    try {
      const [nextPortfolio, nextChanges] = await Promise.all([
        api.celebrityHoldings(slug),
        api.celebrityChanges(slug),
      ]);
      setPortfolio(nextPortfolio);
      setChanges(nextChanges);
      setMessage('');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '名人持仓数据暂时不可用');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadOverview().catch((error) => setMessage(error instanceof Error ? error.message : '读取名人列表失败'));
  }, [loadOverview]);

  useEffect(() => {
    void loadPortfolio(selectedSlug);
    setQuoteRetries(0);
  }, [loadPortfolio, selectedSlug]);

  useEffect(() => {
    const needsRefresh = portfolio?.holdings.some((item) => !item.symbol || item.currentPrice == null);
    if (!needsRefresh || !selectedSlug || quoteRetries >= 4) return;
    const timer = window.setTimeout(() => {
      setQuoteRetries((current) => current + 1);
      void loadPortfolio(selectedSlug);
    }, 8_000);
    return () => window.clearTimeout(timer);
  }, [loadPortfolio, portfolio, quoteRetries, selectedSlug]);

  useEffect(() => {
    if (!status?.running || !selectedSlug) return;
    const timer = window.setInterval(() => {
      void (async () => {
        const next = await api.celebritySyncStatus();
        setStatus(next);
        await loadOverview();
        await loadPortfolio(selectedSlug);
        if (!next.running) setSyncing(false);
      })().catch(() => undefined);
    }, 10_000);
    return () => window.clearInterval(timer);
  }, [loadOverview, loadPortfolio, selectedSlug, status?.running]);

  async function sync() {
    setSyncing(true);
    try {
      const next = await api.syncCelebrityData();
      setStatus(next);
      setMessage(next.running ? '已开始同步公开披露数据，页面会自动更新。' : '同步任务已在运行。');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '无法启动同步');
      setSyncing(false);
    }
  }

  function toggleFollow(slug: string) {
    setFollowed((current) => {
      const next = current.includes(slug) ? current.filter((item) => item !== slug) : [...current, slug];
      localStorage.setItem(FOLLOWED_KEY, JSON.stringify(next));
      return next;
    });
  }

  const activeInvestor = portfolio?.investor ?? investors.find((item) => item.slug === selectedSlug);
  return (
    <div className="celebrity-workspace">
      <section className="celebrity-intro-card">
        <div>
          <span className="celebrity-kicker">公开披露 · 只读跟踪</span>
          <h2>名人持仓雷达</h2>
          <p>13F 是季度末公开仓位，不等于实时持仓；ARKK 为官方日度基金持仓。成本与盈亏为重建估算，绝不代表实际成交记录。</p>
        </div>
        <div className="celebrity-intro-actions">
          <SyncBadge status={status} />
          <button className="primary" disabled={syncing || status?.running || status?.enabled === false} onClick={() => void sync()} type="button">
            <RefreshCw className={syncing || status?.running ? 'spinning' : ''} size={16} />
            {status?.running ? '同步中…' : '刷新披露'}
          </button>
        </div>
      </section>

      <section className="celebrity-investor-grid" aria-label="选择投资人">
        {investors.map((investor) => {
          const isSelected = investor.slug === selectedSlug;
          const isFollowed = followed.includes(investor.slug);
          return (
            <div
              className={`celebrity-investor-card${isSelected ? ' selected' : ''}`}
              key={investor.slug}
            >
              <button
                aria-current={isSelected ? 'page' : undefined}
                className="celebrity-investor-select"
                onClick={() => setSelectedSlug(investor.slug)}
                type="button"
              >
                <span className="celebrity-investor-top"><small>{sourceLabel(investor.sourceType)}</small><span>{investor.reportDate || '等待同步'}</span></span>
                <strong>{investor.displayName}</strong>
                <span className="celebrity-manager">{investor.managerName}</span>
                <span className="celebrity-investor-bottom"><b>{investor.holdingCount || '--'} 个仓位</b><em>{investor.reportDate ? `${investor.disclosureDelayDays} 天前报告期` : '暂无披露'}</em></span>
              </button>
              <button
                aria-label={isFollowed ? `取消关注${investor.displayName}` : `关注${investor.displayName}`}
                className={`celebrity-follow-button${isFollowed ? ' followed' : ''}`}
                onClick={() => toggleFollow(investor.slug)}
                type="button"
              ><Star size={15} fill={isFollowed ? 'currentColor' : 'none'} />{isFollowed ? '已关注' : '关注'}</button>
            </div>
          );
        })}
      </section>

      {activeInvestor ? (
        <section className="celebrity-portfolio-head">
          <div>
            <span className="celebrity-kicker">{sourceLabel(activeInvestor.sourceType)}</span>
            <h3>{activeInvestor.displayName} · {activeInvestor.managerName}</h3>
            <p>报告期 {activeInvestor.reportDate || '--'} · 披露日 {formatDate(activeInvestor.filedAt)} · 报告组合市值 {formatMoney(activeInvestor.reportedPortfolioValue)}</p>
          </div>
          <a className="primary secondary" href={activeInvestor.sourceUrl} rel="noreferrer" target="_blank"><ExternalLink size={15} />查看原始披露</a>
        </section>
      ) : null}

      <div className="celebrity-tabs" role="tablist" aria-label="名人持仓视图">
        <button aria-selected={tab === 'holdings'} onClick={() => setTab('holdings')} role="tab" type="button">持仓明细</button>
        <button aria-selected={tab === 'changes'} onClick={() => setTab('changes')} role="tab" type="button">最近变动 <span>{changes.length}</span></button>
        <button aria-selected={tab === 'discover'} onClick={() => setTab('discover')} role="tab" type="button">全市场发现</button>
      </div>

      {tab === 'holdings' ? <HoldingsTable holdings={portfolio?.holdings || []} loading={loading} /> : null}
      {tab === 'changes' ? <ChangesTable changes={changes} loading={loading} /> : null}
      {tab === 'discover' ? <CelebrityDiscoveryPanel /> : null}
      <CelebrityAlertSettingsPanel investors={investors} />
      {portfolio?.message ? <p className="celebrity-data-note"><CircleAlert size={15} />{portfolio.message}</p> : null}
      {status?.lastError ? <p className="celebrity-error-note"><CircleAlert size={15} />最近同步提示：{status.lastError}</p> : null}
      {message ? <div className="form-message">{message}</div> : null}
    </div>
  );
}

function HoldingsTable({ holdings, loading }: { holdings: CelebrityHolding[]; loading: boolean }) {
  if (loading && holdings.length === 0) return <div className="celebrity-empty">正在读取公开持仓…</div>;
  if (!loading && holdings.length === 0) return <div className="celebrity-empty">还没有持仓数据。点击“刷新披露”后，数据会从官方来源异步导入。</div>;
  return (
    <section className="celebrity-table-card">
      <table className="celebrity-holdings-table">
        <thead><tr><th>标的 / 代码</th><th>报告仓位</th><th>报告占比</th><th>当前行情</th><th>估算成本</th><th>估算盈亏</th></tr></thead>
        <tbody>{holdings.map((holding) => (
          <tr key={holding.holdingKey}>
            <td><div className="celebrity-security"><strong>{holding.symbol || holding.cusip || '待映射'}</strong><span>{holding.issuerName}</span><small>{[holding.titleClass, holding.putCall].filter(Boolean).join(' · ') || '普通股/基金份额'}{holding.symbol && holding.symbolConfidence !== 'HIGH' ? ' · 代码名称匹配待核验' : ''}</small></div></td>
            <td><strong>{formatMoney(holding.reportedValue)}</strong><small>{formatShares(holding.shares)} 股 · 报告期末标价 {formatMoney(holding.reportedUnitValue)}</small></td>
            <td><strong>{formatPercent(holding.reportedWeight)}</strong><small>以报告总市值计算</small></td>
            <td><strong>{formatMoney(holding.currentPrice)}</strong><small>{holding.currentValue ? `当前市值 ${formatMoney(holding.currentValue)}` : '等待代码或有效行情'}</small></td>
            <td title={holding.costNote}><strong>{formatMoney(holding.estimatedAverageCost)}</strong><small>{holding.estimatedCostLow != null ? `${formatMoney(holding.estimatedCostLow)} ～ ${formatMoney(holding.estimatedCostHigh)}` : '尚不能可靠估算'}</small><CostBadge holding={holding} /></td>
            <td className={pnlClass(holding.estimatedPnl)}><strong>{formatSignedMoney(holding.estimatedPnl)}</strong><small>{formatSignedPercent(holding.estimatedPnlPercent)}</small></td>
          </tr>
        ))}</tbody>
      </table>
    </section>
  );
}

function ChangesTable({ changes, loading }: { changes: CelebrityHoldingChange[]; loading: boolean }) {
  if (loading && changes.length === 0) return <div className="celebrity-empty">正在对比本期与上期公开持仓…</div>;
  if (!loading && changes.length === 0) return <div className="celebrity-empty">需要至少两期可比披露后，才能展示新增、加仓、减仓和退出。</div>;
  return (
    <section className="celebrity-table-card">
      <table className="celebrity-holdings-table celebrity-changes-table">
        <thead><tr><th>标的</th><th>动作</th><th>股数变化</th><th>本期报告市值</th><th>报告占比</th><th>披露期</th></tr></thead>
        <tbody>{changes.map((item) => (
          <tr key={item.holdingKey}>
            <td><div className="celebrity-security"><strong>{item.symbol || '待映射'}</strong><span>{item.issuerName}</span></div></td>
            <td><span className={`celebrity-change-tag ${item.action.toLowerCase()}`}>{actionLabel(item.action)}</span></td>
            <td className={item.sharesDelta >= 0 ? 'pnl-pos' : 'pnl-neg'}><strong>{item.sharesDelta >= 0 ? '+' : ''}{formatShares(item.sharesDelta)}</strong><small>{formatSignedPercent(item.sharesChangePercent)}</small></td>
            <td>{formatMoney(item.reportedValue)}</td><td>{formatPercent(item.reportedWeight)}</td><td>{item.reportDate}</td>
          </tr>
        ))}</tbody>
      </table>
    </section>
  );
}

function CostBadge({ holding }: { holding: CelebrityHolding }) {
  if (holding.costConfidence === 'UNKNOWN') return <span className="celebrity-cost-badge unknown">不可估</span>;
  return <span className={`celebrity-cost-badge ${holding.costConfidence.toLowerCase()}`}>{holding.costConfidence === 'MEDIUM' ? '中置信估算' : '低置信估算'}</span>;
}

function SyncBadge({ status }: { status?: CelebritySyncStatus }) {
  if (!status?.enabled) return <span className="celebrity-sync-badge muted">数据同步已关闭</span>;
  if (status.running) return <span className="celebrity-sync-badge running"><RefreshCw className="spinning" size={14} />正在同步</span>;
  if (status.lastOutcome === 'SUCCESS') return <span className="celebrity-sync-badge success"><CheckCircle2 size={14} />已同步 {formatDate(status.lastCompletedAt)}</span>;
  return <span className="celebrity-sync-badge"><CircleAlert size={14} />等待同步</span>;
}

function readFollowed() {
  try {
    const value = JSON.parse(localStorage.getItem(FOLLOWED_KEY) || '[]');
    return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
  } catch {
    return [];
  }
}

function sourceLabel(source: string) { return source === 'ARK_DAILY' ? 'ARK 官方日度' : 'SEC 13F 季度'; }
function actionLabel(action: CelebrityHoldingChange['action']) { return ({ NEW: '新增', ADDED: '加仓', REDUCED: '减仓', EXITED: '退出' })[action]; }
function pnlClass(value?: number) { return value == null ? 'pnl-flat' : value >= 0 ? 'pnl-pos' : 'pnl-neg'; }
function formatMoney(value?: number) { return Number.isFinite(value) ? `$${value!.toLocaleString('en-US', { maximumFractionDigits: 2, minimumFractionDigits: 2 })}` : '--'; }
function formatSignedMoney(value?: number) { return Number.isFinite(value) ? `${value! >= 0 ? '+' : '-'}$${Math.abs(value!).toLocaleString('en-US', { maximumFractionDigits: 2, minimumFractionDigits: 2 })}` : '--'; }
function formatPercent(value?: number) { return Number.isFinite(value) ? `${(value! * 100).toFixed(2)}%` : '--'; }
function formatSignedPercent(value?: number) { return Number.isFinite(value) ? `${value! >= 0 ? '+' : ''}${value!.toFixed(2)}%` : '--'; }
function formatShares(value?: number) { return Number.isFinite(value) ? value!.toLocaleString('en-US', { maximumFractionDigits: 2 }) : '--'; }
function formatDate(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '--'; }
