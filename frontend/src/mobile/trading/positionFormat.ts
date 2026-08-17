/** 持仓页共用的时间与数字格式化工具。 */

export function money(value?: number) {
  return Number.isFinite(value) ? value!.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '--';
}

export function signedMoney(value?: number) {
  return Number.isFinite(value) ? `${value! >= 0 ? '+' : ''}${money(value)}` : '--';
}

export function percent(value?: number) {
  return Number.isFinite(value) ? `${value! >= 0 ? '+' : ''}${value!.toFixed(2)}%` : '--';
}

export function quantity(value: number) {
  return value.toLocaleString('zh-CN', { maximumFractionDigits: 8 });
}

export function price(value?: number) {
  if (!Number.isFinite(value)) return '--';
  const absolute = Math.abs(value!);
  const digits = absolute >= 100 ? 2 : absolute >= 1 ? 4 : 8;
  return value!.toLocaleString('zh-CN', { maximumFractionDigits: digits });
}

export function clockTime(value?: string) {
  if (!value) return '';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}

export function pnlClass(value?: number) {
  return (value || 0) >= 0 ? 'mobile-up' : 'mobile-down';
}
