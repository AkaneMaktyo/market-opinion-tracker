import { Save } from 'lucide-react';
import { useState } from 'react';
import { api } from '../api/client';
import type { LiveSession, PriceLevel } from '../types';

interface Props {
  activeSession?: LiveSession;
  onCreated: (session: LiveSession, symbol: string) => void;
}

const today = new Date().toISOString().slice(0, 10);

export function SessionForm({ activeSession, onCreated }: Props) {
  const [sessionDate, setSessionDate] = useState(today);
  const [title, setTitle] = useState('美股直播');
  const [rawText, setRawText] = useState('');
  const [symbol, setSymbol] = useState('NVDA');
  const [direction, setDirection] = useState('BULLISH');
  const [horizon, setHorizon] = useState('短线');
  const [thesis, setThesis] = useState('');
  const [support, setSupport] = useState('');
  const [target, setTarget] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit() {
    if (!symbol.trim() || !thesis.trim()) {
      return;
    }
    setBusy(true);
    try {
      const session = activeSession || await api.createSession({
        sessionDate,
        title,
        source: '手动录入',
        rawText: rawText || thesis,
      });
      await api.createOpinion({
        sessionId: session.id,
        symbol,
        direction,
        horizon,
        thesis,
        sourceQuote: rawText,
        opinionTime: `${sessionDate}T09:30:00`,
        priceLevels: levels(),
      });
      setThesis('');
      setSupport('');
      setTarget('');
      onCreated(session, symbol.toUpperCase());
    } finally {
      setBusy(false);
    }
  }

  function levels(): PriceLevel[] {
    return [
      support ? { levelType: 'SUPPORT', price: Number(support), note: '支撑' } : null,
      target ? { levelType: 'TARGET', price: Number(target), note: '目标' } : null,
    ].filter(Boolean) as PriceLevel[];
  }

  return (
    <section className="entry">
      <div className="panel-title">录入直播观点</div>
      <div className="form-grid two">
        <label>
          日期
          <input value={sessionDate} onChange={(event) => setSessionDate(event.target.value)} />
        </label>
        <label>
          标题
          <input value={title} onChange={(event) => setTitle(event.target.value)} />
        </label>
      </div>
      <label>
        直播原文
        <textarea
          value={rawText}
          onChange={(event) => setRawText(event.target.value)}
          placeholder="粘贴直播文本，第一版先保留原文并手动确认结构化观点"
        />
      </label>
      <div className="form-grid two">
        <label>
          品种
          <input value={symbol} onChange={(event) => setSymbol(event.target.value)} />
        </label>
        <label>
          方向
          <select value={direction} onChange={(event) => setDirection(event.target.value)}>
            <option value="BULLISH">看多</option>
            <option value="BEARISH">看空</option>
            <option value="RANGE">震荡</option>
            <option value="WATCH">观望</option>
          </select>
        </label>
      </div>
      <div className="form-grid three">
        <label>
          周期
          <input value={horizon} onChange={(event) => setHorizon(event.target.value)} />
        </label>
        <label>
          支撑
          <input value={support} onChange={(event) => setSupport(event.target.value)} />
        </label>
        <label>
          目标
          <input value={target} onChange={(event) => setTarget(event.target.value)} />
        </label>
      </div>
      <label>
        核心观点
        <textarea
          value={thesis}
          onChange={(event) => setThesis(event.target.value)}
          placeholder="例：回踩支撑不破，突破前高后继续看多"
        />
      </label>
      <button className="primary" onClick={submit} disabled={busy}>
        <Save size={16} />
        保存观点
      </button>
    </section>
  );
}
