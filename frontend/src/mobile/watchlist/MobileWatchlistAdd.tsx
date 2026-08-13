import { Plus, X } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { createPortal } from 'react-dom';
import { api } from '../../api/client';

interface Props {
  kolId: string;
  onAdded: (symbol: string) => void;
  onClose: () => void;
}

export function MobileWatchlistAdd({ kolId, onAdded, onClose }: Props) {
  const [symbol, setSymbol] = useState('');
  const [name, setName] = useState('');
  const [market, setMarket] = useState('US');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalized = symbol.trim().toUpperCase();
    if (!normalized) {
      setError('请输入标的代码');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const item = await api.createInstrument({
        symbol: normalized,
        name: name.trim() || normalized,
        market,
        kolId,
        addToWatchlist: true,
      });
      onAdded(item.symbol);
      onClose();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '加入自选表失败');
    } finally {
      setBusy(false);
    }
  }

  return createPortal(
    <div className="modal-backdrop" onMouseDown={onClose}>
      <section aria-labelledby="watchlist-add-title" aria-modal="true" className="entry mobile-watchlist-add-modal" onMouseDown={(event) => event.stopPropagation()} role="dialog">
        <div className="modal-head">
          <div><div className="panel-title" id="watchlist-add-title">新增自选</div><p>输入代码后加入当前自选表。</p></div>
          <button aria-label="关闭" className="icon-button" onClick={onClose} type="button"><X size={18} /></button>
        </div>
        <form className="mobile-watchlist-add-form" onSubmit={(event) => void submit(event)}>
          <label>标的代码<input autoCapitalize="characters" autoFocus onChange={(event) => setSymbol(event.target.value.toUpperCase())} placeholder="例如 NVDA、BTC" value={symbol} /></label>
          <label>名称（可选）<input onChange={(event) => setName(event.target.value)} placeholder="例如 英伟达" value={name} /></label>
          <label>市场<select onChange={(event) => setMarket(event.target.value)} value={market}><option value="US">美股</option><option value="HK">港股</option><option value="CRYPTO">加密货币</option><option value="UNKNOWN">其他</option></select></label>
          {error ? <p className="mobile-watchlist-form-error">{error}</p> : null}
          <button className="primary" disabled={busy} type="submit"><Plus size={16} />{busy ? '正在加入…' : '加入自选表'}</button>
        </form>
      </section>
    </div>,
    document.body,
  );
}
