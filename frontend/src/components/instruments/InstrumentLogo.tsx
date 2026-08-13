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

  return (
    <span className="stock-icon-shell" style={{ height: size, width: size }}>
      <span
        className="stock-icon-fallback"
        style={{ backgroundColor: `hsl(${hue}, 55%, 45%)`, height: size, width: size }}
      >
        {symbol.charAt(0)}
      </span>
      {sourceIndex < sources.length ? (
      <img
        alt={symbol}
        className="stock-icon"
        height={size}
        onError={() => setSourceIndex((current) => current + 1)}
        referrerPolicy="no-referrer"
        src={sources[sourceIndex]}
        width={size}
      />
      ) : null}
    </span>
  );
}

function iconSources(symbol: string, logoUrl?: string) {
  const cleaned = logoUrl?.trim();
  return [
    cleaned,
    `https://assets.parqet.com/logos/symbol/${symbol.toUpperCase()}?format=png`,
    `https://financialmodelingprep.com/image-stock/${symbol.toUpperCase()}.png`,
  ].filter((value, index, items): value is string => Boolean(value) && items.indexOf(value) === index);
}
