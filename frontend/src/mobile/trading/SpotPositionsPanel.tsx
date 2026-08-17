import { Check, PencilLine, RefreshCw, Search, ShieldCheck, Trash2, WalletCards, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import { InstrumentLogo } from '../../components/instruments/InstrumentLogo';
import type { Instrument } from '../../types';
import type { PositionPortfolio, SignalTradingStatus, SpotPosition } from '../../types/trading';
import { PositionOpinionSheet } from './PositionOpinionSheet';
import { clockTime, money, percent, price, pnlClass, quantity, signedMoney } from './positionFormat';

const REFRESH_MS = 15000;

/** 现货与股票持仓面板：数据拉取、平均成本维护与明细展示。 */
export function SpotPositionsPanel() {
  const [portfolio, setPortfolio] = useState<PositionPortfolio>();
  const [status, setStatus] = useState<SignalTradingStatus>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState('');
  const [averageCost, setAverageCost] = useState('');
  const [savingCost, setSavingCost] = useState(false);
  const [instrumentDirectory, setInstrumentDirectory] = useState<Record<string, Instrument>>({});
  const [opinionLookup, setOpinionLookup] = useState<{ keyword: string; logoUrl?: string; sourceKind: 'stock' | 'crypto' } | null>(null);

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

  useEffect(() => {
    let active = true;
    void api.instruments(undefined, 'history', false).then((items) => {
      if (!active) return;
      setInstrumentDirectory(Object.fromEntries(
        items.map((item) => [item.symbol.toUpperCase(), item]),
      ));
    }).catch(() => {
      // 标的资料读取失败不阻塞持仓本身展示。
    });
    return () => { active = false; };
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
    <>
      <section className="mobile-position-summary">
        <div className="mobile-section-head">
          <div><small>现货持仓市值（含股票）</small><h2>{money(portfolio?.marketValue)} <span>{portfolio?.valuationCurrency || 'USD'}</span></h2></div>
          <button aria-label="从币安刷新持仓" disabled={loading} onClick={() => void load(true)} type="button"><RefreshCw className={loading ? 'spinning' : ''} size={19} /></button>
        </div>
        <div className="mobile-position-totals">
          <span><small>已知成本</small><strong>{money(portfolio?.knownCost)}</strong></span>
          <span><small>持仓盈亏</small><strong className={pnlClass(portfolio?.knownPnl)}>{signedMoney(portfolio?.knownPnl)}</strong></span>
          <span><small>收益率</small><strong className={pnlClass(portfolio?.knownPnlPercent)}>{percent(portfolio?.knownPnlPercent)}</strong></span>
        </div>
      </section>

      <section className={`mobile-position-channel ${portfolio?.accountReady ? 'ready' : 'waiting'}`}>
        <ShieldCheck size={19} />
        <div><strong>{portfolio?.message || '正在读取交易账户'}</strong><small>加密货币：币安现货 · 股票：币安股票{portfolio?.updatedAt ? ` · 更新 ${clockTime(portfolio.updatedAt)}` : ''}</small></div>
      </section>

      {error ? <p className="mobile-position-error">{error}</p> : null}

      <section className="mobile-card mobile-position-list">
        <div className="mobile-section-head"><div><h3>持仓明细</h3><small>每 30 秒自动更新</small></div><span>{portfolio?.positions.length || 0} 项</span></div>
        {!loading && portfolio?.positions.length === 0 ? (
          <div className="mobile-position-empty"><WalletCards size={34} /><strong>暂无可展示持仓</strong><small>{status?.paper ? '模拟计划不会显示为真实持仓' : '成交后会在这里显示数量、成本和盈亏'}</small></div>
        ) : null}
        {portfolio?.positions.map((position) => {
          const isEditing = editing === positionKey(position);
          const isCash = position.assetClass === 'CASH';
          const instrument = instrumentDirectory[position.asset.toUpperCase()];
          return <article className={`mobile-position-row ${isCash ? 'cash' : ''}`} key={positionKey(position)}>
            <div className="mobile-position-main">
              <div className="mobile-position-asset">
                <InstrumentLogo logoUrl={instrument?.logoUrl || positionLogoUrl(position)} size={34} sourceKind={position.assetClass === 'STOCK' ? 'stock' : 'crypto'} symbol={position.asset} />
                <strong>{position.asset}</strong>
              </div>
              {!isCash ? (
                <button
                  aria-label={`查看 ${position.asset} 最近观点`}
                  className="mobile-position-opinion-btn"
                  onClick={() => setOpinionLookup({
                    keyword: instrument?.name?.trim() || position.asset,
                    logoUrl: instrument?.logoUrl || positionLogoUrl(position),
                    sourceKind: position.assetClass === 'STOCK' ? 'stock' : 'crypto',
                  })}
                  type="button"
                ><Search size={13} />观点</button>
              ) : null}
              <div className="mobile-position-quantity">{quantity(position.quantity)}</div>
            </div>
            <div className="mobile-position-meta">
              <span>{positionSubtitle(position, instrument)}</span>
              <strong>{money(position.marketValue)} {portfolio.valuationCurrency || 'USD'}</strong>
            </div>
            {!isCash ? <dl className="mobile-position-metrics">
              <div className="mobile-position-cost">
                <dt>平均成本</dt><dd className={position.costSource === 'MANUAL_REVIEW_REQUIRED' ? 'needs-review' : ''}>{costText(position)}
                  <button aria-label={`${position.costKnown ? '修改' : '设置'} ${position.asset} 平均成本`} onClick={() => startEditing(position)} title={position.costKnown ? '修改平均成本' : '设置平均成本'} type="button"><PencilLine size={12} /></button>
                </dd>
              </div>
              <div><dt>当前价格</dt><dd>{price(position.currentPrice)} {portfolio.valuationCurrency || 'USD'}</dd></div>
              <div className="mobile-position-pnl"><dt>持仓盈亏</dt><dd className={pnlClass(position.pnl)}>{position.costKnown ? `${signedMoney(position.pnl)} ${portfolio.valuationCurrency || 'USD'}` : '--'}{position.costKnown ? <small>({percent(position.pnlPercent)})</small> : null}</dd></div>
            </dl> : null}
            {isEditing ? <div className="mobile-position-cost-editor">
              <label><span>当前全部持仓的每股 / 每币平均成本</span><input autoFocus inputMode="decimal" min="0" onChange={(event) => setAverageCost(event.target.value)} step="0.00000001" type="number" value={averageCost} /><small>保存后，应用内新增成交会自动按真实成交金额加权更新</small></label>
              <div>
                {isManualCost(position.costSource) ? <button className="danger" disabled={savingCost} onClick={() => void clearCost(position)} type="button"><Trash2 size={14} />恢复自动</button> : null}
                <button disabled={savingCost} onClick={() => setEditing('')} type="button"><X size={14} />取消</button>
                <button className="primary" disabled={savingCost} onClick={() => void saveCost(position)} type="button"><Check size={14} />保存</button>
              </div>
            </div> : null}
          </article>;
        })}
      </section>

      {!status?.stockBrokerConfigured ? <p className="mobile-position-note">股票持仓来自币安股票与资金账户；股票信号当前不会走合约通道。</p> : null}

      {opinionLookup ? (
        <PositionOpinionSheet
          keyword={opinionLookup.keyword}
          logoUrl={opinionLookup.logoUrl}
          onClose={() => setOpinionLookup(null)}
          sourceKind={opinionLookup.sourceKind}
        />
      ) : null}
    </>
  );
}

function positionKey(position: SpotPosition) {
  return `${position.provider}:${position.symbol}`;
}

function costText(position: SpotPosition) {
  if (position.costKnown) return price(position.averageCost);
  return position.costSource === 'MANUAL_REVIEW_REQUIRED' ? '检测到额外增仓，请更新' : '未设置';
}

function isManualCost(source: SpotPosition['costSource']) {
  return source === 'MANUAL' || source === 'MANUAL_REVIEW_REQUIRED';
}

function positionLogoUrl(position: SpotPosition) {
  if (position.assetClass === 'STOCK') return undefined;
  return `https://assets.coincap.io/assets/icons/${position.asset.toLowerCase()}@2x.png`;
}

function positionSubtitle(position: SpotPosition, instrument?: Instrument) {
  if (position.assetClass !== 'STOCK') {
    return positionLabel(position.assetClass, position.provider);
  }
  const name = instrument?.name?.trim();
  return name && name.toUpperCase() !== position.asset.toUpperCase()
    ? name
    : positionLabel(position.assetClass, position.provider);
}

function positionLabel(assetClass: SpotPosition['assetClass'], provider: SpotPosition['provider']) {
  if (assetClass === 'STOCK') return '币安股票';
  if (assetClass === 'CASH') return provider === 'BINANCE_FUNDING' ? '资金账户 · 现金' : '现货账户 · 现金';
  return provider === 'BINANCE_FUNDING' ? '资金账户 · 加密' : '币安现货';
}
