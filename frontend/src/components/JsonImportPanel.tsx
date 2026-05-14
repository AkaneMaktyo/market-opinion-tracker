import { FileJson, Save } from 'lucide-react';
import { useState } from 'react';
import { api } from '../api/client';
import type { Direction, ImportCandidate, ImportPreview } from '../types';

interface Props {
  kolId: string;
  onImported: (symbol: string) => void;
}

const today = new Date().toISOString().slice(0, 10);
const directions: { value: Direction; label: string }[] = [
  { value: 'BULLISH', label: '看多' },
  { value: 'BEARISH', label: '看空' },
  { value: 'RANGE', label: '震荡' },
  { value: 'WATCH', label: '观望' },
];

export function JsonImportPanel({ kolId, onImported }: Props) {
  const [sessionDate, setSessionDate] = useState(today);
  const [title, setTitle] = useState('美股直播 JSON 导入');
  const [rawJson, setRawJson] = useState('');
  const [preview, setPreview] = useState<ImportPreview | null>(null);
  const [busy, setBusy] = useState(false);

  async function previewJson() {
    if (!rawJson.trim()) return;
    setBusy(true);
    try {
      setPreview(await api.previewImport({ kolId, title, sessionDate, rawJson }));
    } finally {
      setBusy(false);
    }
  }

  async function commit() {
    if (!preview) return;
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
      onImported(first);
    } finally {
      setBusy(false);
    }
  }

  function update(index: number, next: Partial<ImportCandidate>) {
    if (!preview) return;
    const candidates = preview.candidates.map((item, itemIndex) =>
      itemIndex === index ? { ...item, ...next } : item,
    );
    setPreview({ ...preview, candidates });
  }

  return (
    <section className="entry">
      <div className="panel-title">JSON 批量导入</div>
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
      <textarea
        className="json-input"
        value={rawJson}
        onChange={(event) => setRawJson(event.target.value)}
        placeholder="粘贴 KOL 直播 JSON"
      />
      <div className="import-actions">
        <button className="primary" onClick={previewJson} disabled={busy}>
          <FileJson size={16} />
          解析预览
        </button>
        <button className="primary secondary" onClick={commit} disabled={busy || !preview}>
          <Save size={16} />
          保存选中
        </button>
      </div>
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
