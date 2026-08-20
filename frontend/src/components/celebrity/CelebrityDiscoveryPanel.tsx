import { BellRing, Search, ShieldCheck } from 'lucide-react';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import type {
  CelebrityAlertSettings,
  CelebrityConsensus,
  CelebrityFeedItem,
  CelebrityInstrumentOwnership,
  CelebrityInvestorOverview,
  CelebrityWatchlistOverlap,
} from '../../celebrity/types';

export function CelebrityDiscoveryPanel() {
  const [feed, setFeed] = useState<CelebrityFeedItem[]>([]);
  const [consensus, setConsensus] = useState<CelebrityConsensus[]>([]);
  const [overlap, setOverlap] = useState<CelebrityWatchlistOverlap[]>([]);
  const [symbol, setSymbol] = useState('');
  const [ownership, setOwnership] = useState<CelebrityInstrumentOwnership[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState('');

  useEffect(() => {
    void (async () => {
      try {
        const [nextFeed, nextConsensus, nextOverlap] = await Promise.all([
          api.celebrityFeed(), api.celebrityConsensus(), api.celebrityWatchlistOverlap(),
        ]);
        setFeed(nextFeed);
        setConsensus(nextConsensus);
        setOverlap(nextOverlap);
      } catch (error) {
        setMessage(error instanceof Error ? error.message : '名人发现数据暂时不可用');
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  async function search() {
    const value = symbol.trim().toUpperCase();
    if (!value) return;
    try {
      setMessage('');
      setOwnership(await api.celebrityOwnership(value));
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '读取标的反查失败');
    }
  }

  return (
    <section className="celebrity-discovery">
      <div className="celebrity-discovery-head">
        <div><span className="celebrity-kicker">交叉发现 · 只读</span><h3>从公开披露中找重合</h3><p>共识仅在至少两位已跟踪投资人同时持有时出现；自选重合依据你当前自选表，不读取或执行交易。</p></div>
        <form className="celebrity-symbol-search" onSubmit={(event) => { event.preventDefault(); void search(); }}>
          <input aria-label="按代码反查名人持仓" onChange={(event) => setSymbol(event.target.value)} placeholder="输入代码，例如 TSLA" value={symbol} />
          <button type="submit"><Search size={15} />反查</button>
        </form>
      </div>

      {ownership.length > 0 ? <OwnershipResult ownership={ownership} /> : null}
      {symbol.trim() && ownership.length === 0 && !message && !loading ? <p className="celebrity-discovery-empty">暂无已同步的名人公开持仓命中 {symbol.trim().toUpperCase()}。</p> : null}
      <div className="celebrity-discovery-grid">
        <DiscoveryList title="与你的自选重合" subtitle="你的跟踪标的也出现在这些公开组合中" empty="当前自选表没有与已同步持仓重合的标的。">
          {overlap.map((item) => <ConsensusCard consensus={item.consensus} key={item.symbol} label={item.name ? `${item.symbol} · ${item.name}` : item.symbol} />)}
        </DiscoveryList>
        <DiscoveryList title="多人共同持仓" subtitle="同一标的被至少两位跟踪投资人持有" empty="需要两位以上名人的公开持仓发生重合后才会显示。">
          {consensus.map((item) => <ConsensusCard consensus={item} key={item.key} />)}
        </DiscoveryList>
      </div>
      <DiscoveryList title="最新公开变动" subtitle="比较最近两期披露；实际交易日可能早于披露日期" empty={loading ? '正在读取变动…' : '至少同步两期可比披露后才会出现变动。'}>
        {feed.map((item) => <FeedRow item={item} key={item.id} />)}
      </DiscoveryList>
      {message ? <p className="celebrity-error-note">{message}</p> : null}
    </section>
  );
}

export function CelebrityAlertSettingsPanel({ investors }: { investors: CelebrityInvestorOverview[] }) {
  const [settings, setSettings] = useState<CelebrityAlertSettings>();
  const [message, setMessage] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    void api.celebrityAlertSettings().then(setSettings).catch((error) => {
      setMessage(error instanceof Error ? error.message : '读取提醒设置失败');
    });
  }, []);

  async function save(next: CelebrityAlertSettings) {
    try {
      setSaving(true);
      setMessage('');
      setSettings(await api.saveCelebrityAlertSettings(next));
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '保存提醒设置失败');
    } finally {
      setSaving(false);
    }
  }

  function toggleInvestor(slug: string) {
    if (!settings) return;
    const selected = settings.investorSlugs.includes(slug)
      ? settings.investorSlugs.filter((item) => item !== slug)
      : [...settings.investorSlugs, slug];
    void save({ ...settings, investorSlugs: selected });
  }

  return (
    <section className="celebrity-alert-card">
      <div className="celebrity-alert-title"><BellRing size={18} /><div><h3>公开披露提醒</h3><p>默认关闭；只推送之后出现的新披露变动，不补发历史数据，也不会自动下单。</p></div></div>
      {!settings ? <p className="celebrity-discovery-empty">正在读取提醒设置…</p> : <>
        <label className="celebrity-alert-switch"><input checked={settings.enabled} disabled={saving} onChange={(event) => void save({ ...settings, enabled: event.target.checked })} type="checkbox" /><span /><b>{settings.enabled ? '提醒已开启' : '提醒已关闭'}</b></label>
        <div className="celebrity-alert-options">
          <div><small>跟踪投资人</small><div className="celebrity-alert-investors">{investors.map((investor) => <label key={investor.slug}><input checked={settings.investorSlugs.includes(investor.slug)} disabled={saving} onChange={() => toggleInvestor(investor.slug)} type="checkbox" />{investor.displayName}</label>)}</div></div>
          <label className="celebrity-alert-threshold"><small>最小报告占比</small><select disabled={saving} onChange={(event) => void save({ ...settings, minimumReportedWeight: Number(event.target.value) })} value={String(settings.minimumReportedWeight)}><option value="0">不限</option><option value="0.01">1% 及以上</option><option value="0.02">2% 及以上</option><option value="0.05">5% 及以上</option></select></label>
        </div>
      </>}
      <p className="celebrity-alert-note"><ShieldCheck size={14} />提醒内容会标明报告期和原始来源，供你自行判断。</p>
      {message ? <p className="celebrity-error-note">{message}</p> : null}
    </section>
  );
}

function DiscoveryList({ title, subtitle, empty, children }: { title: string; subtitle: string; empty: string; children: React.ReactNode }) {
  const list = Array.isArray(children) ? children : [children];
  const visible = list.filter(Boolean);
  return <section className="celebrity-discovery-list"><div><h4>{title}</h4><p>{subtitle}</p></div>{visible.length ? <div className="celebrity-discovery-items">{visible}</div> : <p className="celebrity-discovery-empty">{empty}</p>}</section>;
}

function ConsensusCard({ consensus, label }: { consensus: CelebrityConsensus; label?: string }) {
  const security = label || consensus.symbol || consensus.cusip || consensus.issuerName;
  return <article className="celebrity-consensus-card"><div><strong>{security}</strong><small>{consensus.issuerName}</small></div><b>{consensus.investorCount} 人持有</b><span>合计报告市值 {money(consensus.combinedReportedValue)}</span><p>{consensus.holders.map((holder) => `${holder.investorName} ${percent(holder.reportedWeight)}`).join(' · ')}</p></article>;
}

function FeedRow({ item }: { item: CelebrityFeedItem }) {
  return <article className="celebrity-feed-row"><span className={`celebrity-change-tag ${item.action.toLowerCase()}`}>{action(item.action)}</span><div><strong>{item.symbol || item.issuerName}</strong><small>{item.investorName} · 报告期 {item.reportDate}</small></div><span className={item.sharesDelta >= 0 ? 'pnl-pos' : 'pnl-neg'}>{item.sharesDelta >= 0 ? '+' : ''}{shares(item.sharesDelta)} 股</span><b>{percent(item.reportedWeight)}</b></article>;
}

function OwnershipResult({ ownership }: { ownership: CelebrityInstrumentOwnership[] }) {
  return <section className="celebrity-ownership-result"><h4>代码反查结果</h4>{ownership.map((item) => <a href={item.sourceUrl} key={`${item.investorSlug}-${item.reportDate}`} rel="noreferrer" target="_blank"><strong>{item.investorName}</strong><span>{item.symbol || item.issuerName} · 报告占比 {percent(item.reportedWeight)} · {item.reportDate}</span></a>)}</section>;
}

function action(value: CelebrityFeedItem['action']) { return ({ NEW: '新增', ADDED: '加仓', REDUCED: '减仓', EXITED: '退出' })[value]; }
function money(value?: number) { return Number.isFinite(value) ? `$${value!.toLocaleString('en-US', { notation: 'compact', maximumFractionDigits: 2 })}` : '--'; }
function percent(value?: number) { return Number.isFinite(value) ? `${(value! * 100).toFixed(2)}%` : '--'; }
function shares(value?: number) { return Number.isFinite(value) ? value!.toLocaleString('en-US', { maximumFractionDigits: 0 }) : '--'; }
