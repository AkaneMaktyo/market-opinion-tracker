import type { SeriesMarker, Time } from 'lightweight-charts';
import type { OpinionView, Timeframe } from '../../types';
import { compareTime, markerTime } from '../chartTime';
import type { PriceLineView } from './ChartCanvas';

const directionColors = {
  BULLISH: '#22c55e',
  BEARISH: '#f43f5e',
  RANGE: '#f59e0b',
  WATCH: '#94a3b8',
};

const levelColors: Record<string, string> = {
  SUPPORT: '#22c55e',
  RESISTANCE: '#f97316',
  TARGET: '#38bdf8',
  STOP: '#f43f5e',
};

const levelLabels: Record<string, string> = {
  SUPPORT: '支撑',
  RESISTANCE: '阻力',
  TARGET: '目标',
  STOP: '止损',
};

export function buildMarkers(opinions: OpinionView[], timeframe: Timeframe) {
  return opinions.map(({ opinion }): SeriesMarker<Time> => {
    const bullish = opinion.direction === 'BULLISH';
    const bearish = opinion.direction === 'BEARISH';
    const confidence = opinion.confidence == null ? '' : ` ${opinion.confidence}%`;
    return {
      time: markerTime(opinion.opinionTime, timeframe),
      position: bullish ? 'belowBar' : 'aboveBar',
      color: directionColors[opinion.direction] || directionColors.WATCH,
      shape: bullish ? 'arrowUp' : bearish ? 'arrowDown' : 'circle',
      text: opinion.status === 'MESSAGE' ? '消息' : `${directionLabel(opinion.direction)}${confidence}`,
    };
  }).sort((left, right) => compareTime(left.time, right.time));
}

export function buildPriceLines(opinions: OpinionView[]) {
  const unique = new Map<string, PriceLineView>();
  opinions.forEach((item) => item.priceLevels.forEach((level) => {
    const price = Number(level.price);
    if (!Number.isFinite(price)) return;
    const type = level.levelType.toUpperCase();
    unique.set(`${type}:${price}`, {
      price,
      color: levelColors[type] || '#94a3b8',
      title: levelLabels[type] || type,
    });
  }));
  return [...unique.values()];
}

function directionLabel(direction: string) {
  return {
    BULLISH: '看多',
    BEARISH: '看空',
    RANGE: '震荡',
    WATCH: '观望',
  }[direction] || '观点';
}
