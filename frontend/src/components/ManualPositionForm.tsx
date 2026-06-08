import { Plus } from 'lucide-react';
import type { FormEvent } from 'react';
import { useEffect, useState } from 'react';
import { api } from '../api/client';

interface Props {
  kolId: string;
  defaultSymbol: string;
  onAdded: (symbol: string) => void;
}

export function ManualPositionForm({ kolId, defaultSymbol, onAdded }: Props) {
  const [symbol, setSymbol] = useState(defaultSymbol);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!symbol && defaultSymbol) {
      setSymbol(defaultSymbol);
    }
  }, [defaultSymbol, symbol]);

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
      await api.openPosition({ kolId, symbol: value });
      setSymbol(value);
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
      <button className="icon-button" disabled={busy} title="添加持仓" type="submit">
        <Plus size={16} />
      </button>
      {message ? <span className="manual-position-message">{message}</span> : null}
    </form>
  );
}
