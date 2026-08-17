import type { PositionStats } from '../../positionTypes';

interface Props {
  stats: PositionStats | null;
}

export function StatsCards({ stats }: Props) {
  if (!stats) {
    return <div className="stats-cards muted">统计加载中…</div>;
  }
  return (
    <div className="stats-cards">
      <StatCard label="胜率" value={stats.winRate == null ? '—' : `${Number(stats.winRate).toFixed(1)}%`} hint={`${stats.wins} 胜 / ${stats.losses} 负`} />
      <StatCard label="已结算" value={String(stats.settledTrades)} hint={`共 ${stats.totalTrades} 笔记录`} />
      <StatCard label="单笔均值" value={formatPct(stats.avgPnlPct)} tone={tone(stats.avgPnlPct)} />
      <StatCard label="累计盈亏" value={formatPct(stats.totalPnlPct)} tone={tone(stats.totalPnlPct)} />
      <StatCard label="最佳" value={formatPct(stats.bestPnlPct)} tone="pos" />
      <StatCard label="最差" value={formatPct(stats.worstPnlPct)} tone="neg" />
      <StatCard label="当前持仓" value={String(stats.activeCount)} hint="按观点虚拟持有" />
    </div>
  );
}

function StatCard({ label, value, hint, tone }: {
  label: string;
  value: string;
  hint?: string;
  tone?: 'pos' | 'neg' | 'flat';
}) {
  return (
    <div className={`stat-card${tone ? ` ${tone}` : ''}`}>
      <span className="stat-label">{label}</span>
      <strong className="stat-value">{value}</strong>
      {hint ? <span className="stat-hint">{hint}</span> : null}
    </div>
  );
}

function formatPct(value?: number | null) {
  const num = typeof value === 'string' ? Number(value) : value;
  if (num == null || Number.isNaN(num)) return '—';
  return `${num > 0 ? '+' : ''}${num.toFixed(2)}%`;
}

function tone(value?: number | null): 'pos' | 'neg' | 'flat' | undefined {
  const num = typeof value === 'string' ? Number(value) : value;
  if (num == null || Number.isNaN(num) || num === 0) return 'flat';
  return num > 0 ? 'pos' : 'neg';
}
