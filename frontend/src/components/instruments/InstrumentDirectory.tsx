import { FolderTree, Pencil, Search, X } from 'lucide-react';
import { useMemo, useState } from 'react';
import type { Instrument } from '../../types';
import { InstrumentLogo } from './InstrumentLogo';
import { InstrumentManager } from './InstrumentManager';

interface Props {
  groups: string[];
  instruments: Instrument[];
  kolId: string;
  selected: string;
  onChanged: (nextSelected?: string) => void;
  onClose: () => void;
}

export function InstrumentDirectory({ groups, instruments, kolId, selected, onChanged, onClose }: Props) {
  const [query, setQuery] = useState('');
  const [managing, setManaging] = useState<Instrument | null>(null);
  const filtered = useMemo(() => {
    const keyword = query.trim().toUpperCase();
    return instruments
      .filter((item) => !keyword
        || item.symbol.includes(keyword)
        || (item.name || '').toUpperCase().includes(keyword))
      .sort((left, right) => left.symbol.localeCompare(right.symbol));
  }, [instruments, query]);

  return (
    <>
      <div className="modal-backdrop" onMouseDown={onClose}>
        <section className="entry directory-modal" onMouseDown={(event) => event.stopPropagation()}>
          <div className="modal-head">
            <div>
              <div className="panel-title">品种管理中心</div>
              <p>这里可以快速找到品种，再进入重命名、归并或分组。</p>
            </div>
            <button className="icon-button" onClick={onClose} type="button">
              <X size={18} />
            </button>
          </div>

          <div className="directory-toolbar">
            <label className="directory-search">
              <Search size={15} />
              <input
                onChange={(event) => setQuery(event.target.value)}
                placeholder="搜索代码或名称"
                value={query}
              />
            </label>
            <div className="directory-groups">
              <span className="directory-group-pill">
                <FolderTree size={14} />
                {groups.length} 个分组
              </span>
            </div>
          </div>

          <div className="directory-list">
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
                  <span className="directory-badge">{item.groupName || '未分组'}</span>
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
          groups={groups}
          instrument={managing}
          instruments={instruments}
          kolId={kolId}
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
