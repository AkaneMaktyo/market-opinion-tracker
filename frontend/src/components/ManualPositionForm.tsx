import { Plus } from 'lucide-react';
import type { FormEvent } from 'react';
import { useState } from 'react';
import { api } from '../api/client';

interface Props {
  kolId: string;
  onAdded: (symbol: string) => void;
}

export function ManualPositionForm({ kolId, onAdded }: Props) {
  const [symbol, setSymbol] = useState('');
  const [direction, setDirection] = useState('LONG');
  const [entryPrice, setEntryPrice] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = symbol.trim().toUpperCase();
    if (!value) {
      setMessage('请先填写持仓代码');
      return;
    }
    setBusy(true);
    setMessage('');
    try {
      await api.openPosition({
        kolId,
        symbol: value,
        direction,
        entryPrice: entryPrice.trim() ? Number(entryPrice) : undefined,
      });
      setSymbol('');
      setEntryPrice('');
      setMessage(`已加入：${value}`);
      onAdded(value);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '添加持仓失败');
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="manual-position" onSubmit={submit}>
      <input
        aria-label="持仓代码"
        onChange={(event) => setSymbol(event.target.value)}
        placeholder="持仓代码"
        value={symbol}
      />
      <select aria-label="方向" onChange={(event) => setDirection(event.target.value)} value={direction}>
        <option value="LONG">多</option>
        <option value="SHORT">空</option>
      </select>
      <input
        aria-label="入场价（可选）"
        onChange={(event) => setEntryPrice(event.target.value)}
        placeholder="入场价（可选）"
        type="number"
        value={entryPrice}
      />
      <button className="icon-button" disabled={busy} title="添加持仓" type="submit">
        <Plus size={16} />
      </button>
      {message ? <span className="manual-position-message">{message}</span> : null}
    </form>
  );
}
