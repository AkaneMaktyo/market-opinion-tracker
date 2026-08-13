import { useEffect, useMemo, useState } from 'react';

interface Props {
  symbol: string;
  logoUrl?: string;
  size?: number;
}

export function InstrumentLogo({ symbol, logoUrl, size = 20 }: Props) {
  const [sourceIndex, setSourceIndex] = useState(0);
  const [loaded, setLoaded] = useState(false);
  const sources = useMemo(() => iconSources(symbol, logoUrl), [logoUrl, symbol]);
  const hue = symbol.split('').reduce((sum, char) => sum + char.charCodeAt(0), 0) % 360;

  useEffect(() => {
    setSourceIndex(0);
    setLoaded(false);
  }, [logoUrl, symbol]);

  const source = sources[sourceIndex];
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
        height={size}
        onError={() => {
          setLoaded(false);
          setSourceIndex((current) => current + 1);
        }}
        onLoad={(event) => {
          if (event.currentTarget.naturalWidth <= 1 || event.currentTarget.naturalHeight <= 1) {
            setSourceIndex((current) => current + 1);
            return;
          }
          setLoaded(true);
          saveSuccessfulSource(symbol, logoUrl, source);
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

function iconSources(symbol: string, logoUrl?: string) {
  const cleaned = logoUrl?.trim();
  const cached = cachedSuccessfulSource(symbol, cleaned);
  return [
    cached,
    cleaned,
    `https://assets.parqet.com/logos/symbol/${symbol.toUpperCase()}?format=png`,
    `https://financialmodelingprep.com/image-stock/${symbol.toUpperCase()}.png`,
  ].filter((value, index, items): value is string => Boolean(value) && items.indexOf(value) === index);
}

const LOGO_CACHE_PREFIX = 'market-opinion:instrument-logo:v1:';

interface LogoCacheEntry {
  configuredUrl: string;
  source: string;
}

function cachedSuccessfulSource(symbol: string, configuredUrl?: string) {
  try {
    const raw = window.localStorage.getItem(LOGO_CACHE_PREFIX + symbol.toUpperCase());
    if (!raw) return undefined;
    const entry = JSON.parse(raw) as LogoCacheEntry;
    return entry.configuredUrl === (configuredUrl || '') ? entry.source : undefined;
  } catch {
    return undefined;
  }
}

function saveSuccessfulSource(symbol: string, configuredUrl: string | undefined, source: string) {
  try {
    const entry: LogoCacheEntry = { configuredUrl: configuredUrl?.trim() || '', source };
    window.localStorage.setItem(LOGO_CACHE_PREFIX + symbol.toUpperCase(), JSON.stringify(entry));
  } catch {
    // WebView 禁用存储时仍正常显示本次已加载图标。
  }
}
