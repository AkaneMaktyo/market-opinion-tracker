import { ArrowRightLeft, FolderOpen, Pencil, X } from 'lucide-react';
import type { ReactNode } from 'react';
import { useState } from 'react';
import { api } from '../../api/client';
import type { Instrument } from '../../types';
import { InstrumentLogo } from './InstrumentLogo';

interface Props {
  instrument: Instrument;
  instruments: Instrument[];
  groups: string[];
  onChanged: (nextSelected?: string) => void;
  onClose: () => void;
}

export function InstrumentManager({ instrument, instruments, groups, onChanged, onClose }: Props) {
  const [tab, setTab] = useState<'rename' | 'merge' | 'group'>('rename');
  const [symbol, setSymbol] = useState(instrument.symbol);
  const [name, setName] = useState(instrument.name || '');
  const [logoUrl, setLogoUrl] = useState(instrument.logoUrl || '');
  const [mergeTarget, setMergeTarget] = useState('');
  const [groupName, setGroupName] = useState(instrument.groupName || '');
  const [newGroup, setNewGroup] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const otherInstruments = instruments.filter((item) => item.id !== instrument.id);
  const groupOptions = [...new Set([...groups, ...(instrument.groupName ? [instrument.groupName] : [])])]
    .filter(Boolean)
    .sort((left, right) => left.localeCompare(right));

  async function doRename() {
    const nextSymbol = symbol.trim().toUpperCase();
    if (!nextSymbol) return;
    setBusy(true);
    try {
      await api.renameInstrument(instrument.id, { symbol: nextSymbol, name: name.trim() || undefined, logoUrl: logoUrl.trim() || null });
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

  async function doUpdateGroup() {
    setBusy(true);
    try {
      await api.updateInstrumentGroup(instrument.id, newGroup.trim() || groupName.trim() || null);
      onChanged();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '更新分组失败');
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <section className="entry manager-modal" onMouseDown={(event) => event.stopPropagation()}>
        <div className="modal-head">
          <div><div className="panel-title">品种管理：{instrument.symbol}</div><p>{instrument.name || '未命名'}，这里可以改名、归并和分组。</p></div>
          <button className="icon-button" onClick={onClose} type="button"><X size={18} /></button>
        </div>
        <div className="manager-tabs">
          <TabButton active={tab === 'rename'} icon={<Pencil size={14} />} onClick={() => setTab('rename')} text="重命名" />
          <TabButton active={tab === 'merge'} icon={<ArrowRightLeft size={14} />} onClick={() => setTab('merge')} text="归并" />
          <TabButton active={tab === 'group'} icon={<FolderOpen size={14} />} onClick={() => setTab('group')} text="分组" />
        </div>
        {tab === 'rename' ? <div className="manager-panel">
          <div className="manager-logo-row"><InstrumentLogo symbol={symbol || instrument.symbol} logoUrl={logoUrl || undefined} size={28} /><span className="manager-hint">留空时会自动按代码尝试抓取图标。</span></div>
          <label>品种代码<input onChange={(event) => setSymbol(event.target.value.toUpperCase())} value={symbol} /></label>
          <label>品种名称<input onChange={(event) => setName(event.target.value)} placeholder="例如 台积电" value={name} /></label>
          <label>图标地址<input onChange={(event) => setLogoUrl(event.target.value)} placeholder="可选，填图片链接后优先使用" value={logoUrl} /></label>
          <button className="primary" disabled={busy || !symbol.trim()} onClick={doRename} type="button">保存修改</button>
        </div> : null}
        {tab === 'merge' ? <div className="manager-panel">
          <label>归并到<select onChange={(event) => setMergeTarget(event.target.value)} value={mergeTarget}>
            <option value="">-- 选择目标品种 --</option>
            {otherInstruments.map((item) => <option key={item.id} value={item.id}>{item.symbol}{item.name ? ` (${item.name})` : ''}</option>)}
          </select></label>
          <p className="manager-hint manager-hint-warn">会把 {instrument.symbol} 的观点和 K 线数据都迁到目标品种，再删除原条目。</p>
          <button className="primary secondary" disabled={busy || !mergeTarget} onClick={doMerge} type="button">执行归并</button>
        </div> : null}
        {tab === 'group' ? <div className="manager-panel">
          <label>已有分组<select onChange={(event) => { setGroupName(event.target.value); setNewGroup(''); }} value={groupName}>
            <option value="">-- 无分组 --</option>
            {groupOptions.map((group) => <option key={group} value={group}>{group}</option>)}
          </select></label>
          <label>或新建分组<input onChange={(event) => { setNewGroup(event.target.value); setGroupName(''); }} placeholder="例如 港股" value={newGroup} /></label>
          <button className="primary" disabled={busy} onClick={doUpdateGroup} type="button">更新分组</button>
        </div> : null}
        {message ? <div className="form-message">{message}</div> : null}
      </section>
    </div>
  );
}

function TabButton({ active, icon, text, onClick }: { active: boolean; icon: ReactNode; text: string; onClick: () => void }) {
  return <button className={active ? 'manager-tab active' : 'manager-tab'} onClick={onClick} type="button">{icon}<span>{text}</span></button>;
}
