import { X } from 'lucide-react';
import type { KolPositionTrade, PositionView } from '../../positionTypes';

interface Props {
  orders: KolPositionTrade[];
  positions: PositionView[];
  onClose: (id: string) => void;
}

export function OrdersTable({ orders, positions, onClose }: Props) {
  if (orders.length === 0) {
    return (
      <section className="positions-section">
        <div className="panel-title">单子列表</div>
        <p className="muted">暂无单子。点击上方"从历史消息重建单子"可回放全部历史观点生成完整单子。</p>
      </section>
    );
  }
  const floating = new Map(
    positions
      .filter((item) => item.pnlPct != null)
      .map((item) => [item.position.symbol, item.pnlPct as number]),
  );
  return (
    <section className="positions-section">
      <div className="panel-title">单子列表（{orders.length}）</div>
      <table className="positions-table">
        <thead>
          <tr>
            <th>#</th><th>品种</th><th>方向</th><th>状态</th>
            <th>入场价</th><th>出场价</th><th>盈亏</th>
            <th>开仓时间</th><th>平仓时间</th><th>原因</th><th></th>
          </tr>
        </thead>
        <tbody>
          {orders.map((order, index) => {
            const running = !order.exitAt;
            const rawPnl = running ? floating.get(order.symbol) ?? null : order.pnlPct;
            const pnl = rawPnl == null ? null : Number(rawPnl);
            const livePosition = running
              ? positions.find((item) => item.position.instrumentId === order.instrumentId)
              : undefined;
            return (
              <tr className={running ? 'order-running' : ''} key={order.id}>
                <td className="muted">{orders.length - index}</td>
                <td><strong>{order.symbol}</strong></td>
                <td>{order.direction === 'SHORT' ? '空' : '多'}</td>
                <td>
                  <span className={running ? 'status-pill active' : 'status-pill'}>
                    {running ? '进行中' : '已平仓'}
                  </span>
                </td>
                <td>{price(order.entryPrice)}</td>
                <td>{price(order.exitPrice)}</td>
                <td className={pnlClass(pnl)}>{pnl == null ? '待价' : formatPct(pnl)}</td>
                <td className="muted">{shortTime(order.entryAt)}</td>
                <td className="muted">{shortTime(order.exitAt)}</td>
                <td className="muted">{order.exitReason || '—'}</td>
                <td>
                  {livePosition ? (
                    <button
                      className="icon-button"
                      onClick={() => void onClose(livePosition.position.id)}
                      title="按最新收盘价平仓"
                      type="button"
                    >
                      <X size={14} />
                    </button>
                  ) : null}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </section>
  );
}

function price(value?: number | null) {
  const num = typeof value === 'string' ? Number(value) : value;
  if (num == null || Number.isNaN(num)) return '—';
  return num % 1 === 0 ? String(num) : num.toFixed(2);
}

function pnlClass(value?: number | null) {
  const num = typeof value === 'string' ? Number(value) : value;
  if (num == null || Number.isNaN(num) || num === 0) return 'pnl-flat';
  return num > 0 ? 'pnl-pos' : 'pnl-neg';
}

function formatPct(value: number) {
  const num = typeof value === 'string' ? Number(value) : value;
  return `${num > 0 ? '+' : ''}${num.toFixed(2)}%`;
}

function shortTime(value?: string | null) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—';
}
