import { BellRing, ChevronRight, CloudCheck, Database, ListTree, RadioTower, RefreshCw, Users } from 'lucide-react';
import { useState } from 'react';
import { PriceAlertButton } from '../../components/alerts/PriceAlertButton';
import { InstrumentManager } from '../../components/instruments/InstrumentManager';
import { KolPicker } from '../../components/KolPicker';
import { SourceManagerButton } from '../../components/sources/SourceManagerButton';
import type { LiveUpdateController } from '../useLiveUpdate';
import type { DashboardModel } from './mobileTypes';

interface Props {
  dashboard: DashboardModel;
  liveUpdate: LiveUpdateController;
  onOpenTranscript: () => void;
}

export function MobileProfile({ dashboard, liveUpdate, onOpenTranscript }: Props) {
  const [managingInstrument, setManagingInstrument] = useState(false);
  const selectedKol = dashboard.kols.find((item) => item.id === dashboard.selectedKol);
  const selectedInstrument = dashboard.instruments.find((item) => item.symbol === dashboard.selected);
  const checking = liveUpdate.phase === 'checking' || liveUpdate.phase === 'downloading';

  return (
    <div className="mobile-screen-content mobile-profile-screen">
      <section className="mobile-card mobile-profile-card">
        <span className="mobile-profile-avatar">{selectedKol?.name?.slice(0, 1) || 'M'}</span>
        <div><h2>{selectedKol?.name || '观点追踪'}</h2><small>{dashboard.instruments.length} 个自选 · {dashboard.kols.length} 位 KOL</small></div>
      </section>

      <section className="mobile-card mobile-preference-card">
        <div className="mobile-setting-label"><Users size={19} /><span><strong>当前 KOL</strong><small>切换后所有页面同步更新</small></span></div>
        <KolPicker kols={dashboard.kols} onChange={dashboard.selectKol} onCreated={(kol) => { dashboard.setKols((items) => [...items, kol]); dashboard.selectKol(kol.id); }} selectedId={dashboard.selectedKol} />
      </section>

      <section className="mobile-card mobile-settings-group">
        <SourceManagerButton onChanged={() => dashboard.reload()} trigger={<SettingContent icon={<RadioTower size={20} />} note="来源、博主与同步状态" title="来源与 KOL 管理" />} triggerClassName="mobile-setting-row" />
        <button className="mobile-setting-row" disabled={!selectedInstrument} onClick={() => setManagingInstrument(true)} type="button"><SettingContent icon={<ListTree size={20} />} note={`${dashboard.selected || '未选择'} · 改名、分组与行情源`} title="品种管理" /></button>
        <PriceAlertButton onJumpToChart={dashboard.selectSymbol} selectedSymbol={dashboard.selected} trigger={<SettingContent icon={<BellRing size={20} />} note="到价后自动发送通知" title="价格提醒" />} triggerClassName="mobile-setting-row" />
        <button className="mobile-setting-row" onClick={onOpenTranscript} type="button"><SettingContent icon={<Database size={20} />} note="频道、音频与逐段文本" title="视频转写" /></button>
      </section>

      <section className="mobile-card mobile-settings-group">
        <button className="mobile-setting-row" disabled={checking} onClick={liveUpdate.checkNow} type="button"><SettingContent icon={checking ? <RefreshCw className="spinning" size={20} /> : <CloudCheck size={20} />} note={liveUpdate.message} title="数据与更新" /></button>
        <button className="mobile-setting-row" onClick={() => dashboard.reload()} type="button"><SettingContent icon={<RefreshCw size={20} />} note="重新获取自选、观点与行情" title="刷新全部数据" /></button>
      </section>

      <p className="mobile-version-note">普通页面和功能更新会自动下载，无需重新传 APK。</p>

      {managingInstrument && selectedInstrument ? <InstrumentManager groups={dashboard.instrumentGroups} instrument={selectedInstrument} instruments={dashboard.instruments} kolId={dashboard.selectedKol} onChanged={(next) => { setManagingInstrument(false); dashboard.reload(next || dashboard.selected); }} onClose={() => setManagingInstrument(false)} /> : null}
    </div>
  );
}

function SettingContent({ icon, note, title }: { icon: React.ReactNode; note: string; title: string }) {
  return <><span className="mobile-setting-icon">{icon}</span><span className="mobile-setting-copy"><strong>{title}</strong><small>{note}</small></span><ChevronRight className="mobile-setting-chevron" size={19} /></>;
}
