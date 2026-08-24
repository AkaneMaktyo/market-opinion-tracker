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

export function MobileCelebrityDiscovery({ investors }: { investors: CelebrityInvestorOverview[] }) {
  const [feed, setFeed] = useState<CelebrityFeedItem[]>([]);
  const [consensus, setConsensus] = useState<CelebrityConsensus[]>([]);
  const [overlap, setOverlap] = useState<CelebrityWatchlistOverlap[]>([]);
  const [settings, setSettings] = useState<CelebrityAlertSettings>();
  const [symbol, setSymbol] = useState('');
  const [ownership, setOwnership] = useState<CelebrityInstrumentOwnership[]>([]);
  const [message, setMessage] = useState('');

  useEffect(() => {
    void (async () => {
      try {
        const [nextFeed, nextConsensus, nextOverlap, nextSettings] = await Promise.all([
          api.celebrityFeed(16), api.celebrityConsensus(8), api.celebrityWatchlistOverlap(undefined, 8),
          api.celebrityAlertSettings(),
        ]);
        setFeed(nextFeed);
        setConsensus(nextConsensus);
        setOverlap(nextOverlap);
        setSettings(nextSettings);
      } catch {
        setMessage('公开持仓发现暂时不可用，请稍后再试。');
      }
    })();
  }, []);

  async function search() {
    const value = symbol.trim().toUpperCase();
    if (!value) return;
    try {
      setMessage('');
      setOwnership(await api.celebrityOwnership(value));
    } catch {
      setMessage('标的反查暂时不可用，请稍后再试。');
    }
  }

  async function save(next: CelebrityAlertSettings) {
    try {
      setMessage('');
      setSettings(await api.saveCelebrityAlertSettings(next));
    } catch {
      setMessage('提醒设置暂时无法保存，请稍后再试。');
    }
  }

  function toggleInvestor(slug: string) {
    if (!settings) return;
    const investorSlugs = settings.investorSlugs.includes(slug)
      ? settings.investorSlugs.filter((item) => item !== slug)
      : [...settings.investorSlugs, slug];
    void save({ ...settings, investorSlugs });
  }

  return <section className="mobile-celebrity-discovery">
    <div className="mobile-section-head"><div><small>交叉发现 · 只读</small><h3>公开持仓发现</h3><p>从已同步披露中找自选重合和多人共识。</p></div></div>
    <form className="mobile-celebrity-search" onSubmit={(event) => { event.preventDefault(); void search(); }}><input aria-label="输入代码反查" onChange={(event) => setSymbol(event.target.value)} placeholder="代码反查，例如 TSLA" value={symbol} /><button type="submit"><Search size={15} />反查</button></form>
    {ownership.length > 0 ? <div className="mobile-celebrity-ownership">{ownership.map((item) => <a href={item.sourceUrl} key={`${item.investorSlug}-${item.reportDate}`} rel="noreferrer" target="_blank"><strong>{item.investorName}</strong><span>{item.symbol || item.issuerName} · {percent(item.reportedWeight)} · {item.reportDate}</span></a>)}</div> : null}
    {symbol.trim() && ownership.length === 0 && !message ? <p className="mobile-celebrity-empty">暂无公开持仓命中 {symbol.trim().toUpperCase()}。</p> : null}
    <MobileDiscoveryList empty="你的自选表暂未与已同步持仓重合。" title="与你的自选重合">{overlap.map((item) => <MobileConsensus consensus={item.consensus} key={item.symbol} label={item.symbol} />)}</MobileDiscoveryList>
    <MobileDiscoveryList empty="至少两位名人共同持有时才会显示。" title="多人共同持仓">{consensus.map((item) => <MobileConsensus consensus={item} key={item.key} />)}</MobileDiscoveryList>
    <MobileDiscoveryList empty="同步两期可比披露后会显示变动。" title="最新公开变动">{feed.map((item) => <article className="mobile-celebrity-feed" key={item.id}><span className={item.action.toLowerCase()}>{action(item.action)}</span><div><strong>{item.symbol || item.issuerName}</strong><small>{item.investorName} · {item.reportDate}</small></div><b>{percent(item.reportedWeight)}</b></article>)}</MobileDiscoveryList>
    <section className="mobile-card mobile-celebrity-alerts"><div className="mobile-celebrity-alert-head"><BellRing size={17} /><div><h3>公开披露提醒</h3><p>默认关闭，不补发历史变动，不会自动交易。</p></div></div>{settings ? <><label className="mobile-celebrity-alert-switch"><input checked={settings.enabled} onChange={(event) => void save({ ...settings, enabled: event.target.checked })} type="checkbox" /><span /><b>{settings.enabled ? '已开启' : '已关闭'}</b></label><div className="mobile-celebrity-alert-investors">{investors.map((investor) => <label key={investor.slug}><input checked={settings.investorSlugs.includes(investor.slug)} onChange={() => toggleInvestor(investor.slug)} type="checkbox" />{investor.displayName}</label>)}</div><label className="mobile-celebrity-alert-threshold"><small>最小报告占比</small><select onChange={(event) => void save({ ...settings, minimumReportedWeight: Number(event.target.value) })} value={String(settings.minimumReportedWeight)}><option value="0">不限</option><option value="0.01">1% 及以上</option><option value="0.02">2% 及以上</option><option value="0.05">5% 及以上</option></select></label></> : <p className="mobile-celebrity-empty">正在读取提醒设置…</p>}<p className="mobile-celebrity-alert-note"><ShieldCheck size={13} />通知会附报告期和来源，供自行判断。</p></section>
    {message ? <p className="mobile-celebrity-message">{message}</p> : null}
  </section>;
}

function MobileDiscoveryList({ title, empty, children }: { title: string; empty: string; children: React.ReactNode }) {
  const values = Array.isArray(children) ? children.filter(Boolean) : [children].filter(Boolean);
  return <section className="mobile-celebrity-discovery-list"><div className="mobile-section-head"><h3>{title}</h3></div>{values.length ? values : <p className="mobile-celebrity-empty">{empty}</p>}</section>;
}

function MobileConsensus({ consensus, label }: { consensus: CelebrityConsensus; label?: string }) {
  return <article className="mobile-celebrity-consensus"><div><strong>{label || consensus.symbol || consensus.cusip || consensus.issuerName}</strong><small>{consensus.issuerName}</small></div><b>{consensus.investorCount} 人持有</b><span>{consensus.holders.map((holder) => `${holder.investorName} ${percent(holder.reportedWeight)}`).join(' · ')}</span></article>;
}

function action(value: CelebrityFeedItem['action']) { return ({ NEW: '新增', ADDED: '加仓', REDUCED: '减仓', EXITED: '退出' })[value]; }
function percent(value?: number) { return Number.isFinite(value) ? `${(value! * 100).toFixed(2)}%` : '--'; }
