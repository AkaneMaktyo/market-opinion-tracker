import { useState } from 'react';
import { MobileFuturesPositions } from './MobileFuturesPositions';
import { SpotPositionsPanel } from './SpotPositionsPanel';

type PositionsTab = 'spot' | 'futures';

/** 持仓页外壳：现货（含股票）与合约两个分区切换，内容由各自面板负责。 */
export function MobilePositions() {
  const [tab, setTab] = useState<PositionsTab>('spot');
  return (
    <div className="mobile-screen-content mobile-positions">
      <div className="mobile-position-tabs" role="tablist" aria-label="持仓分区">
        <button
          aria-selected={tab === 'spot'}
          className={tab === 'spot' ? 'active' : ''}
          onClick={() => setTab('spot')}
          role="tab"
          type="button"
        >现货 · 股票</button>
        <button
          aria-selected={tab === 'futures'}
          className={tab === 'futures' ? 'active' : ''}
          onClick={() => setTab('futures')}
          role="tab"
          type="button"
        >合约</button>
      </div>
      {tab === 'spot' ? <SpotPositionsPanel /> : <MobileFuturesPositions />}
    </div>
  );
}
