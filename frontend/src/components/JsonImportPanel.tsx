import { FileJson, Save, X } from 'lucide-react';
import { useState } from 'react';
import { api } from '../api/client';
import type { PositionAction } from '../positionTypes';
import type { Direction, ImportCandidate, ImportPreview } from '../types';
interface Props {
  kolId: string;
  onImported: (symbol: string) => void;
}
const directions: { value: Direction; label: string }[] = [
  { value: 'BULLISH', label: '看多' },
  { value: 'BEARISH', label: '看空' },
  { value: 'RANGE', label: '震荡' },
  { value: 'WATCH', label: '观望' },
];
const positionActions: { value: PositionAction; label: string }[] = [
  { value: 'IGNORE', label: '不改持仓' },
  { value: 'OPEN', label: '加入持仓' },
  { value: 'CLOSE', label: '移出持仓' },
];

export function JsonImportPanel({ kolId, onImported }: Props) {
  const [open, setOpen] = useState(false);
  const [sessionDate, setSessionDate] = useState('');
  const [title, setTitle] = useState('观点录入');
  const [rawJson, setRawJson] = useState('');
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  async function previewJson() {
    if (!canImport()) return;
    setBusy(true);
    try {
      setPreview(await api.previewImport({ kolId, title, sessionDate, rawJson }));
      setMessage('');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'JSON 解析失败');
    } finally {
      setBusy(false);
    }
  }

  async function commit() {
    if (!preview || !canImport()) return;
    setBusy(true);
    try {
      await api.commitImport({
        kolId,
        title,
        sessionDate,
        rawJson,
        items: preview.candidates.filter((item) => item.selected),
      });
      const first = preview.candidates.find((item) => item.selected)?.symbol || 'NVDA';
      setPreview(null);
      setRawJson('');
      setMessage('已保存选中的 JSON 观点');
      setOpen(false);
      onImported(first);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '保存失败');
    } finally {
      setBusy(false);
    }
  }

  function canImport() {
    if (!sessionDate.trim()) {
      setMessage('请先选择直播时间节点');
      return false;
    }
    if (!rawJson.trim()) {
      setMessage('请先粘贴 JSON 内容');
      return false;
    }
    return true;
  }

  function update(index: number, next: Partial<ImportCandidate>) {
    if (!preview) return;
    const candidates = preview.candidates.map((item, itemIndex) =>
      itemIndex === index ? { ...item, ...next } : item,
    );
    setPreview({ ...preview, candidates });
  }

  return (
    <>
      <button className="primary" onClick={() => setOpen(true)} type="button">
        <FileJson size={16} />
        观点录入
      </button>
      {open && (
        <div className="modal-backdrop" onMouseDown={() => setOpen(false)}>
          <section className="entry import-modal" onMouseDown={(event) => event.stopPropagation()}>
            <div className="modal-head">
              <div>
                <div className="panel-title">观点录入</div>
                <p>粘贴直播 JSON，解析后保存为历史观点</p>
              </div>
              <button className="icon-button" onClick={() => setOpen(false)} type="button">
                <X size={18} />
              </button>
            </div>
      <div className="form-grid two">
        <label>
          直播时间节点
          <input
            type="date"
            value={sessionDate}
            onChange={(event) => {
              setSessionDate(event.target.value);
              setMessage('');
            }}
            required
          />
        </label>
        <label>
          标题
          <input value={title} onChange={(event) => setTitle(event.target.value)} />
        </label>
      </div>
      <textarea
        className="json-input"
        value={rawJson}
        onChange={(event) => setRawJson(event.target.value)}
        placeholder="粘贴 KOL 直播 JSON"
      />
      <div className="import-actions">
        <button className="primary" onClick={previewJson} disabled={busy || !sessionDate}>
          <FileJson size={16} />
          解析预览
        </button>
        <button className="primary secondary" onClick={commit} disabled={busy || !preview || !sessionDate}>
          <Save size={16} />
          保存选中
        </button>
      </div>
      {message && <div className="form-message">{message}</div>}
      {preview && (
        <div className="import-preview">
          <PreviewNotes preview={preview} />
          {preview.candidates.map((item, index) => (
            <article className="candidate" key={`${item.displayName}-${index}`}>
              <input
                type="checkbox"
                checked={item.selected}
                onChange={(event) => update(index, { selected: event.target.checked })}
              />
              <input value={item.symbol} onChange={(event) => update(index, { symbol: event.target.value })} />
              <select
                value={item.direction}
                onChange={(event) => update(index, { direction: event.target.value as Direction })}
              >
                {directions.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
              <input
                value={item.horizon || ''}
                onChange={(event) => update(index, { horizon: event.target.value })}
                placeholder="周期"
              />
              <select
                value={item.positionAction || 'IGNORE'}
                onChange={(event) => update(index, { positionAction: event.target.value as PositionAction })}
              >
                {positionActions.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
              <textarea
                value={item.thesis}
                onChange={(event) => update(index, { thesis: event.target.value })}
              />
              <span>{item.displayName}</span>
            </article>
          ))}
        </div>
      )}
          </section>
        </div>
      )}
    </>
  );
}

function PreviewNotes({ preview }: { preview: ImportPreview }) {
  return (
    <div className="preview-notes">
      <strong>{preview.candidates.length} 条候选观点</strong>
      {preview.skipped.length > 0 && <span>{preview.skipped.length} 条已跳过</span>}
      {preview.summary.slice(0, 3).map((line) => <p key={line}>{line}</p>)}
      {preview.skipped.map((item) => (
        <p key={item.name}>跳过：{item.name || '未命名'}，{item.reason}</p>
      ))}
    </div>
  );
}
