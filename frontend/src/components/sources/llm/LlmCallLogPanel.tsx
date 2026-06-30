import { RefreshCw } from 'lucide-react';
import { useEffect, useState } from 'react';
import { api } from '../../../api/client';
import type { LlmCallLog, LlmSceneSummary } from '../../../types/llm';

export function LlmCallLogPanel() {
  const [date, setDate] = useState(today());
  const [logs, setLogs] = useState<LlmCallLog[]>([]);
  const [summary, setSummary] = useState<LlmSceneSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => {
    void load(date);
  }, [date]);

  async function load(nextDate: string) {
    setLoading(true);
    try {
      const [nextLogs, nextSummary] = await Promise.all([
        api.llmLogs(nextDate, 50),
        api.llmSummary(nextDate),
      ]);
      setLogs(nextLogs);
      setSummary(nextSummary);
      setMessage('');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '读取 LLM 调用记录失败');
    } finally {
      setLoading(false);
    }
  }

  return (
    <section className="source-panel">
      <div className="modal-head">
        <div>
          <div className="panel-title">LLM 调用记录</div>
          <p className="muted">按日期查看所有大模型调用，包括成功、失败和健康检查。</p>
        </div>
        <button className="icon-button" disabled={loading} onClick={() => void load(date)} title="刷新" type="button">
          <RefreshCw size={16} />
        </button>
      </div>

      <div className="form-grid two">
        <label>
          日期
          <input onChange={(event) => setDate(event.target.value)} type="date" value={date} />
        </label>
        <div>
          <span className="muted">总调用</span>
          <strong>{logs.length}</strong>
        </div>
      </div>

      <div className="status-grid">
        {summary.map((item) => (
          <div className="status-item" key={`${item.scene}-${item.status}`}>
            <span>{item.scene} / {item.status}</span>
            <strong>{item.totalCount}</strong>
          </div>
        ))}
        {summary.length === 0 ? <p className="muted">当天还没有调用记录。</p> : null}
      </div>

      <div className="message-audit">
        {logs.map((item) => (
          <div className="message-row" key={item.id}>
            <div>
              <strong>{item.scene} / {item.status}</strong>
              <p>{item.model} · {trimTime(item.createdAt)} · {item.durationMs}ms</p>
              <p className="muted">
                请求 {item.requestChars} 字 · 响应 {item.responseChars} 字
                {item.errorMessage ? ` · ${item.errorMessage}` : ''}
              </p>
              {item.requestPreview ? <p className="muted">请求：{item.requestPreview}</p> : null}
              {item.responsePreview ? <p className="muted">响应：{item.responsePreview}</p> : null}
            </div>
          </div>
        ))}
        {logs.length === 0 ? <p className="muted">当天还没有调用记录。</p> : null}
      </div>

      {message ? <div className="form-message">{message}</div> : null}
    </section>
  );
}

function trimTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '暂无';
}

function today() {
  return new Date().toISOString().slice(0, 10);
}
