import { Activity, Database, RefreshCw } from 'lucide-react';
import { useEffect, useState } from 'react';
import type { MarketBackfillStatus } from '../types';

interface Props {
  status?: MarketBackfillStatus | null;
  busy: boolean;
  error?: string;
  symbol: string;
  onCurrent: () => void;
  onAll: () => void;
}

export function BackfillControls({ status, busy, error, symbol, onCurrent, onAll }: Props) {
  const [notice, setNotice] = useState('');
  const running = status?.state === 'RUNNING' || busy;
  const finished = status?.state === 'DONE' || status?.state === 'FAILED';
  const noticeText = error || (finished ? formatBackfill(status) : '');
  const noticeKey = [
    noticeText,
    status?.state,
    status?.scope,
    status?.symbol,
    status?.startedAt,
    status?.finishedAt,
  ].join('|');
  const noticeTone = error || status?.state === 'FAILED' ? 'danger' : 'success';

  useEffect(() => {
    if (!noticeText) {
      setNotice('');
      return;
    }
    setNotice(noticeText);
    const timer = window.setTimeout(() => setNotice(''), 5000);
    return () => window.clearTimeout(timer);
  }, [noticeKey, noticeText]);

  return (
    <div className="backfill-box">
      <div className="backfill-actions">
        <button
          className="backfill-button"
          disabled={running || !symbol}
          onClick={onCurrent}
          title={`只回填 ${symbol} 的历史 K 线`}
          type="button"
        >
          <RefreshCw size={16} />
          <span>当前品种</span>
        </button>
        <button
          className="backfill-button"
          disabled={running}
          onClick={onAll}
          title="回填全部品种的历史 K 线"
          type="button"
        >
          <Database size={16} />
          <span>全部品种</span>
        </button>
      </div>
      {running && status ? (
        <div className="backfill-status running">
          <Activity className="backfill-spin" size={16} />
          <span>{formatBackfill(status)}</span>
        </div>
      ) : null}
      {notice ? (
        <div className={`backfill-toast ${noticeTone}`} role="status">
          {notice}
        </div>
      ) : null}
    </div>
  );
}

function formatBackfill(status?: MarketBackfillStatus | null) {
  if (!status) return '';
  const label = status.scope === 'SYMBOL' && status.symbol ? status.symbol : '全部品种';
  if (status.state === 'RUNNING') {
    return `${label}回填中：已完成 ${status.processed}/${status.total} 个周期，成功 ${status.success}，跳过 ${status.skipped}，失败 ${status.failed}，写入/覆盖 ${status.fetchedBars} 根`;
  }
  if (status.state === 'DONE') {
    const outcome = `${status.success}/${status.total} 个周期回填成功`;
    if (status.skipped === 0 && status.failed === 0) {
      return `${label}回填完成：${outcome}，写入/覆盖 ${status.fetchedBars} 根`;
    }
    return `${label}回填完成：${outcome}，跳过 ${status.skipped}，失败 ${status.failed}，写入/覆盖 ${status.fetchedBars} 根`;
  }
  if (status.state === 'FAILED') return status.message;
  return status.message;
}
