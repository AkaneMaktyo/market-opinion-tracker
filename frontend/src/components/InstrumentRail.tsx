import type { Instrument } from '../types';

interface Props {
  instruments: Instrument[];
  selected: string;
  onSelect: (symbol: string) => void;
}

export function InstrumentRail({ instruments, selected, onSelect }: Props) {
  return (
    <aside className="rail">
      <div className="panel-title">品种</div>
      <button
        className={selected === 'NVDA' ? 'symbol active' : 'symbol'}
        onClick={() => onSelect('NVDA')}
      >
        <strong>NVDA</strong>
        <span>示例</span>
      </button>
      {instruments.map((item) => (
        <button
          className={selected === item.symbol ? 'symbol active' : 'symbol'}
          key={item.id}
          onClick={() => onSelect(item.symbol)}
        >
          <strong>{item.symbol}</strong>
          <span>{item.name || item.sector || 'US'}</span>
        </button>
      ))}
    </aside>
  );
}
