import { ExternalLink, Pause, Pencil, Play, Plus, RefreshCw, Save, Trash2, X } from 'lucide-react';
import { createPortal } from 'react-dom';
import type { PriceAlert, PriceAlertMonitorStatus, PriceAlertTriggerDirection } from '../../types/alerts';

interface Props {
  alerts: PriceAlert[];
  alertType: 'RANGE' | 'POINT';
  busy: boolean;
  editing: boolean;
  lower: string;
  message: string;
  status: PriceAlertMonitorStatus | null;
  symbol: string;
  target: string;
  triggerDirection: PriceAlertTriggerDirection;
  upper: string;
  onClose: () => void;
  onCancelEdit: () => void;
  onDelete: (id: string) => void;
  onEdit: (alert: PriceAlert) => void;
  onJump: (symbol: string) => void;
  onRefresh: () => void;
  onSave: () => void;
  onSetEnabled: (id: string, enabled: boolean) => void;
  setAlertType: (value: 'RANGE' | 'POINT') => void;
  setLower: (value: string) => void;
  setSymbol: (value: string) => void;
  setTarget: (value: string) => void;
  setTriggerDirection: (value: PriceAlertTriggerDirection) => void;
  setUpper: (value: string) => void;
}

export function PriceAlertModal(props: Props) {
  return createPortal(
    <div className="modal-backdrop price-alert-backdrop" onMouseDown={props.onClose}>
      <section
        aria-busy={props.busy}
        aria-labelledby="price-alert-title"
        aria-modal="true"
        className="entry price-alert-modal"
        onMouseDown={(event) => event.stopPropagation()}
        role="dialog"
      >
        <div className="price-alert-sheet-handle" />
        <header className="modal-head">
          <div>
            <div className="panel-title" id="price-alert-title">价格信号提醒</div>
            <p>后台通过 Bitget 行情监控，进入区间或穿越点位后发送一次 WxPusher。</p>
          </div>
          <div className="inline-actions">
            <button className="icon-button" onClick={props.onRefresh} title="刷新" type="button">
              <RefreshCw size={16} />
            </button>
            <button className="icon-button" onClick={props.onClose} title="关闭" type="button">
              <X size={18} />
            </button>
          </div>
        </header>

        <div className="price-alert-scroll">
          <MonitorStatus status={props.status} />

          <div className="price-alert-form">
            <label>标的
              <input value={props.symbol} onChange={(event) => props.setSymbol(event.target.value.toUpperCase())} />
            </label>
            <label>提醒方式
              <select value={props.alertType} onChange={(event) => props.setAlertType(event.target.value as 'RANGE' | 'POINT')}>
                <option value="RANGE">价格区间</option>
                <option value="POINT">单个点位</option>
              </select>
            </label>
            {props.alertType === 'POINT' ? (
              <>
                <label>提醒点位
                  <input min="0" step="any" type="number" value={props.target} onChange={(event) => props.setTarget(event.target.value)} />
                </label>
                <label>触发方向
                  <select value={props.triggerDirection} onChange={(event) => props.setTriggerDirection(event.target.value as PriceAlertTriggerDirection)}>
                    <option value="ANY">任意方向穿越</option>
                    <option value="UP">向上突破</option>
                    <option value="DOWN">向下跌破</option>
                  </select>
                </label>
              </>
            ) : (
              <>
                <label>区间下限
                  <input min="0" step="any" type="number" value={props.lower} onChange={(event) => props.setLower(event.target.value)} />
                </label>
                <label>区间上限
                  <input min="0" step="any" type="number" value={props.upper} onChange={(event) => props.setUpper(event.target.value)} />
                </label>
              </>
            )}
            <button className="primary" disabled={props.busy} onClick={props.onSave} type="button">
              {props.editing ? <Save size={16} /> : <Plus size={16} />}
              {props.editing ? '保存修改' : '新增提醒'}
            </button>
            {props.editing ? <button className="text-button" onClick={props.onCancelEdit} type="button">取消编辑</button> : null}
          </div>

          {props.message ? <div className="form-message">{props.message}</div> : null}
          <div className="price-alert-list">
            {props.alerts.length === 0 ? <p className="muted">还没有价格提醒。</p> : null}
            {props.alerts.map((alert) => (
              <article className="price-alert-row" key={alert.id}>
                <div className="price-alert-main">
                  <div>
                    <strong>{alert.symbol}</strong>
                    <span className={`status-pill ${alert.status === 'ACTIVE' ? 'active' : ''}`}>
                      {statusLabel(alert.status)}
                    </span>
                  </div>
                  <p>{conditionLabel(alert)}</p>
                  {alert.lastPrice != null ? (
                    <small>最近价 {formatPrice(alert.lastPrice)} · {formatTime(alert.lastCheckedAt)}</small>
                  ) : <small>等待首次行情</small>}
                  {alert.errorMessage ? <small className="alert-error">{alert.errorMessage}</small> : null}
                </div>
                <div className="inline-actions">
                  <button className="icon-button" disabled={props.busy} onClick={() => props.onJump(alert.symbol)} title="跳转到该 K 线" type="button">
                    <ExternalLink size={15} />
                  </button>
                  <button className="icon-button" disabled={props.busy} onClick={() => props.onEdit(alert)} title="编辑" type="button">
                    <Pencil size={15} />
                  </button>
                  <button
                    className="icon-button"
                    disabled={props.busy}
                    onClick={() => props.onSetEnabled(alert.id, alert.status !== 'ACTIVE')}
                    title={alert.status === 'ACTIVE' ? '暂停' : '重新启用'}
                    type="button"
                  >
                    {alert.status === 'ACTIVE' ? <Pause size={15} /> : <Play size={15} />}
                  </button>
                  <button className="icon-button danger" disabled={props.busy} onClick={() => props.onDelete(alert.id)} title="删除" type="button">
                    <Trash2 size={15} />
                  </button>
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>
    </div>,
    document.body,
  );
}

function MonitorStatus({ status }: { status: PriceAlertMonitorStatus | null }) {
  if (!status) return null;
  return (
    <div className="price-monitor-status">
      <span className={`monitor-dot ${['LIVE', 'POLLING'].includes(status.state) ? 'live' : ''}`} />
      <strong>{monitorLabel(status.state)}</strong>
      <span>{status.activeAlerts} 个提醒 / {status.subscribedSymbols} 个标的</span>
      <span>{status.pushReady ? 'WxPusher 已就绪' : 'WxPusher 未配置'}</span>
      {status.lastError ? <span className="alert-error">{status.lastError}</span> : null}
    </div>
  );
}

function statusLabel(status: PriceAlert['status']) {
  return {
    ACTIVE: '监控中',
    DELIVERING: '发送中',
    TRIGGERED: '已触发',
    PAUSED: '已暂停',
    ERROR: '发送失败',
  }[status];
}

function monitorLabel(state: PriceAlertMonitorStatus['state']) {
  return {
    IDLE: '等待提醒',
    CONNECTING: '正在连接',
    LIVE: 'Bitget 实时',
    POLLING: 'Bitget 轮询',
    RECONNECTING: '轮询监控',
    ERROR: '监控异常',
  }[state];
}

function formatPrice(value: number) {
  return new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 8 }).format(value);
}

function conditionLabel(alert: PriceAlert) {
  return alert.alertType === 'POINT'
    ? `${directionLabel(alert.triggerDirection)} ${formatPrice(alert.targetPrice ?? alert.lowerPrice)}`
    : `${formatPrice(alert.lowerPrice)} ～ ${formatPrice(alert.upperPrice)}`;
}

function directionLabel(direction: PriceAlertTriggerDirection) {
  return ({ ANY: '穿越点位', UP: '向上突破', DOWN: '向下跌破' })[direction || 'ANY'];
}

function formatTime(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN') : '尚未检查';
}
