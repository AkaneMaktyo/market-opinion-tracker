import { CheckCircle2, LoaderCircle, X } from 'lucide-react';
import { useMemo, useState } from 'react';
import { createPortal } from 'react-dom';
import { api } from '../../api/client';
import type {
  PriceAlertBatchItemResult,
  PriceAlertRecognitionCandidate,
  PriceAlertRecognitionResult,
  PriceAlertTriggerDirection,
} from '../../types/alerts';

interface Props {
  result: PriceAlertRecognitionResult;
  kolId: string;
  onClose: () => void;
  onWatchlistChanged: () => void;
}

type Draft = PriceAlertRecognitionCandidate & { selected: boolean };

export function PriceAlertRecognitionSheet({ result, kolId, onClose, onWatchlistChanged }: Props) {
  const [drafts, setDrafts] = useState<Draft[]>(() => result.candidates.map((item) => ({ ...item, selected: false })));
  const [created, setCreated] = useState<Record<string, PriceAlertBatchItemResult>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const groups = useMemo(() => groupCandidates(drafts), [drafts]);
  const selectedCount = drafts.filter((item) => item.selected && !created[item.candidateId]).length;

  function patchCandidate(candidateId: string, patch: Partial<Draft>) {
    setDrafts((current) => current.map((item) => item.candidateId === candidateId ? { ...item, ...patch } : item));
  }

  async function createSelected() {
    const selected = drafts.filter((item) => item.selected && !created[item.candidateId]);
    if (selected.length === 0) {
      setError('请先勾选需要创建的价格提醒');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const response = await api.createPriceAlertsBatch(result.recognitionId, kolId, selected.map((item) => ({
        candidateId: item.candidateId,
        instrumentName: item.instrumentName,
        symbol: item.symbol.trim().toUpperCase(),
        market: item.market,
        alertType: item.alertType,
        triggerDirection: item.alertType === 'POINT' ? item.triggerDirection : 'ANY',
        lowerPrice: numberOrUndefined(item.lowerPrice),
        upperPrice: numberOrUndefined(item.upperPrice),
        targetPrice: numberOrUndefined(item.targetPrice),
      })));
      setCreated((current) => ({
        ...current,
        ...Object.fromEntries(response.items.map((item) => [item.candidateId, item])),
      }));
      if (response.items.some((item) => item.status === 'CREATED' || item.status === 'EXISTS')) {
        onWatchlistChanged();
      }
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : '批量创建价格提醒失败');
    } finally {
      setBusy(false);
    }
  }

  return createPortal(
    <div className="modal-backdrop recognition-backdrop" onMouseDown={onClose}>
      <section aria-labelledby="recognition-title" aria-modal="true" className="recognition-sheet" onMouseDown={(event) => event.stopPropagation()} role="dialog">
        <div className="recognition-handle" />
        <header className="recognition-head">
          <div>
            <strong id="recognition-title">添加价格提醒</strong>
            <small>识别到 {drafts.length} 个候选，默认均未勾选</small>
          </div>
          <button aria-label="关闭" className="icon-button" onClick={onClose} type="button"><X size={19} /></button>
        </header>

        <div className="recognition-scroll">
          {result.warnings.map((warning) => <p className="recognition-warning" key={warning}>{warning}</p>)}
          {groups.map(([group, items]) => (
            <section className="recognition-group" key={group}>
              <h3>{group}</h3>
              {items.map((item) => (
                <CandidateEditor
                  item={item}
                  key={item.candidateId}
                  onChange={(patch) => patchCandidate(item.candidateId, patch)}
                  outcome={created[item.candidateId]}
                />
              ))}
            </section>
          ))}
          {error ? <p className="recognition-error">{error}</p> : null}
        </div>

        <footer className="recognition-footer">
          <span>已选择 {selectedCount} 项</span>
          <button className="primary" disabled={busy || selectedCount === 0} onClick={() => void createSelected()} type="button">
            {busy ? <LoaderCircle className="spinning" size={16} /> : <CheckCircle2 size={16} />}
            {busy ? '正在创建…' : `创建 ${selectedCount} 个提醒`}
          </button>
        </footer>
      </section>
    </div>,
    document.body,
  );
}

function CandidateEditor({ item, outcome, onChange }: {
  item: Draft;
  outcome?: PriceAlertBatchItemResult;
  onChange: (patch: Partial<Draft>) => void;
}) {
  const disabled = Boolean(outcome && outcome.status !== 'FAILED');
  return (
    <article className={`recognition-candidate ${outcome ? `is-${outcome.status.toLowerCase()}` : ''}`}>
      <label className="recognition-select">
        <input checked={item.selected} disabled={disabled} onChange={(event) => onChange({ selected: event.target.checked })} type="checkbox" />
        <span>{categoryLabel(item.category)}</span>
        <b>{priceLabel(item)}</b>
      </label>
      <div className="recognition-fields">
        <label>代码<input onChange={(event) => onChange({ symbol: event.target.value.toUpperCase() })} value={item.symbol} /></label>
        <label>类型
          <select onChange={(event) => onChange({ alertType: event.target.value as 'POINT' | 'RANGE' })} value={item.alertType}>
            <option value="POINT">单点</option><option value="RANGE">区间</option>
          </select>
        </label>
        {item.alertType === 'POINT' ? (
          <>
            <label>点位<input min="0" onChange={(event) => onChange({ targetPrice: valueOrUndefined(event.target.value) })} step="any" type="number" value={item.targetPrice ?? ''} /></label>
            <label>方向
              <select onChange={(event) => onChange({ triggerDirection: event.target.value as PriceAlertTriggerDirection })} value={item.triggerDirection}>
                <option value="ANY">任意穿越</option><option value="UP">向上突破</option><option value="DOWN">向下跌破</option>
              </select>
            </label>
          </>
        ) : (
          <>
            <label>下限<input min="0" onChange={(event) => onChange({ lowerPrice: valueOrUndefined(event.target.value) })} step="any" type="number" value={item.lowerPrice ?? ''} /></label>
            <label>上限<input min="0" onChange={(event) => onChange({ upperPrice: valueOrUndefined(event.target.value) })} step="any" type="number" value={item.upperPrice ?? ''} /></label>
          </>
        )}
      </div>
      <div className="recognition-meta">
        <span>{item.instrumentName || item.symbol}</span><span>{item.market || '未知市场'}</span>
        <span>{item.source === 'OCR' ? '来自图片 OCR' : '来自正文'}</span>
      </div>
      {item.sourceQuote ? <blockquote>{item.sourceQuote}</blockquote> : null}
      {item.note ? <p>{item.note}</p> : null}
      {outcome ? <p className={`recognition-outcome ${outcome.status.toLowerCase()}`}>{outcome.message}</p> : null}
    </article>
  );
}

function groupCandidates(items: Draft[]) {
  const groups = new Map<string, Draft[]>();
  items.forEach((item) => {
    const label = `${item.instrumentName || item.symbol || '待确认标的'}${item.symbol ? ` · ${item.symbol}` : ''}`;
    groups.set(label, [...(groups.get(label) || []), item]);
  });
  return [...groups.entries()];
}

function categoryLabel(value: string) {
  return ({ SUPPORT: '支撑', RESISTANCE: '压力', ENTRY: '入场', ADD: '加仓', TAKE_PROFIT: '止盈', STOP_LOSS: '止损', TARGET: '目标', CURRENT: '当前价', HISTORICAL: '历史价', OTHER: '其他价格' } as Record<string, string>)[value] || value || '价格';
}

function priceLabel(item: Draft) {
  return item.alertType === 'RANGE' ? `${item.lowerPrice ?? '?'} ～ ${item.upperPrice ?? '?'}` : String(item.targetPrice ?? '?');
}

function valueOrUndefined(value: string) {
  return value === '' ? undefined : Number(value);
}

function numberOrUndefined(value?: number) {
  return value == null || !Number.isFinite(Number(value)) ? undefined : Number(value);
}
