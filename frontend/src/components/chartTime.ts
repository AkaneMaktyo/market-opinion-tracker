import type { Time, UTCTimestamp } from 'lightweight-charts';
import type { MarketBar, Timeframe } from '../types';

export const barTime = (bar: MarketBar, timeframe: Timeframe): Time =>
  timeframe === '1D' ? bar.barTime.slice(0, 10) : timestamp(bar.barTime);

export const markerTime = (value: string, timeframe: Timeframe): Time =>
  timeframe === '1D' ? value.slice(0, 10) : timestamp(value);

export const formatTime = (time: Time, timeframe: Timeframe) => {
  const date = dateFromTime(time);
  if (!date) return '';
  const day = [
    date.getUTCFullYear(),
    pad(date.getUTCMonth() + 1),
    pad(date.getUTCDate()),
  ].join('-');
  if (timeframe === '1D') return day;
  return `${day} ${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`;
};

export const compareTime = (left: Time, right: Time) =>
  timeValue(left) - timeValue(right);

const timestamp = (value: string): UTCTimestamp =>
  Math.floor(Date.parse(value) / 1000) as UTCTimestamp;

function dateFromTime(time: Time) {
  if (typeof time === 'number') return new Date(time * 1000);
  if (typeof time === 'string') return new Date(`${time.slice(0, 10)}T00:00:00Z`);
  return new Date(Date.UTC(time.year, time.month - 1, time.day));
}

function timeValue(time: Time) {
  const date = dateFromTime(time);
  return date ? date.getTime() : 0;
}

const pad = (value: number) => value.toString().padStart(2, '0');
