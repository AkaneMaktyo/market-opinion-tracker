import { AlertTriangle, Bell, Radar, RefreshCw } from 'lucide-react';
import { useEffect, useState } from 'react';
import { resonanceApi, type ResonanceView } from '../../api/resonance';
import './resonance.css';

export function ResonancePanel({ symbol }: { symbol: string }) {
  const [items, setItems] = useState<ResonanceView[]>([]);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');

  async function load() {
    setLoading(true);
    setMessage('');
    try {
      setItems(await resonanceApi.list(symbol, symbol ? 6 : 8));
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '读取共振雷达失败');
    } finally {
      setLoading(false);
    }
  }

  async function refresh() {
    if (!symbol) {
      setMessage('请先选择一个标的');
      return;
    }
    setLoading(true);
    setMessage('');
    try {
      setItems(await resonanceApi.refresh(symbol));
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '刷新共振失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, [symbol]);

  return (
    <section className="resonance-panel">
      <div className="resonance-head">
        <div>
          <span className="panel-title">共振雷达</span>
          <h2>{symbol || '全市场'}</h2>
        </div>
        <button className="icon-button" disabled={loading} onClick={() => void refresh()} type="button">
          <RefreshCw size={16} />
        </button>
      </div>
      {message ? <div className="resonance-message">{message}</div> : null}
      {items.length === 0 && !loading ? (
        <div className="resonance-empty">
          <Radar size={18} />
          暂无可展示共振，等更多独立来源确认。
        </div>
      ) : null}
      <div className="resonance-list">
        {items.map(({ cluster, items: detail }) => {
          const support = detail.filter((item) => item.role === 'SUPPORT');
          const conflict = detail.filter((item) => item.role === 'CONFLICT');
          return (
            <article className={`resonance-card ${cluster.grade.toLowerCase()}`} key={cluster.id}>
              <div className="resonance-score">
                <strong>{cluster.score}</strong>
                <span>{gradeLabel(cluster.grade)}</span>
              </div>
              <div className="resonance-body">
                <div className="resonance-meta">
                  <span className={`badge ${cluster.direction.toLowerCase()}`}>
                    {directionLabel(cluster.direction)}
                  </span>
                  <span>{cluster.horizon}</span>
                  <span>{trimTime(cluster.lastOpinionAt)}</span>
                </div>
                <h3>{cluster.action}</h3>
                <p>{cluster.summary}</p>
                <div className="resonance-factors">
                  <span>来源 {cluster.sourceCount}</span>
                  <span>支撑 {cluster.supportCount}</span>
                  <span>冲突 {cluster.conflictCount}</span>
                  <span>{alertLabel(cluster.alertStatus)}</span>
                </div>
                <SignalLine label="触发" value={cluster.triggerText} />
                <SignalLine label="失效" value={cluster.invalidationText} />
                <SourceStrip items={support} conflict={conflict.length} />
                {cluster.alertError ? (
                  <p className="resonance-alert">
                    <AlertTriangle size={14} />
                    {cluster.alertError}
                  </p>
                ) : null}
              </div>
            </article>
          );
        })}
      </div>
      {loading ? <div className="resonance-loading">正在刷新共振信号...</div> : null}
    </section>
  );
}

function SignalLine({ label, value }: { label: string; value?: string }) {
  if (!value) return null;
  return (
    <p className="signal-line">
      <span>{label}</span>
      {value}
    </p>
  );
}

function SourceStrip({ items, conflict }: { items: { sourceName: string; thesis?: string }[]; conflict: number }) {
  return (
    <div className="source-strip">
      {items.slice(0, 3).map((item) => (
        <span key={`${item.sourceName}-${item.thesis}`}>{item.sourceName}</span>
      ))}
      {conflict > 0 ? <span className="conflict">反向 {conflict}</span> : null}
    </div>
  );
}

function directionLabel(value: string) {
  return {
    BULLISH: '看多',
    BEARISH: '看空',
    RANGE: '震荡',
    WATCH: '观察',
  }[value] || value;
}

function gradeLabel(value: string) {
  return {
    STRONG: '强共振',
    ACTIONABLE: '可行动',
    WATCH: '观察',
  }[value] || value;
}

function alertLabel(value: string) {
  if (value === 'SENT') return <><Bell size={12} />已提醒</>;
  if (value === 'WAITING_CONFIG') return '待配置推送';
  if (value === 'FAILED') return '提醒失败';
  return '未提醒';
}

function trimTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '暂无时间';
}
