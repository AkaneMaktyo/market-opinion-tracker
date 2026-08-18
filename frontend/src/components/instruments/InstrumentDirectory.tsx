import { Pencil, Search, X } from 'lucide-react';
import { useDeferredValue, useMemo, useState } from 'react';
import type { Instrument } from '../../types';
import { InstrumentLogo } from './InstrumentLogo';
import { InstrumentManager } from './InstrumentManager';

interface Props {
  instruments: Instrument[];
  selected: string;
  onChanged: (nextSelected?: string) => void;
  onClose: () => void;
}

export function InstrumentDirectory({ instruments, selected, onChanged, onClose }: Props) {
  const [query, setQuery] = useState('');
  const [managing, setManaging] = useState<Instrument | null>(null);
  const deferredQuery = useDeferredValue(query);
  const filtered = useMemo(() => {
    const terms = searchTerms(deferredQuery);
    return instruments
      .map((item) => ({ item, score: instrumentSearchScore(item, terms) }))
      .filter((entry) => entry.score != null)
      .sort((left, right) => left.score! - right.score!
        || left.item.symbol.localeCompare(right.item.symbol))
      .map((entry) => entry.item);
  }, [instruments, deferredQuery]);

  return (
    <>
      <div className="modal-backdrop" onMouseDown={onClose}>
        <section className="entry directory-modal" onMouseDown={(event) => event.stopPropagation()}>
          <div className="modal-head">
            <div>
              <div className="panel-title">品种管理中心</div>
              <p>这里可以快速找到品种，再进入重命名、归并、行情源设置或删除。</p>
            </div>
            <button className="icon-button" onClick={onClose} type="button">
              <X size={18} />
            </button>
          </div>

          <div className="directory-toolbar">
            <div className="directory-search">
              <Search size={15} />
              <input
                aria-label="搜索品种"
                autoCapitalize="none"
                autoComplete="off"
                onChange={(event) => setQuery(event.target.value)}
                placeholder="代码、名称、市场或行业"
                spellCheck={false}
                value={query}
              />
              {query ? <button aria-label="清空搜索" onClick={() => setQuery('')} type="button"><X size={15} /></button> : null}
            </div>
            <span className="directory-result-count">{query ? `${filtered.length} 个结果` : `${instruments.length} 个品种`}</span>
          </div>

          <div className="directory-list">
            {filtered.length === 0 ? <div className="directory-empty">没有找到“{query.trim()}”，可尝试代码、中文名或市场名称。</div> : null}
            {filtered.map((item) => (
              <div className={item.symbol === selected ? 'directory-item active' : 'directory-item'} key={item.id}>
                <span className="directory-main">
                  <InstrumentLogo symbol={item.symbol} logoUrl={item.logoUrl} size={22} />
                  <span>
                    <strong>{item.symbol}</strong>
                    <span className="directory-name">{item.name || '未命名'}</span>
                  </span>
                </span>
                <span className="directory-meta">
                  <button className="icon-button" onClick={() => setManaging(item)} type="button">
                    <Pencil size={15} />
                  </button>
                </span>
              </div>
            ))}
          </div>
        </section>
      </div>
      {managing && (
        <InstrumentManager
          instrument={managing}
          instruments={instruments}
          onChanged={(nextSelected) => {
            setManaging(null);
            onClose();
            onChanged(nextSelected);
          }}
          onClose={() => setManaging(null)}
        />
      )}
    </>
  );
}

function searchTerms(value: string) {
  return [...new Set(value.normalize('NFKC').toLocaleLowerCase()
    .split(/[\p{P}\p{S}\s]+/u).filter(Boolean))];
}

function instrumentSearchScore(item: Instrument, terms: string[]): number | null {
  if (terms.length === 0) return 0;
  const symbol = item.symbol.normalize('NFKC').toLocaleLowerCase();
  const name = (item.name || '').normalize('NFKC').toLocaleLowerCase();
  const market = (item.market || '').normalize('NFKC').toLocaleLowerCase();
  const sector = (item.sector || '').normalize('NFKC').toLocaleLowerCase();
  const searchable = `${symbol} ${name} ${market} ${sector}`;
  if (!terms.every((term) => searchable.includes(term))) return null;
  const phrase = terms.join(' ');
  if (symbol === phrase) return 0;
  if (symbol.startsWith(phrase)) return 5;
  if (name === phrase) return 10;
  if (name.startsWith(phrase)) return 15;
  return terms.reduce((score, term) => score
    + (symbol.includes(term) ? 2 : 0)
    + (name.includes(term) ? 4 : 0)
    + (market.includes(term) || sector.includes(term) ? 8 : 0), 20);
}
