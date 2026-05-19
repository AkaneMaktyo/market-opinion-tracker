import { useEffect, useState } from 'react';

interface Props {
  symbol: string;
  logoUrl?: string;
  size?: number;
}

export function InstrumentLogo({ symbol, logoUrl, size = 20 }: Props) {
  const [sourceIndex, setSourceIndex] = useState(0);
  const sources = iconSources(symbol, logoUrl);
  const hue = symbol.split('').reduce((sum, char) => sum + char.charCodeAt(0), 0) % 360;

  useEffect(() => {
    setSourceIndex(0);
  }, [logoUrl, symbol]);

  if (sourceIndex < sources.length) {
    return (
      <img
        alt={symbol}
        className="stock-icon"
        height={size}
        onError={() => setSourceIndex((current) => current + 1)}
        src={sources[sourceIndex]}
        width={size}
      />
    );
  }

  return (
    <span
      className="stock-icon stock-icon-fallback"
      style={{ backgroundColor: `hsl(${hue}, 55%, 45%)`, height: size, width: size }}
    >
      {symbol.charAt(0)}
    </span>
  );
}

function iconSources(symbol: string, logoUrl?: string) {
  const cleaned = logoUrl?.trim();
  return [
    cleaned,
    `https://s3-symbol-logo.tradingview.com/${symbol.toLowerCase()}.svg`,
    `https://financialmodelingprep.com/image-stock/${symbol.toUpperCase()}.png`,
  ].filter((value, index, items): value is string => Boolean(value) && items.indexOf(value) === index);
}
