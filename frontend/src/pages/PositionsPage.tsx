import { ArrowLeft } from 'lucide-react';
import { AppBrand } from '../components/brand/AppBrand';
import { PositionsWorkspace } from '../components/positions/PositionsWorkspace';
import { goToDashboard } from '../hashRoute';

export function PositionsPage() {
  return (
    <main className="positions-page-shell">
      <header className="positions-page-header">
        <div className="positions-page-brand">
          <AppBrand />
          <div>
            <div className="panel-title">虚拟持仓跟单</div>
            <p className="muted">基于消息观点的虚拟开平仓结算：胜率、单笔与累计盈亏。</p>
          </div>
        </div>
        <button className="primary secondary positions-back-button" onClick={goToDashboard} type="button">
          <ArrowLeft size={16} />
          返回主面板
        </button>
      </header>
      <section className="positions-page-body">
        <PositionsWorkspace />
      </section>
    </main>
  );
}
