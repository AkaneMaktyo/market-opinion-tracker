import { History, RefreshCw } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { Kol } from '../../types';
import type { KolPositionTrade, PositionStats, PositionView } from '../../positionTypes';
import { StatsCards } from './StatsCards';
import { OrdersTable } from './OrdersTable';

export function PositionsWorkspace() {
  const [kols, setKols] = useState<Kol[]>([]);
  const [kolId, setKolId] = useState('');
  const [stats, setStats] = useState<PositionStats | null>(null);
  const [trades, setTrades] = useState<KolPositionTrade[]>([]);
  const [positions, setPositions] = useState<PositionView[]>([]);
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const [rebuilding, setRebuilding] = useState(false);

  useEffect(() => {
    void (async () => {
      try {
        const list = await api.kols();
        setKols(list);
        setKolId((current) => current || list[0]?.id || 'default');
      } catch (error) {
        setMessage(error instanceof Error ? error.message : '读取 KOL 失败');
      }
    })();
  }, []);

  const reload = useCallback(async (target = kolId) => {
    if (!target) return;
    setLoading(true);
    setMessage('');
    try {
      const [nextStats, nextTrades, nextPositions] = await Promise.all([
        api.positionStats(target),
        api.positionTrades(target, 300),
        api.positions(target, false),
      ]);
      setStats(nextStats);
      setTrades(nextTrades);
      setPositions(nextPositions);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '读取单子失败');
    } finally {
      setLoading(false);
    }
  }, [kolId]);

  useEffect(() => {
    void reload(kolId);
  }, [kolId, reload]);

  async function rebuild() {
    setRebuilding(true);
    setMessage('');
    try {
      const result = await api.rebuildPositionTrades(kolId);
      await reload(kolId);
      const source = result.sourceInclude ? `（仅统计来源含“${result.sourceInclude}”的消息）` : '';
      setMessage(`已重放 ${result.scannedOpinions} 条历史观点${source}：${result.totalTrades} 笔单子（${result.settledTrades} 已平仓 / ${result.runningTrades} 进行中），清理非本源持仓 ${result.removedPositions} 个`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '重建失败');
    } finally {
      setRebuilding(false);
    }
  }

  async function closePosition(id: string) {
    setMessage('');
    try {
      await api.closePosition(id);
      await rebuild();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '平仓失败');
    }
  }

  return (
    <div className="positions-workspace">
      <div className="positions-toolbar">
        <label>
          KOL
          <select onChange={(event) => setKolId(event.target.value)} value={kolId}>
            {kols.map((kol) => (
              <option key={kol.id} value={kol.id}>{kol.name}</option>
            ))}
          </select>
        </label>
        <button className="primary" disabled={rebuilding || !kolId} onClick={() => void rebuild()} type="button">
          <History size={16} />
          {rebuilding ? '重建中…' : '从历史消息重建单子'}
        </button>
        <button className="primary secondary" disabled={loading} onClick={() => void reload()} type="button">
          <RefreshCw size={16} />
          刷新
        </button>
        <span className="muted">{loading ? '加载中…' : `单子 ${trades.length} 笔（进行中 ${trades.filter((t) => !t.exitAt).length}）`}</span>
      </div>
      <StatsCards stats={stats} />
      <OrdersTable onClose={closePosition} orders={trades} positions={positions} />
      {message ? <div className="form-message">{message}</div> : null}
    </div>
  );
}
