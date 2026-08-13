import type { PriceAlert } from '../../types/alerts';

export const ALERT_NEAR_PERCENT = 3;
export const ALERT_WATCH_PERCENT = 5;

export type AlertProximityState = 'far' | 'watch' | 'near' | 'inside' | 'unavailable';

export interface AlertProximity {
  state: AlertProximityState;
  label: string;
  distancePercent?: number;
  intensity: number;
}

export function priceAlertProximity(alert: PriceAlert, price?: number): AlertProximity {
  if (!Number.isFinite(price)) return unavailable();
  if (alert.alertType === 'RANGE') return rangeProximity(alert, price!);
  return pointProximity(alert, price!);
}

function pointProximity(alert: PriceAlert, price: number) {
  const target = alert.targetPrice ?? alert.lowerPrice;
  if (!validBoundary(target)) return unavailable();
  return distanceProximity(Math.abs(price - target) / Math.abs(target) * 100, '即将到达');
}

function rangeProximity(alert: PriceAlert, price: number) {
  if (!validBoundary(alert.lowerPrice) || !validBoundary(alert.upperPrice)) return unavailable();
  const lower = Math.min(alert.lowerPrice, alert.upperPrice);
  const upper = Math.max(alert.lowerPrice, alert.upperPrice);
  if (price >= lower && price <= upper) {
    return { state: 'inside' as const, label: '区间内', distancePercent: 0, intensity: 1 };
  }
  const boundary = price < lower ? lower : upper;
  return distanceProximity(Math.abs(price - boundary) / Math.abs(boundary) * 100, '即将进入');
}

function distanceProximity(distancePercent: number, nearLabel: string): AlertProximity {
  const intensity = clamp((ALERT_WATCH_PERCENT - distancePercent) / ALERT_WATCH_PERCENT);
  if (distancePercent <= ALERT_NEAR_PERCENT) {
    return { state: 'near', label: nearLabel, distancePercent, intensity };
  }
  if (distancePercent <= ALERT_WATCH_PERCENT) {
    return { state: 'watch', label: '逐渐接近', distancePercent, intensity };
  }
  return { state: 'far', label: '距离较远', distancePercent, intensity: 0 };
}

function unavailable(): AlertProximity {
  return { state: 'unavailable', label: '等待行情', intensity: 0 };
}

function validBoundary(value?: number) {
  return Number.isFinite(value) && value !== 0;
}

function clamp(value: number) {
  return Math.min(1, Math.max(0, value));
}
