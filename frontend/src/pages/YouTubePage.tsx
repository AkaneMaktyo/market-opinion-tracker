import { ArrowLeft } from 'lucide-react';
import { AppBrand } from '../components/brand/AppBrand';
import { YouTubeWorkspace } from '../components/sources/youtube/YouTubeWorkspace';
import { goToDashboard } from '../hashRoute';

export function YouTubePage() {
  return (
    <main className="youtube-page-shell">
      <header className="youtube-page-header">
        <div className="youtube-page-brand">
          <AppBrand />
          <div>
            <div className="panel-title">YouTube 音频转写</div>
            <p className="muted">独立页面更适合手机端查看频道、视频和逐段转写。</p>
          </div>
        </div>
        <button className="primary secondary youtube-back-button" onClick={goToDashboard} type="button">
          <ArrowLeft size={16} />
          返回主面板
        </button>
      </header>
      <section className="youtube-page-body">
        <YouTubeWorkspace mode="page" />
      </section>
    </main>
  );
}
