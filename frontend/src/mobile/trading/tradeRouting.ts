import type { PriceAlert } from '../../types/alerts';

const CRYPTO_MARKETS = new Set([
  'CRYPTO', 'CRYPTOCURRENCY', 'COIN', 'DIGITAL_ASSET', 'BINANCE', 'BITGET', 'OKX', 'BYBIT',
]);

export function isCryptoAlert(alert: PriceAlert) {
  const market = (alert.market || '').trim().toUpperCase();
  const symbol = alert.symbol.replace(/[^A-Za-z0-9]/g, '').toUpperCase();
  return CRYPTO_MARKETS.has(market) || symbol.endsWith('USDT') || symbol.endsWith('USDC');
}
