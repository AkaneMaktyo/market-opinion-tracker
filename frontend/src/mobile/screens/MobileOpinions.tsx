import { BellPlus, Search } from 'lucide-react';
import { useMemo, useState } from 'react';
import type { Direction, OpinionView } from '../../types';
import { directionLabel, formatDate } from './MobileOverview';
import type { DashboardModel } from './mobileTypes';

interface Props {
  dashboard: DashboardModel;
  onQuickAdd: () => void;
}

type OpinionFilter = 'ALL' | Direction;

export function MobileOpinions({ dashboard, onQuickAdd }: Props) {
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<OpinionFilter>('ALL');
  const items = useMemo(() => filterOpinions(dashboard.opinions, filter, query), [dashboard.opinions, filter, query]);

  return (
    <div className="mobile-screen-content mobile-opinions-screen">
      <div className="mobile-opinion-toolbar">
        <label className="mobile-search-box">
          <Search aria-hidden="true" size={18} />
          <input onChange={(event) => setQuery(event.target.value)} placeholder="搜索观点关键词" type="search" value={query} />
        </label>
        <select aria-label="切换标的" onChange={(event) => dashboard.selectSymbol(event.target.value)} value={dashboard.selected}>
          {dashboard.instruments.map((item) => <option key={item.id} value={item.symbol}>{item.symbol}</option>)}
        </select>
      </div>
      <div className="mobile-filter-row" aria-label="观点筛选">
        {([['ALL', '全部'], ['BULLISH', '看多'], ['BEARISH', '看空'], ['WATCH', '观察']] as const).map(([value, label]) => (
          <button className={filter === value ? 'active' : ''} key={value} onClick={() => setFilter(value)} type="button">{label}</button>
        ))}
      </div>
      <div className="mobile-list-title"><strong>{dashboard.selected || '观点时间线'}</strong><small>{items.length} 条结果</small></div>
      <section className="mobile-opinion-feed">
        {items.length === 0 ? <div className="mobile-card mobile-empty">没有符合条件的观点</div> : items.map((item) => <OpinionCard item={item} key={item.opinion.id} />)}
      </section>
      <button className="mobile-floating-action" onClick={onQuickAdd} type="button"><BellPlus size={19} />记录新观点</button>
    </div>
  );
}

function OpinionCard({ item }: { item: OpinionView }) {
  const { opinion, priceLevels } = item;
  const message = opinion.status === 'MESSAGE';
  return (
    <article className="mobile-card mobile-feed-card">
      <div className="mobile-feed-top">
        <span className="mobile-avatar">{opinion.symbol.slice(0, 1)}</span>
        <div><strong>{opinion.symbol}</strong><small>{formatDate(opinion.opinionTime)} · {opinion.horizon || '未设周期'}</small></div>
        <b className={`mobile-direction mobile-direction-${opinion.direction.toLowerCase()}`}>{message ? '消息' : directionLabel(opinion.direction)}</b>
      </div>
      {opinion.rawDirection ? <p className="mobile-raw-direction">{opinion.rawDirection}</p> : null}
      <p className="mobile-feed-thesis">{opinion.thesis}</p>
      {priceLevels.length > 0 ? <div className="mobile-level-row">{priceLevels.map((level) => <span key={`${level.levelType}-${level.price}`}>{levelLabel(level.levelType)} {level.price}</span>)}</div> : null}
      {opinion.risksText ? <details><summary>风险与失效条件</summary><p>{opinion.risksText}</p></details> : null}
    </article>
  );
}

function filterOpinions(items: OpinionView[], filter: OpinionFilter, query: string) {
  const keyword = query.trim().toLowerCase();
  return [...items]
    .filter(({ opinion }) => filter === 'ALL' || opinion.direction === filter)
    .filter(({ opinion }) => !keyword || [opinion.symbol, opinion.thesis, opinion.sourceQuote, opinion.rawDirection].some((value) => value?.toLowerCase().includes(keyword)))
    .sort((left, right) => right.opinion.opinionTime.localeCompare(left.opinion.opinionTime));
}

function levelLabel(value: string) {
  return ({ SUPPORT: '支撑', RESISTANCE: '压力', TARGET: '目标', STOP: '止损', NOTE: '价位' } as Record<string, string>)[value] || value;
}
