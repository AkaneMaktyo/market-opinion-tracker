import { CandlestickChart, RefreshCw, Search, ShieldCheck, WalletCards } from 'lucide-react';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import { InstrumentLogo } from '../../components/instruments/InstrumentLogo';
import type { FuturesPortfolio, FuturesPosition } from '../../types/trading';
import { PositionOpinionSheet } from './PositionOpinionSheet';
import { clockTime, money, percent, price, pnlClass, signedMoney } from './positionFormat';

const REFRESH_MS = 15000;

/** Bitget USDT 合约持仓面板：汇总卡片、账户状态与逐仓明细。 */
export function MobileFuturesPositions() {
  const [portfolio, setPortfolio] = useState<FuturesPortfolio>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [opinionSymbol, setOpinionSymbol] = useState('');

  async function load() {
    setLoading(true);
    try {
      setPortfolio(await api.futuresPortfolio());
      setError('');
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '合约持仓读取失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => {
      if (!document.hidden) void load();
    }, REFRESH_MS);
    return () => window.clearInterval(timer);
  }, []);

  const positions = portfolio?.positions || [];
  const updatedAt = clockTime(portfolio?.updatedAt);

  return (
    <>
      <section className="mobile-position-summary">
        <div className="mobile-section-head">
          <div><small>合约未实现盈亏合计</small><h2 className={pnlClass(portfolio?.totalUnrealizedPL)}>{signedMoney(portfolio?.totalUnrealizedPL)} <span>{portfolio?.marginCoin || 'USDT'}</span></h2></div>
          <button aria-label="刷新合约持仓" disabled={loading} onClick={() => void load()} type="button"><RefreshCw className={loading ? 'spinning' : ''} size={19} /></button>
        </div>
        <div className="mobile-position-totals">
          <span><small>持仓数</small><strong>{portfolio?.positionCount ?? '--'} 个</strong></span>
          <span><small>保证金占用</small><strong>{money(portfolio?.totalMargin)}</strong></span>
          <span><small>合计收益率</small><strong className={pnlClass(portfolio?.totalReturnRate)}>{percent(portfolio?.totalReturnRate)}</strong></span>
        </div>
      </section>

      <section className={`mobile-position-channel ${portfolio?.accountReady ? 'ready' : 'waiting'}`}>
        <ShieldCheck size={19} />
        <div>
          <strong>{portfolio?.message || '正在读取 Bitget 合约账户'}</strong>
          <small>
            {portfolio?.demo ? '模拟盘环境' : '实盘环境'} · 账户权益 {money(portfolio?.accountEquity)} {portfolio?.marginCoin || 'USDT'}
            {updatedAt ? ` · 更新 ${updatedAt}` : ''}
          </small>
        </div>
      </section>

      {error ? <p className="mobile-position-error">{error}</p> : null}

      <section className="mobile-card mobile-position-list">
        <div className="mobile-section-head">
          <div><h3>合约持仓</h3><small>每 15 秒自动更新</small></div>
          <span>{portfolio?.productType === 'USDT-FUTURES' ? 'USDT 本位' : portfolio?.productType || '--'}</span>
        </div>
        {!loading && positions.length === 0 ? (
          <div className="mobile-position-empty">
            <WalletCards size={34} />
            <strong>暂无合约持仓</strong>
            <small>{portfolio?.accountReady ? '在 Bitget 开仓后会显示在这里' : (portfolio?.message || '合约账户未就绪')}</small>
          </div>
        ) : null}
        {positions.map((position) => <FuturesRow key={`${position.symbol}-${position.side}`} marginCoin={portfolio?.marginCoin || 'USDT'} onLookup={setOpinionSymbol} position={position} />)}
      </section>

      <p className="mobile-position-note">
        <CandlestickChart size={13} /> 合约数据来自 Bitget {portfolio?.demo ? '模拟盘' : '实盘'}，仅供持仓跟踪，不构成下单入口。
      </p>

      {opinionSymbol ? (
        <PositionOpinionSheet
          keyword={opinionSymbol}
          logoUrl={baseAsset(opinionSymbol) ? `https://assets.coincap.io/assets/icons/${baseAsset(opinionSymbol).toLowerCase()}@2x.png` : undefined}
          onClose={() => setOpinionSymbol('')}
          sourceKind="crypto"
        />
      ) : null}
    </>
  );
}

function FuturesRow({ marginCoin, onLookup, position }: { marginCoin: string; onLookup: (keyword: string) => void; position: FuturesPosition }) {
  const base = baseAsset(position.symbol);
  const isLong = position.side === 'long';
  return (
    <article className="mobile-futures-row">
      <div className="mobile-futures-top">
        <div className="mobile-position-asset">
          <InstrumentLogo logoUrl={base ? `https://assets.coincap.io/assets/icons/${base.toLowerCase()}@2x.png` : undefined} size={30} sourceKind="crypto" symbol={base || position.symbol} />
          <strong>{position.symbol}</strong>
        </div>
        <span className={`mobile-futures-side ${isLong ? 'long' : 'short'}`}>{isLong ? '做多' : '做空'}</span>
        <span className="mobile-futures-mode">{position.leverage ? `${trimNumber(position.leverage)}x ` : ''}{position.isolated ? '逐仓' : '全仓'}</span>
        <button
          aria-label={`查看 ${position.symbol} 最近观点`}
          className="mobile-position-opinion-btn"
          onClick={() => onLookup(base || position.symbol)}
          type="button"
        ><Search size={13} />观点</button>
        <span className="mobile-futures-size">{position.size != null ? `${trimNumber(position.size)} ${base}` : '--'}</span>
      </div>
      <div className="mobile-futures-pnl">
        <div><small>未实现盈亏 ({marginCoin})</small><b className={pnlClass(position.unrealizedPL)}>{signedMoney(position.unrealizedPL)}</b></div>
        <div><small>收益率</small><b className={pnlClass(position.returnRate)}>{percent(position.returnRate)}</b></div>
      </div>
      <dl className="mobile-futures-metrics">
        <div><dt>开仓均价 / 标记价格</dt><dd>{price(position.openPriceAvg)} / {price(position.markPrice)}</dd></div>
        <div><dt>保证金</dt><dd>{money(position.margin)} {marginCoin}</dd></div>
        {position.liquidationPrice != null && position.liquidationPrice > 0 ? (
          <div><dt>强平价</dt><dd className="mobile-futures-liq">{price(position.liquidationPrice)}</dd></div>
        ) : null}
      </dl>
    </article>
  );
}

function baseAsset(symbol: string) {
  const match = symbol.match(/^([A-Z0-9]+)USDT.*$/);
  return match ? match[1] : '';
}

function trimNumber(value: number) {
  return value.toLocaleString('zh-CN', { maximumFractionDigits: 8 });
}
