import { ArrowLeft } from 'lucide-react';
import { AppBrand } from '../components/brand/AppBrand';
import { CelebrityPortfolioWorkspace } from '../components/celebrity/CelebrityPortfolioWorkspace';
import { goToDashboard } from '../hashRoute';

export function CelebrityPortfolioPage() {
  return (
    <main className="celebrity-page-shell">
      <header className="celebrity-page-header">
        <div className="celebrity-page-brand">
          <AppBrand />
          <div><div className="panel-title">名人持仓雷达</div><p className="muted">官方公开披露跟踪 · 估算与原始披露分层展示</p></div>
        </div>
        <button className="primary secondary positions-back-button" onClick={goToDashboard} type="button"><ArrowLeft size={16} />返回主面板</button>
      </header>
      <section className="celebrity-page-body"><CelebrityPortfolioWorkspace /></section>
    </main>
  );
}
