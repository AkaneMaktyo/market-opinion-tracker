import { BellRing } from 'lucide-react';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { PriceAlert, PriceAlertMonitorStatus } from '../../types/alerts';
import { PriceAlertModal } from './PriceAlertModal';

interface Props {
  selectedSymbol: string;
  onJumpToChart: (symbol: string) => void;
}

export function PriceAlertButton({ selectedSymbol, onJumpToChart }: Props) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [alerts, setAlerts] = useState<PriceAlert[]>([]);
  const [status, setStatus] = useState<PriceAlertMonitorStatus | null>(null);
  const [symbol, setSymbol] = useState(selectedSymbol);
  const [alertType, setAlertType] = useState<'RANGE' | 'POINT'>('RANGE');
  const [lower, setLower] = useState('');
  const [upper, setUpper] = useState('');
  const [target, setTarget] = useState('');
  const [editingId, setEditingId] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!open) return;
    setSymbol(selectedSymbol);
    void load();
  }, [open, selectedSymbol]);

  useEffect(() => {
    if (!open) return;
    const timer = window.setInterval(() => void refreshSilently(), 5000);
    return () => window.clearInterval(timer);
  }, [open]);

  async function load() {
    setBusy(true);
    try {
      const [nextAlerts, nextStatus] = await Promise.all([
        api.priceAlerts(),
        api.priceAlertStatus(),
      ]);
      setAlerts(nextAlerts);
      setStatus(nextStatus);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '读取价格提醒失败');
    } finally {
      setBusy(false);
    }
  }

  async function refreshSilently() {
    try {
      const [nextAlerts, nextStatus] = await Promise.all([
        api.priceAlerts(),
        api.priceAlertStatus(),
      ]);
      setAlerts(nextAlerts);
      setStatus(nextStatus);
    } catch {
      // 手动刷新时再展示连接错误，后台刷新不打断当前操作。
    }
  }

  async function save() {
    setMessage('');
    const lowerPrice = Number(lower);
    const upperPrice = Number(upper);
    const targetPrice = Number(target);
    const validPrices = alertType === 'POINT'
      ? Number.isFinite(targetPrice) && targetPrice > 0
      : Number.isFinite(lowerPrice) && Number.isFinite(upperPrice);
    if (!symbol.trim() || !validPrices) {
      setMessage(alertType === 'POINT' ? '请填写标的和提醒点位' : '请填写标的和完整价格区间');
      return;
    }
    setBusy(true);
    try {
      const body = {
        symbol: symbol.trim(),
        alertType,
        lowerPrice: alertType === 'RANGE' ? lowerPrice : undefined,
        upperPrice: alertType === 'RANGE' ? upperPrice : undefined,
        targetPrice: alertType === 'POINT' ? targetPrice : undefined,
      };
      if (editingId) {
        await api.updatePriceAlert(editingId, body);
      } else {
        await api.createPriceAlert(body);
      }
      resetDraft();
      await load();
      setMessage(editingId ? '价格提醒已更新' : '价格提醒已启动；无需保持 K 线页面打开');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '创建价格提醒失败');
      setBusy(false);
    }
  }

  function edit(alert: PriceAlert) {
    setEditingId(alert.id);
    setSymbol(alert.symbol);
    setAlertType(alert.alertType);
    setLower(String(alert.lowerPrice));
    setUpper(String(alert.upperPrice));
    setTarget(alert.targetPrice == null ? '' : String(alert.targetPrice));
    setMessage('');
  }

  function resetDraft() {
    setEditingId('');
    setAlertType('RANGE');
    setLower('');
    setUpper('');
    setTarget('');
  }

  async function setEnabled(id: string, enabled: boolean) {
    setBusy(true);
    setMessage('');
    try {
      await api.setPriceAlertEnabled(id, enabled);
      await load();
      setMessage(enabled ? '提醒已重新启用' : '提醒已暂停');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '更新提醒失败');
      setBusy(false);
    }
  }

  async function remove(id: string) {
    setBusy(true);
    setMessage('');
    try {
      await api.deletePriceAlert(id);
      await load();
      setMessage('提醒已删除');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '删除提醒失败');
      setBusy(false);
    }
  }

  return (
    <>
      <button className="primary secondary" onClick={() => setOpen(true)} type="button">
        <BellRing size={16} />价格提醒
      </button>
      {open ? (
        <PriceAlertModal
          alerts={alerts}
          alertType={alertType}
          busy={busy}
          editing={Boolean(editingId)}
          lower={lower}
          message={message}
          onClose={() => setOpen(false)}
          onCancelEdit={resetDraft}
          onDelete={(id) => void remove(id)}
          onEdit={edit}
          onJump={(nextSymbol) => {
            setOpen(false);
            onJumpToChart(nextSymbol);
          }}
          onRefresh={() => void load()}
          onSave={() => void save()}
          onSetEnabled={(id, enabled) => void setEnabled(id, enabled)}
          setAlertType={setAlertType}
          setLower={setLower}
          setSymbol={setSymbol}
          setTarget={setTarget}
          setUpper={setUpper}
          status={status}
          symbol={symbol}
          target={target}
          upper={upper}
        />
      ) : null}
    </>
  );
}
