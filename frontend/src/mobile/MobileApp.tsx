import { Bell, Captions, ChevronRight, ClipboardPaste, House, MessageSquareText, PenLine, Plus, Search, UserRound, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { PriceAlertButton } from '../components/alerts/PriceAlertButton';
import { JsonImportPanel } from '../components/JsonImportPanel';
import { useDashboardData } from '../pages/dashboard/useDashboardData';
import type { LiveUpdateController } from './useLiveUpdate';
import { MobileComposer } from './screens/MobileComposer';
import { MobileOpinions } from './screens/MobileOpinions';
import { MobileOverview } from './screens/MobileOverview';
import { MobileProfile } from './screens/MobileProfile';
import { MobileTranscript } from './screens/MobileTranscript';
import type { MobileTab } from './screens/mobileTypes';

const tabTitles: Record<MobileTab, string> = {
  overview: '今日概览',
  opinions: '观点时间线',
  transcript: '视频转写',
  profile: '我的',
};

export function MobileApp({ liveUpdate }: { liveUpdate: LiveUpdateController }) {
  const dashboard = useDashboardData();
  const [tab, setTab] = useState<MobileTab>('overview');
  const [quickOpen, setQuickOpen] = useState(false);
  const [composerOpen, setComposerOpen] = useState(false);
  const [composerSeed, setComposerSeed] = useState('');
  const viewportRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    viewportRef.current?.scrollTo({ top: 0, behavior: 'auto' });
  }, [tab]);

  function openComposer(seed = '') {
    setQuickOpen(false);
    setComposerSeed(seed);
    setComposerOpen(true);
  }

  return (
    <main className="mobile-app-shell">
      <header className="mobile-app-header">
        <div className="mobile-brand-mark" aria-hidden="true"><span /></div>
        <div className="mobile-page-title"><small>Market pulse</small><strong>{tabTitles[tab]}</strong></div>
        <button aria-label="搜索观点" className="mobile-header-button" onClick={() => setTab('opinions')} type="button"><Search size={20} /></button>
        <PriceAlertButton onJumpToChart={(symbol) => { dashboard.selectSymbol(symbol); setTab('overview'); }} selectedSymbol={dashboard.selected} trigger={<><Bell size={20} /><span className="mobile-notice-dot" /><span className="mobile-visually-hidden">价格提醒</span></>} triggerClassName="mobile-header-button mobile-notice-button" />
      </header>

      <section className="mobile-app-viewport" aria-live="polite" ref={viewportRef}>
        {tab === 'overview' ? <MobileOverview dashboard={dashboard} onOpenOpinions={() => setTab('opinions')} onQuickAdd={() => setQuickOpen(true)} /> : null}
        {tab === 'opinions' ? <MobileOpinions dashboard={dashboard} onQuickAdd={() => setQuickOpen(true)} /> : null}
        {tab === 'transcript' ? <MobileTranscript onCreateOpinion={openComposer} /> : null}
        {tab === 'profile' ? <MobileProfile dashboard={dashboard} liveUpdate={liveUpdate} onOpenTranscript={() => setTab('transcript')} /> : null}
      </section>

      <nav className="mobile-bottom-nav" aria-label="主要导航">
        <NavButton active={tab === 'overview'} icon={<House size={21} />} label="概览" onClick={() => setTab('overview')} />
        <NavButton active={tab === 'opinions'} icon={<MessageSquareText size={21} />} label="观点" onClick={() => setTab('opinions')} />
        <button aria-label="快速添加" className="mobile-add-button" onClick={() => setQuickOpen(true)} type="button"><Plus size={27} /></button>
        <NavButton active={tab === 'transcript'} icon={<Captions size={21} />} label="转写" onClick={() => setTab('transcript')} />
        <NavButton active={tab === 'profile'} icon={<UserRound size={21} />} label="我的" onClick={() => setTab('profile')} />
      </nav>

      {quickOpen ? <div className="modal-backdrop mobile-quick-backdrop" data-mobile-overlay onMouseDown={() => setQuickOpen(false)}><section className="mobile-quick-sheet" onMouseDown={(event) => event.stopPropagation()}><div className="mobile-sheet-handle" /><div className="mobile-sheet-title"><div><h2>快速添加</h2><small>常用操作集中在这里</small></div><button aria-label="关闭" onClick={() => setQuickOpen(false)} type="button"><X size={20} /></button></div><JsonImportPanel kolId={dashboard.selectedKol} onImported={(symbol) => { dashboard.reload(symbol); setTab('opinions'); }} onOpen={() => setQuickOpen(false)} trigger={<ActionContent icon={<ClipboardPaste size={20} />} note="从聊天或直播文字快速识别" title="粘贴导入观点" />} triggerClassName="mobile-action-row" /><button className="mobile-action-row" onClick={() => openComposer()} type="button"><ActionContent icon={<PenLine size={20} />} note="选择标的、方向和关键价位" title="手动记录观点" /></button><PriceAlertButton onJumpToChart={(symbol) => { dashboard.selectSymbol(symbol); setTab('overview'); }} onOpen={() => setQuickOpen(false)} selectedSymbol={dashboard.selected} trigger={<ActionContent icon={<Bell size={20} />} note="到价后自动发送手机通知" title="创建价格提醒" />} triggerClassName="mobile-action-row" /></section></div> : null}

      <MobileComposer dashboard={dashboard} onClose={() => setComposerOpen(false)} onCreated={() => setTab('opinions')} open={composerOpen} seed={composerSeed} />
    </main>
  );
}

function NavButton({ active, icon, label, onClick }: { active: boolean; icon: React.ReactNode; label: string; onClick: () => void }) {
  return <button aria-current={active ? 'page' : undefined} className={active ? 'active' : ''} onClick={onClick} type="button">{icon}<span>{label}</span></button>;
}

function ActionContent({ icon, note, title }: { icon: React.ReactNode; note: string; title: string }) {
  return <><span className="mobile-action-icon">{icon}</span><span><strong>{title}</strong><small>{note}</small></span><ChevronRight size={19} /></>;
}
