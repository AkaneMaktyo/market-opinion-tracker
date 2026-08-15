import { Layers3, ShieldCheck, X } from 'lucide-react';
import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { api } from '../../api/client';
import type { PriceAlert } from '../../types/alerts';
import type { SignalTradePlan, SignalTradingStatus } from '../../types/trading';
import { isCryptoAlert } from './tradeRouting';

interface Props {
  alert: PriceAlert;
  status?: SignalTradingStatus;
  existingPlan?: SignalTradePlan;
  onClose: () => void;
  onSaved: (plan: SignalTradePlan) => void;
}

export function SignalTradeSheet({ alert, status, existingPlan, onClose, onSaved }: Props) {
  const [cost, setCost] = useState('300');
  const [batches, setBatches] = useState(alert.alertType === 'RANGE' ? 3 : 1);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const crypto = isCryptoAlert(alert);
  const quoteAsset = crypto ? 'USDT' : 'USDC';
  const routeReady = crypto ? status?.liveReady : status?.stockBrokerConfigured;
  const unsupportedBreakout = alert.alertType === 'POINT' && alert.triggerDirection === 'UP';
  const costNumber = Number(cost);
  const levels = useMemo(() => previewLevels(alert, batches), [alert, batches]);

  useEffect(() => {
    setBatches(alert.alertType === 'RANGE' ? 3 : 1);
    setCost('300');
    setError('');
  }, [alert.id, alert.alertType]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!Number.isFinite(costNumber) || costNumber <= 0) {
      setError('请输入大于 0 的投入成本');
      return;
    }
    if (!status?.paper && !routeReady) {
      setError(crypto ? '币安现货账户尚未就绪' : '币安股票账户尚未就绪');
      return;
    }
    if (!status?.paper && !window.confirm(`确认创建 ${batches} 笔币安${crypto ? '现货' : '股票'}限价买单？`)) return;
    setSaving(true);
    setError('');
    try {
      const plan = await api.saveSignalTradePlan(alert.id, { totalCost: costNumber, batchCount: batches });
      onSaved(plan);
    } catch (submitError) {
      setError(errorMessage(submitError));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="modal-backdrop mobile-trade-backdrop" data-mobile-overlay onMouseDown={onClose}>
      <form className="mobile-trade-sheet" onMouseDown={(event) => event.stopPropagation()} onSubmit={(event) => void submit(event)}>
        <div className="mobile-sheet-handle" />
        <div className="mobile-sheet-title">
          <div><h2>设置交易</h2><small>{alert.symbol} · {crypto ? '币安现货买入' : '股票买入'}</small></div>
          <button aria-label="关闭" onClick={onClose} type="button"><X size={20} /></button>
        </div>

        {!crypto ? (
          <div className={`mobile-trade-channel ${status?.paper ? 'paper' : routeReady ? 'live' : 'blocked'}`}>
            <ShieldCheck size={20} />
            <div><strong>{status?.paper ? '当前为股票模拟计划' : routeReady ? '币安股票实盘' : '币安股票未就绪'}</strong><small>{status?.stockMessage || '只调用币安股票订单，不使用币安合约。'}</small></div>
          </div>
        ) : (
          <div className={`mobile-trade-channel ${status?.paper ? 'paper' : status?.liveReady ? 'live' : 'blocked'}`}>
            <ShieldCheck size={20} />
            <div>
              <strong>{status?.paper ? '当前为模拟交易' : status?.liveReady ? '币安现货实盘' : '币安现货未就绪'}</strong>
              <small>仅买入现货，不使用杠杆，也不调用合约账户。</small>
            </div>
          </div>
        )}

        {existingPlan ? <ExistingPlan plan={existingPlan} /> : null}

        {!existingPlan ? (
          <>
            <section className="mobile-trade-signal">
              <span><small>信号类型</small><strong>{alert.alertType === 'RANGE' ? '区间分批' : '点位买入'}</strong></span>
              <span><small>信号价格</small><strong>{condition(alert)}</strong></span>
            </section>

            <label className="mobile-trade-field">
              <span>总投入成本</span>
              <div><input inputMode="decimal" min="0" onChange={(event) => setCost(event.target.value)} step="0.01" type="number" value={cost} /><b>{quoteAsset}</b></div>
            </label>

            {alert.alertType === 'RANGE' ? (
              <label className="mobile-trade-field">
                <span>分批入场</span>
                <select onChange={(event) => setBatches(Number(event.target.value))} value={batches}>
                  {[1, 2, 3, 4, 5].map((value) => <option key={value} value={value}>{value} 批</option>)}
                </select>
              </label>
            ) : null}

            <section className="mobile-trade-batches">
              <div className="mobile-trade-subhead"><span><Layers3 size={17} />下单预览</span><small>从区间上沿到下沿</small></div>
              {levels.map((price, index) => (
                <div key={`${price}-${index}`}><span>第 {index + 1} 批</span><strong>{formatPrice(price)}</strong><small>约 {formatMoney(costNumber / batches)} {quoteAsset}</small></div>
              ))}
            </section>

            {unsupportedBreakout ? <p className="mobile-trade-error">向上突破需要触发单，当前版本不会直接下单。</p> : null}
            {error ? <p className="mobile-trade-error">{error}</p> : null}
            <button
              className="mobile-trade-submit"
              disabled={saving || unsupportedBreakout || (!status?.paper && !routeReady)}
              type="submit"
            >{saving ? '正在创建…' : status?.paper ? '保存模拟交易计划' : `确认创建 ${batches} 笔${crypto ? '现货' : '股票'}买单`}</button>
          </>
        ) : null}
      </form>
    </div>
  );
}

function ExistingPlan({ plan }: { plan: SignalTradePlan }) {
  return (
    <section className="mobile-trade-existing">
      <div><span>已设置交易</span><b className={`trade-status-${plan.status.toLowerCase()}`}>{planStatus(plan.status)}</b></div>
      <strong>{formatMoney(plan.totalCost)} {plan.quoteAsset} · {plan.batchCount} 批</strong>
      {plan.orders.map((order) => (
        <p key={order.id}><span>第 {order.batchNo} 批 · {formatPrice(order.price)}</span><small>{orderStatus(order.status)}</small></p>
      ))}
    </section>
  );
}

function previewLevels(alert: PriceAlert, batches: number) {
  if (alert.alertType === 'POINT') return [alert.targetPrice ?? alert.lowerPrice];
  const upper = Math.max(alert.upperPrice, alert.lowerPrice);
  const lower = Math.min(alert.upperPrice, alert.lowerPrice);
  if (batches === 1) return [upper];
  return Array.from({ length: batches }, (_, index) => (
    index === batches - 1 ? lower : upper - ((upper - lower) * index) / (batches - 1)
  ));
}

function condition(alert: PriceAlert) {
  return alert.alertType === 'POINT'
    ? formatPrice(alert.targetPrice ?? alert.lowerPrice)
    : `${formatPrice(alert.lowerPrice)} ～ ${formatPrice(alert.upperPrice)}`;
}

function planStatus(status: string) {
  return ({ PLANNED: '待执行', ACTIVE: '挂单中', PARTIALLY_FILLED: '部分成交', FILLED: '已成交', ERROR: '需检查' } as Record<string, string>)[status] || status;
}

function orderStatus(status: string) {
  return ({ PLANNED: '待执行', SUBMITTING: '提交中', NEW: '已挂单', PARTIALLY_FILLED: '部分成交', FILLED: '已成交', ERROR: '失败', UNKNOWN: '待确认' } as Record<string, string>)[status] || status;
}

function formatPrice(value: number) {
  return Number.isFinite(value) ? value.toLocaleString('zh-CN', { maximumFractionDigits: 8 }) : '--';
}

function formatMoney(value: number) {
  return Number.isFinite(value) ? value.toLocaleString('zh-CN', { maximumFractionDigits: 2 }) : '--';
}

function errorMessage(error: unknown) {
  const message = error instanceof Error ? error.message : String(error);
  return message.replace(/^HTTP \d+ \[[^\]]+\]:\s*/, '') || '保存失败，请稍后重试';
}
