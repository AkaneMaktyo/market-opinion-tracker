import { ArrowRightLeft, Database, Pencil, Trash2, X } from 'lucide-react';
import type { ReactNode } from 'react';
import { useState } from 'react';
import { api } from '../../api/client';
import type { Instrument } from '../../types';
import { InstrumentLogo } from './InstrumentLogo';

interface Props {
  instrument: Instrument;
  instruments: Instrument[];
  onChanged: (nextSelected?: string) => void;
  onClose: () => void;
}

type ManagerTab = 'rename' | 'merge' | 'provider' | 'delete';

const PROVIDERS = [
  { value: 'auto', label: '自动兜底' },
  { value: 'okx', label: 'OKX' },
  { value: 'binance', label: 'Binance' },
  { value: 'bybit', label: 'Bybit' },
  { value: 'bitget', label: 'Bitget' },
];

export function InstrumentManager({ instrument, instruments, onChanged, onClose }: Props) {
  const [tab, setTab] = useState<ManagerTab>('rename');
  const [symbol, setSymbol] = useState(instrument.symbol);
  const [name, setName] = useState(instrument.name || '');
  const [logoUrl, setLogoUrl] = useState(instrument.logoUrl || '');
  const [mergeTarget, setMergeTarget] = useState('');
  const [provider, setProvider] = useState(instrument.marketDataProvider || 'auto');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const otherInstruments = instruments.filter((item) => item.id !== instrument.id);

  async function doRename() {
    const nextSymbol = symbol.trim().toUpperCase();
    if (!nextSymbol) return;
    setBusy(true);
    try {
      await api.renameInstrument(instrument.id, {
        symbol: nextSymbol,
        name: name.trim() || undefined,
        logoUrl: logoUrl.trim() || null,
      });
      onChanged(nextSymbol);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '保存失败');
      setBusy(false);
    }
  }

  async function doMerge() {
    const target = otherInstruments.find((item) => item.id === mergeTarget);
    if (!target) return;
    if (!window.confirm(`确认把 ${instrument.symbol} 归并到 ${target.symbol} 吗？此操作不可撤销。`)) return;
    setBusy(true);
    try {
      await api.mergeInstrument(instrument.id, mergeTarget);
      onChanged(target.symbol);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '归并失败');
      setBusy(false);
    }
  }

  async function doUpdateProvider() {
    setBusy(true);
    try {
      await api.updateInstrumentMarketProvider(instrument.id, provider === 'auto' ? null : provider);
      onChanged();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '更新行情源失败');
      setBusy(false);
    }
  }

  async function doDelete() {
    const prompt = `确认删除 ${instrument.symbol} 吗？会同时删除该标的的观点、K 线、持仓和共振数据，此操作不可撤销。`;
    if (!window.confirm(prompt)) return;
    setBusy(true);
    try {
      await api.deleteInstrument(instrument.id);
      onChanged();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '删除失败');
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <section className="entry manager-modal" onMouseDown={(event) => event.stopPropagation()}>
        <div className="modal-head">
          <div>
            <div className="panel-title">标的管理：{instrument.symbol}</div>
            <p>{instrument.name || '未命名'}，这里可以改名、归并、切换行情源或直接删除。</p>
          </div>
          <button className="icon-button" onClick={onClose} type="button"><X size={18} /></button>
        </div>
        <div className="manager-tabs">
          <TabButton active={tab === 'rename'} icon={<Pencil size={14} />} onClick={() => setTab('rename')} text="重命名" />
          <TabButton active={tab === 'merge'} icon={<ArrowRightLeft size={14} />} onClick={() => setTab('merge')} text="归并" />
          <TabButton active={tab === 'provider'} icon={<Database size={14} />} onClick={() => setTab('provider')} text="行情源" />
          <TabButton active={tab === 'delete'} icon={<Trash2 size={14} />} onClick={() => setTab('delete')} text="删除" />
        </div>
        {tab === 'rename' ? <RenamePanel busy={busy} logoUrl={logoUrl} name={name} symbol={symbol} setLogoUrl={setLogoUrl} setName={setName} setSymbol={setSymbol} onSave={doRename} /> : null}
        {tab === 'merge' ? <MergePanel busy={busy} instrument={instrument} mergeTarget={mergeTarget} options={otherInstruments} setMergeTarget={setMergeTarget} onMerge={doMerge} /> : null}
        {tab === 'provider' ? <ProviderPanel busy={busy} provider={provider} setProvider={setProvider} onSave={doUpdateProvider} /> : null}
        {tab === 'delete' ? <DeletePanel busy={busy} instrument={instrument} onDelete={doDelete} /> : null}
        {message ? <div className="form-message">{message}</div> : null}
      </section>
    </div>
  );
}

function RenamePanel(props: { busy: boolean; logoUrl: string; name: string; symbol: string; setLogoUrl: (value: string) => void; setName: (value: string) => void; setSymbol: (value: string) => void; onSave: () => void }) {
  const { busy, logoUrl, name, setLogoUrl, setName, setSymbol, symbol, onSave } = props;
  return <div className="manager-panel">
    <div className="manager-logo-row"><InstrumentLogo symbol={symbol} logoUrl={logoUrl || undefined} size={28} /><span className="manager-hint">留空时会按代码自动尝试抓取图标。</span></div>
    <label>标的代码<input onChange={(event) => setSymbol(event.target.value.toUpperCase())} value={symbol} /></label>
    <label>标的名称<input onChange={(event) => setName(event.target.value)} placeholder="例如 台积电" value={name} /></label>
    <label>图标地址<input onChange={(event) => setLogoUrl(event.target.value)} placeholder="可选，填图片链接后优先使用" value={logoUrl} /></label>
    <button className="primary" disabled={busy || !symbol.trim()} onClick={onSave} type="button">保存修改</button>
  </div>;
}

function MergePanel({ busy, instrument, mergeTarget, options, setMergeTarget, onMerge }: { busy: boolean; instrument: Instrument; mergeTarget: string; options: Instrument[]; setMergeTarget: (value: string) => void; onMerge: () => void }) {
  return <div className="manager-panel">
    <label>归并到<select onChange={(event) => setMergeTarget(event.target.value)} value={mergeTarget}>
      <option value="">-- 选择目标标的 --</option>
      {options.map((item) => <option key={item.id} value={item.id}>{item.symbol}{item.name ? ` (${item.name})` : ''}</option>)}
    </select></label>
    <p className="manager-hint manager-hint-warn">会把 {instrument.symbol} 的观点和 K 线都迁移到目标标的，再删除原标的。</p>
    <button className="primary secondary" disabled={busy || !mergeTarget} onClick={onMerge} type="button">执行归并</button>
  </div>;
}

function ProviderPanel({ busy, provider, setProvider, onSave }: { busy: boolean; provider: string; setProvider: (value: string) => void; onSave: () => void }) {
  return <div className="manager-panel">
    <label>K 线供应商<select onChange={(event) => setProvider(event.target.value)} value={provider}>
      {PROVIDERS.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
    </select></label>
    <p className="manager-hint">自动兜底会使用后端默认顺序；选定供应商后，该标的会优先使用它。</p>
    <button className="primary" disabled={busy} onClick={onSave} type="button">保存行情源</button>
  </div>;
}

function DeletePanel({ busy, instrument, onDelete }: { busy: boolean; instrument: Instrument; onDelete: () => void }) {
  return <div className="manager-panel">
    <p className="manager-hint manager-hint-warn">删除后会一并清理 {instrument.symbol} 的观点、K 线、持仓和共振数据，不能恢复。</p>
    <button className="primary secondary" disabled={busy} onClick={onDelete} type="button">确认删除标的</button>
  </div>;
}

function TabButton({ active, icon, text, onClick }: { active: boolean; icon: ReactNode; text: string; onClick: () => void }) {
  return <button className={active ? 'manager-tab active' : 'manager-tab'} onClick={onClick} type="button">{icon}<span>{text}</span></button>;
}
