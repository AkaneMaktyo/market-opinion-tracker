import { FolderTree } from 'lucide-react';
import { InstrumentGroupList } from '../../components/instruments/InstrumentGroupList';
import { InstrumentSortBar } from '../../components/instruments/InstrumentSortBar';
import {
  applyManualOrder,
  groupItems,
  mergeDefaultInstrument,
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
    ? applyManualOrder(mergeDefaultInstrument(dashboard.instruments), state.order)
    : dashboard.instruments;
  const grouped = customMode
    ? groupItems(sortItems(railItems, state.mode), state.groupOrder)
    : [{ group: '', items: railItems }];

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
          <button className="rail-manage" onClick={state.openDirectory} type="button">
            <FolderTree size={14} />
            <span>管理</span>
          </button>
        </div>
        {customMode ? <InstrumentSortBar mode={state.mode} onChange={changeMode} /> : null}
        <div className="rail-table-head">
          <span>商品</span>
          <span>最新价</span>
          <span>涨跌</span>
          <span>涨跌%</span>
        </div>
        <InstrumentGroupList
          collapsedGroups={state.collapsedGroups}
          draggingGroup={state.draggingGroup}
          draggingItem={state.draggingItem}
          dropGroup={state.dropGroup}
          grouped={grouped}
          manualMode={customMode && state.mode === 'manual'}
          onDragItemEnd={state.resetItemDrag}
          onDragItemOver={state.dragItemOver}
          onDragItemStart={state.startItemDrag}
          onDropGroup={state.dropGroupOn}
          onDropItem={(event, symbol) => state.dropItemOn(event, symbol, railItems)}
          onManage={state.openManager}
          onSelect={onOpenDetail}
          onSetDraggingGroup={state.setDraggingGroup}
          onSetDropGroup={state.setDropGroup}
          onToggleGroup={state.toggleGroup}
          selected={dashboard.selected}
        />
      </aside>
      {state.renderOverlays()}
    </div>
  );
}
