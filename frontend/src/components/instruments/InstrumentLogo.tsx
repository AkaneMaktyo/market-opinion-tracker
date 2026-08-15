import { useEffect, useMemo, useState } from 'react';

interface Props {
  symbol: string;
  logoUrl?: string;
  size?: number;
  sourceKind?: 'stock' | 'crypto';
}

export function InstrumentLogo({ symbol, logoUrl, size = 20, sourceKind = 'stock' }: Props) {
  const [sourceIndex, setSourceIndex] = useState(0);
  const [loadedSource, setLoadedSource] = useState('');
  const sources = useMemo(() => iconSources(symbol, logoUrl, sourceKind), [logoUrl, sourceKind, symbol]);
  const hue = symbol.split('').reduce((sum, char) => sum + char.charCodeAt(0), 0) % 360;

  useEffect(() => {
    setSourceIndex(0);
  }, [logoUrl, sourceKind, symbol]);

  const source = sources[sourceIndex];
  const loaded = Boolean(source && source === loadedSource);
  return (
    <span className="stock-icon-shell" style={{ height: size, width: size }}>
      {!loaded ? <span
        className="stock-icon-fallback"
        style={{ backgroundColor: `hsl(${hue}, 55%, 45%)`, height: size, width: size }}
      >
        {symbol.charAt(0)}
      </span> : null}
      {source ? (
      <img
        alt={symbol}
        className="stock-icon"
        decoding="async"
        height={size}
        loading="lazy"
        onError={() => {
          setSourceIndex((current) => current + 1);
        }}
        onLoad={(event) => {
          if (event.currentTarget.naturalWidth <= 1 || event.currentTarget.naturalHeight <= 1) {
            setSourceIndex((current) => current + 1);
            return;
          }
          setLoadedSource(source);
          saveSuccessfulSource(symbol, logoUrl, sourceKind, source);
        }}
        referrerPolicy="no-referrer"
        src={source}
        style={{ visibility: loaded ? 'visible' : 'hidden' }}
        width={size}
      />
      ) : null}
    </span>
  );
}

function iconSources(symbol: string, logoUrl: string | undefined, sourceKind: Props['sourceKind']) {
  const cleaned = logoUrl?.trim();
  const cached = cachedSuccessfulSource(symbol, cleaned, sourceKind);
  const lowerSymbol = symbol.toLowerCase();
  const automaticSources = sourceKind === 'crypto' ? [
    `https://assets.coincap.io/assets/icons/${lowerSymbol}@2x.png`,
    `https://cdn.jsdelivr.net/npm/cryptocurrency-icons@0.18.1/128/color/${lowerSymbol}.png`,
  ] : [
    `https://assets.parqet.com/logos/symbol/${symbol.toUpperCase()}?format=png`,
    `https://financialmodelingprep.com/image-stock/${symbol.toUpperCase()}.png`,
  ];
  return [
    cached,
    cleaned,
    ...automaticSources,
  ].filter((value, index, items): value is string => Boolean(value) && items.indexOf(value) === index);
}

const LOGO_CACHE_PREFIX = 'market-opinion:instrument-logo:v2:';

interface LogoCacheEntry {
  configuredUrl: string;
  sourceKind: Props['sourceKind'];
  source: string;
}

function cachedSuccessfulSource(symbol: string, configuredUrl: string | undefined, sourceKind: Props['sourceKind']) {
  try {
    const raw = window.localStorage.getItem(LOGO_CACHE_PREFIX + symbol.toUpperCase());
    if (!raw) return undefined;
    const entry = JSON.parse(raw) as LogoCacheEntry;
    return entry.configuredUrl === (configuredUrl || '') && entry.sourceKind === sourceKind ? entry.source : undefined;
  } catch {
    return undefined;
  }
}

function saveSuccessfulSource(symbol: string, configuredUrl: string | undefined, sourceKind: Props['sourceKind'], source: string) {
  try {
    const entry: LogoCacheEntry = { configuredUrl: configuredUrl?.trim() || '', sourceKind, source };
    window.localStorage.setItem(LOGO_CACHE_PREFIX + symbol.toUpperCase(), JSON.stringify(entry));
  } catch {
    // WebView 禁用存储时仍正常显示本次已加载图标。
  }
}
