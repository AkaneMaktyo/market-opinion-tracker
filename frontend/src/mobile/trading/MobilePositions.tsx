import { Check, PencilLine, RefreshCw, ShieldCheck, Trash2, WalletCards, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { PositionPortfolio, SignalTradingStatus, SpotPosition } from '../../types/trading';

const REFRESH_MS = 15000;

export function MobilePositions() {
  const [portfolio, setPortfolio] = useState<PositionPortfolio>();
  const [status, setStatus] = useState<SignalTradingStatus>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState('');
  const [averageCost, setAverageCost] = useState('');
  const [savingCost, setSavingCost] = useState(false);

  async function load(refresh = false) {
    setLoading(true);
    try {
      const [nextStatus, nextPortfolio] = await Promise.all([
        api.signalTradingStatus(),
        api.spotPositions(refresh),
      ]);
      setStatus(nextStatus);
      setPortfolio(nextPortfolio);
      setError('');
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '持仓读取失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load(false);
    const timer = window.setInterval(() => {
      if (!document.hidden) void load(false);
    }, REFRESH_MS);
    return () => window.clearInterval(timer);
  }, []);

  function startEditing(position: SpotPosition) {
    setEditing(positionKey(position));
    setAverageCost(position.averageCost ? String(position.averageCost) : '');
    setError('');
  }

  async function saveCost(position: SpotPosition) {
    const value = Number(averageCost);
    if (!Number.isFinite(value) || value <= 0) {
      setError('请输入大于 0 的平均成本');
      return;
    }
    setSavingCost(true);
    try {
      setPortfolio(await api.setPositionAverageCost(position.provider, position.symbol, value));
      setEditing('');
      setError('');
    } catch (saveError) {
      setError(saveError instanceof Error ? saveError.message : '成本保存失败');
    } finally {
      setSavingCost(false);
    }
  }

  async function clearCost(position: SpotPosition) {
    setSavingCost(true);
    try {
      setPortfolio(await api.clearPositionAverageCost(position.provider, position.symbol));
      setEditing('');
      setError('');
    } catch (clearError) {
      setError(clearError instanceof Error ? clearError.message : '成本清除失败');
    } finally {
      setSavingCost(false);
    }
  }

  return (
    <div className="mobile-screen-content mobile-positions">
      <section className="mobile-position-summary">
        <div className="mobile-section-head">
          <div><small>当前持仓市值</small><h2>{money(portfolio?.marketValue)} <span>{portfolio?.valuationCurrency || 'USD'}</span></h2></div>
          <button aria-label="从币安刷新持仓" disabled={loading} onClick={() => void load(true)} type="button"><RefreshCw className={loading ? 'spinning' : ''} size={19} /></button>
        </div>
        <div className="mobile-position-totals">
          <span><small>已知成本</small><strong>{money(portfolio?.knownCost)}</strong></span>
          <span><small>持仓盈亏</small><strong className={(portfolio?.knownPnl || 0) >= 0 ? 'mobile-up' : 'mobile-down'}>{signedMoney(portfolio?.knownPnl)}</strong></span>
          <span><small>收益率</small><strong className={(portfolio?.knownPnlPercent || 0) >= 0 ? 'mobile-up' : 'mobile-down'}>{percent(portfolio?.knownPnlPercent)}</strong></span>
        </div>
      </section>

      <section className={`mobile-position-channel ${portfolio?.accountReady ? 'ready' : 'waiting'}`}>
        <ShieldCheck size={19} />
        <div><strong>{portfolio?.message || '正在读取交易账户'}</strong><small>加密货币：币安现货 · 股票：币安股票{portfolio?.updatedAt ? ` · 更新 ${time(portfolio.updatedAt)}` : ''}</small></div>
      </section>

      {error ? <p className="mobile-position-error">{error}</p> : null}

      <section className="mobile-card mobile-position-list">
        <div className="mobile-section-head"><div><h3>持仓明细</h3><small>页面读取缓存，后台每 30 秒更新</small></div><span>{portfolio?.positions.length || 0} 项</span></div>
        {!loading && portfolio?.positions.length === 0 ? (
          <div className="mobile-position-empty"><WalletCards size={34} /><strong>暂无可展示持仓</strong><small>{status?.paper ? '模拟计划不会显示为真实持仓' : '成交后会在这里显示数量、成本和盈亏'}</small></div>
        ) : null}
        {portfolio?.positions.map((position) => {
          const isEditing = editing === positionKey(position);
          return <article className="mobile-position-row" key={positionKey(position)}>
            <div className="mobile-position-asset"><span>{position.asset.slice(0, 2)}</span><div><strong>{position.asset}</strong><small>{positionLabel(position.assetClass, position.provider)}</small></div></div>
            <div className="mobile-position-value"><strong>{money(position.marketValue)} {portfolio.valuationCurrency || 'USD'}</strong><small>{quantity(position.quantity)} {position.asset}</small></div>
            <dl>
              <div className="mobile-position-cost"><dt>平均成本</dt><dd>{position.costKnown ? money(position.averageCost) : '成本未知'}</dd>{position.assetClass !== 'CASH' ? <button onClick={() => startEditing(position)} type="button"><PencilLine size={12} />{position.costKnown ? '修改' : '设置成本'}</button> : null}</div>
              <div><dt>当前价格</dt><dd>{money(position.currentPrice)}</dd></div>
              <div><dt>持仓盈亏</dt><dd className={(position.pnl || 0) >= 0 ? 'mobile-up' : 'mobile-down'}>{position.costKnown ? `${signedMoney(position.pnl)} (${percent(position.pnlPercent)})` : '--'}</dd></div>
            </dl>
            {isEditing ? <div className="mobile-position-cost-editor">
              <label><span>每股 / 每币平均成本</span><input autoFocus inputMode="decimal" min="0" onChange={(event) => setAverageCost(event.target.value)} step="0.00000001" type="number" value={averageCost} /></label>
              <div>
                {position.costSource === 'MANUAL' ? <button className="danger" disabled={savingCost} onClick={() => void clearCost(position)} type="button"><Trash2 size={14} />恢复自动</button> : null}
                <button disabled={savingCost} onClick={() => setEditing('')} type="button"><X size={14} />取消</button>
                <button className="primary" disabled={savingCost} onClick={() => void saveCost(position)} type="button"><Check size={14} />保存</button>
              </div>
            </div> : null}
          </article>;
        })}
      </section>

      {!status?.stockBrokerConfigured ? <p className="mobile-position-note">股票持仓来自币安股票与资金账户；股票信号当前不会走合约通道。</p> : null}
    </div>
  );
}

function positionKey(position: SpotPosition) {
  return `${position.provider}:${position.symbol}`;
}

function money(value?: number) {
  return Number.isFinite(value) ? value!.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '--';
}

function signedMoney(value?: number) {
  return Number.isFinite(value) ? `${value! >= 0 ? '+' : ''}${money(value)}` : '--';
}

function percent(value?: number) {
  return Number.isFinite(value) ? `${value! >= 0 ? '+' : ''}${value!.toFixed(2)}%` : '--';
}

function quantity(value: number) {
  return value.toLocaleString('zh-CN', { maximumFractionDigits: 8 });
}

function positionLabel(assetClass: SpotPosition['assetClass'], provider: SpotPosition['provider']) {
  if (assetClass === 'STOCK') return '币安股票';
  if (assetClass === 'CASH') return provider === 'BINANCE_FUNDING' ? '资金账户现金' : '现货账户现金';
  return provider === 'BINANCE_FUNDING' ? '资金账户加密资产' : '币安现货';
}

function time(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '--' : date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}
