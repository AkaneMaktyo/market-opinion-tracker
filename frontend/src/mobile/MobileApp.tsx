import { Bell, Captions, LayoutDashboard, ListChecks, MessageSquareText, Search, UserRound } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { PriceAlertButton } from '../components/alerts/PriceAlertButton';
import { useDashboardData } from '../pages/dashboard/useDashboardData';
import type { LiveUpdateController } from './useLiveUpdate';
import { MobileComposer } from './screens/MobileComposer';
import { MobileOpinions } from './screens/MobileOpinions';
import { MobileOverview } from './screens/MobileOverview';
import { MobileProfile } from './screens/MobileProfile';
import { MobileTranscript } from './screens/MobileTranscript';
import type { MobileTab } from './screens/mobileTypes';
import { MobileInstrumentDetail } from './watchlist/MobileInstrumentDetail';
import { MobileWatchlist } from './watchlist/MobileWatchlist';
import { MOBILE_APP_BACK_EVENT } from './useAndroidApp';
import { OPEN_OPINIONS_EVENT, useJpushOpen } from './useJpushOpen';

const tabTitles: Record<MobileTab, string> = {
  opinions: '最新观点',
  overview: '今日概览',
  watchlist: '自选表',
  transcript: '视频转写',
  profile: '我的',
};

export function MobileApp({ liveUpdate }: { liveUpdate: LiveUpdateController }) {
  const dashboard = useDashboardData();
  const [tab, setTab] = useState<MobileTab>('opinions');
  const [detailOpen, setDetailOpen] = useState(false);
  const [transcriptMounted, setTranscriptMounted] = useState(false);
  const [composerOpen, setComposerOpen] = useState(false);
  const [composerSeed, setComposerSeed] = useState('');
  const [focusMessageId, setFocusMessageId] = useState('');
  const viewportRef = useRef<HTMLElement | null>(null);
  const detailOpenRef = useRef(detailOpen);
  detailOpenRef.current = detailOpen;

  useEffect(() => {
    viewportRef.current?.scrollTo({ top: 0, behavior: 'auto' });
  }, [tab, detailOpen]);

  useJpushOpen();

  useEffect(() => {
    const closeDetail = (event: Event) => {
      if (!detailOpenRef.current) return;
      event.preventDefault();
      setDetailOpen(false);
    };
    window.addEventListener(MOBILE_APP_BACK_EVENT, closeDetail);
    return () => window.removeEventListener(MOBILE_APP_BACK_EVENT, closeDetail);
  }, []);

  useEffect(() => {
    const handler = (event: Event) => {
      const detail = (event as CustomEvent<{ messageId?: string }>).detail;
      setDetailOpen(false);
      setTab('opinions');
      setFocusMessageId(detail?.messageId || '');
    };
    window.addEventListener(OPEN_OPINIONS_EVENT, handler);
    return () => window.removeEventListener(OPEN_OPINIONS_EVENT, handler);
  }, []);

  function openComposer(seed = '') {
    setComposerSeed(seed);
    setComposerOpen(true);
  }

  function switchTab(next: MobileTab) {
    if (next === 'transcript') setTranscriptMounted(true);
    setDetailOpen(false);
    setTab(next);
  }

  function openInstrument(symbol: string) {
    dashboard.selectSymbol(symbol);
    setTab('watchlist');
    setDetailOpen(true);
  }

  function focusOverviewSymbol(symbol: string) {
    dashboard.selectSymbol(symbol);
    setDetailOpen(false);
    setTab('overview');
    viewportRef.current?.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function openOpinions(messageId = '') {
    setFocusMessageId(messageId || '');
    switchTab('opinions');
  }

  const title = detailOpen && tab === 'watchlist' ? '标的详情' : tabTitles[tab];

  return (
    <main className="mobile-app-shell">
      <header className="mobile-app-header">
        <div className="mobile-brand-mark" aria-hidden="true"><span /></div>
        <div className="mobile-page-title"><small>Market pulse</small><strong>{title}</strong></div>
        <button aria-label="搜索观点" className="mobile-header-button" onClick={() => openOpinions()} type="button"><Search size={20} /></button>
        <PriceAlertButton
          onJumpToChart={openInstrument}
          selectedSymbol={dashboard.selected}
          trigger={<><Bell size={20} /><span className="mobile-notice-dot" /><span className="mobile-visually-hidden">价格提醒</span></>}
          triggerClassName="mobile-header-button mobile-notice-button"
        />
      </header>

      <section className={`mobile-app-viewport${detailOpen ? ' showing-chart-detail' : ''}`} aria-live="polite" ref={viewportRef}>
        {tab === 'opinions' ? <MobileOpinions focusMessageId={focusMessageId} kolId={dashboard.selectedKol} onWatchlistChanged={() => dashboard.reload()} /> : null}
        {tab === 'overview' ? <MobileOverview dashboard={dashboard} onFocusSymbol={focusOverviewSymbol} onOpenMessage={openOpinions} onQuickAdd={() => openComposer()} /> : null}
        {tab === 'watchlist' && !detailOpen ? <MobileWatchlist dashboard={dashboard} onOpenDetail={openInstrument} /> : null}
        {tab === 'watchlist' && detailOpen ? <MobileInstrumentDetail dashboard={dashboard} onBack={() => setDetailOpen(false)} /> : null}
        {transcriptMounted ? (
          <div className="mobile-persistent-tab" hidden={tab !== 'transcript'}>
            <MobileTranscript onCreateOpinion={openComposer} />
          </div>
        ) : null}
        {tab === 'profile' ? <MobileProfile dashboard={dashboard} liveUpdate={liveUpdate} onOpenTranscript={() => switchTab('transcript')} /> : null}
      </section>

      <nav className="mobile-bottom-nav" aria-label="主要导航">
        <NavButton active={tab === 'opinions'} icon={<MessageSquareText size={21} />} label="观点" onClick={() => openOpinions()} />
        <NavButton active={tab === 'overview'} icon={<LayoutDashboard size={21} />} label="概览" onClick={() => switchTab('overview')} />
        <NavButton active={tab === 'watchlist'} icon={<ListChecks size={21} />} label="自选" onClick={() => switchTab('watchlist')} />
        <NavButton active={tab === 'transcript'} icon={<Captions size={21} />} label="转写" onClick={() => switchTab('transcript')} />
        <NavButton active={tab === 'profile'} icon={<UserRound size={21} />} label="我的" onClick={() => switchTab('profile')} />
      </nav>

      <MobileComposer dashboard={dashboard} onClose={() => setComposerOpen(false)} onCreated={() => openOpinions()} open={composerOpen} seed={composerSeed} />
    </main>
  );
}

function NavButton({ active, icon, label, onClick }: { active: boolean; icon: React.ReactNode; label: string; onClick: () => void }) {
  return <button aria-current={active ? 'page' : undefined} className={active ? 'active' : ''} onClick={onClick} type="button">{icon}<span>{label}</span></button>;
}
