import { Save, X } from 'lucide-react';
import { type FormEvent, useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { Direction, PriceLevel } from '../../types';
import type { DashboardModel } from './mobileTypes';

interface Props {
  dashboard: DashboardModel;
  open: boolean;
  seed: string;
  onClose: () => void;
  onCreated: () => void;
}

export function MobileComposer({ dashboard, open, seed, onClose, onCreated }: Props) {
  const [symbol, setSymbol] = useState(dashboard.selected);
  const [direction, setDirection] = useState<Direction>('WATCH');
  const [horizon, setHorizon] = useState('短线');
  const [thesis, setThesis] = useState(seed);
  const [support, setSupport] = useState('');
  const [target, setTarget] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!open) return;
    setSymbol(dashboard.selected);
    setThesis(seed);
    setMessage('');
  }, [dashboard.selected, open, seed]);

  if (!open) return null;

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!symbol || !thesis.trim()) {
      setMessage('请选择标的并填写观点内容');
      return;
    }
    setBusy(true);
    setMessage('');
    try {
      const now = new Date();
      const session = await api.createSession({
        kolId: dashboard.selectedKol,
        sessionDate: now.toISOString().slice(0, 10),
        title: '手机手动记录',
        source: 'ANDROID',
        rawText: thesis.trim(),
      });
      await api.createOpinion({
        sessionId: session.id,
        symbol,
        direction,
        horizon,
        thesis: thesis.trim(),
        opinionTime: now.toISOString(),
        priceLevels: buildLevels(support, target),
      });
      dashboard.reload(symbol);
      onCreated();
      onClose();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '保存观点失败');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop mobile-composer-backdrop" data-mobile-overlay onMouseDown={onClose}>
      <form className="mobile-composer" onMouseDown={(event) => event.stopPropagation()} onSubmit={(event) => void submit(event)}>
        <div className="mobile-sheet-handle" />
        <div className="mobile-sheet-title"><div><h2>手动记录观点</h2><small>只保留最常用的信息，之后仍可继续完善</small></div><button aria-label="关闭" onClick={onClose} type="button"><X size={20} /></button></div>
        <div className="mobile-form-grid two">
          <label>标的<select onChange={(event) => setSymbol(event.target.value)} value={symbol}>{dashboard.instruments.map((item) => <option key={item.id} value={item.symbol}>{item.symbol} {item.name || ''}</option>)}</select></label>
          <label>方向<select onChange={(event) => setDirection(event.target.value as Direction)} value={direction}><option value="BULLISH">看多</option><option value="BEARISH">看空</option><option value="RANGE">震荡</option><option value="WATCH">观察</option></select></label>
        </div>
        <label>周期<input onChange={(event) => setHorizon(event.target.value)} placeholder="例如：短线 / 中线" value={horizon} /></label>
        <label>观点<textarea autoFocus onChange={(event) => setThesis(event.target.value)} placeholder="写下核心判断、触发条件和风险" value={thesis} /></label>
        <div className="mobile-form-grid two"><label>支撑位（可选）<input inputMode="decimal" onChange={(event) => setSupport(event.target.value)} value={support} /></label><label>目标位（可选）<input inputMode="decimal" onChange={(event) => setTarget(event.target.value)} value={target} /></label></div>
        {message ? <div className="form-message">{message}</div> : null}
        <button className="mobile-save-button" disabled={busy} type="submit"><Save size={18} />{busy ? '正在保存…' : '保存观点'}</button>
      </form>
    </div>
  );
}

function buildLevels(support: string, target: string): PriceLevel[] {
  const levels: PriceLevel[] = [];
  const supportPrice = Number(support);
  const targetPrice = Number(target);
  if (Number.isFinite(supportPrice) && supportPrice > 0) levels.push({ levelType: 'SUPPORT', price: supportPrice });
  if (Number.isFinite(targetPrice) && targetPrice > 0) levels.push({ levelType: 'TARGET', price: targetPrice });
  return levels;
}
