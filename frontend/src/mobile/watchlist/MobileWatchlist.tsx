import { Plus, Settings } from 'lucide-react';
import { FlatInstrumentList } from '../../components/instruments/FlatInstrumentList';
import { InstrumentSortBar } from '../../components/instruments/InstrumentSortBar';
import {
  applyManualOrder,
  sortItems,
  type SortMode,
} from '../../components/instruments/instrumentList';
import type { DashboardModel } from '../screens/mobileTypes';
import { useMobileInstrumentList } from './useMobileInstrumentList';

interface Props {
  dashboard: DashboardModel;
  onOpenDetail: (symbol: string) => void;
}

const DEFAULT_KOL = 'default';

export function MobileWatchlist({ dashboard, onOpenDetail }: Props) {
  const customMode = dashboard.selectedKol === DEFAULT_KOL;
  const state = useMobileInstrumentList(dashboard, customMode);
  const railItems = customMode
    ? applyManualOrder(dashboard.instruments, state.order)
    : dashboard.instruments;
  const visibleItems = customMode ? sortItems(railItems, state.mode) : railItems;

  function changeMode(mode: SortMode) {
    state.setMode(mode);
    window.localStorage.setItem(state.storage.sort, mode);
  }

  return (
    <div className="mobile-watchlist-screen">
      <aside className="rail mobile-instrument-rail">
        <div className="rail-head">
          <div>
            <div className="panel-title">自选表</div>
            <span className="rail-note">{customMode ? (state.mode === 'manual' ? '自定义排序' : '行情排序') : '最新观点排序'}</span>
          </div>
          <div className="mobile-watchlist-head-actions">
            <button className="rail-manage" onClick={state.openAdd} type="button"><Plus size={14} /><span>新增</span></button>
            <button className="rail-manage" onClick={state.openDirectory} type="button"><Settings size={14} /><span>管理</span></button>
          </div>
        </div>
        {customMode ? <InstrumentSortBar mode={state.mode} onChange={changeMode} /> : null}
        <div className="rail-table-head">
          <span>商品</span>
          <span>最新价</span>
          <span>涨跌</span>
          <span>涨跌%</span>
          <span>操作</span>
        </div>
        {state.message ? <p className="mobile-watchlist-message">{state.message}</p> : null}
        <FlatInstrumentList
          draggingItem={state.draggingItem}
          items={visibleItems}
          manualMode={customMode && state.mode === 'manual'}
          onDragItemEnd={state.resetItemDrag}
          onDragItemOver={state.dragItemOver}
          onDragItemStart={state.startItemDrag}
          onDropItem={(event, symbol) => state.dropItemOn(event, symbol, railItems)}
          onManage={state.openManager}
          onRemove={state.removeFromWatchlist}
          onSelect={onOpenDetail}
          selected={dashboard.selected}
          removingId={state.removing}
        />
      </aside>
      {state.renderOverlays()}
    </div>
  );
}
