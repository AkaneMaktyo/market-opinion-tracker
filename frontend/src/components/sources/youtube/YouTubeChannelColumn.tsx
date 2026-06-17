import { RefreshCw, Trash2 } from 'lucide-react';
import type { YouTubeChannel } from '../../../types/youtube';
import { formatDateTimeLabel } from './youtubeFormat';

interface Props {
  activeVideoId: string;
  channels: YouTubeChannel[];
  loading: boolean;
  onRemove: (channelRowId: string) => void;
  onSync: (channelRowId: string, activeVideoId: string) => void;
}

export function YouTubeChannelColumn({
  activeVideoId,
  channels,
  loading,
  onRemove,
  onSync,
}: Props) {
  return (
    <div className="youtube-column">
      <div className="youtube-subhead">
        <strong>频道</strong>
        <span className="muted">{loading ? '处理中...' : '支持单独同步'}</span>
      </div>
      <div className="youtube-list">
        {channels.map((item) => (
          <article className="youtube-card" key={item.channel.id}>
            <div>
              <strong>{item.channel.title}</strong>
              <p className="muted">
                上次同步：{item.channel.lastCheckedAt ? formatDateTimeLabel(item.channel.lastCheckedAt) : '尚未同步'}
              </p>
            </div>
            <div className="inline-actions">
              <button
                className="icon-button"
                onClick={() => onSync(item.channel.id, activeVideoId)}
                title="同步频道"
                type="button"
              >
                <RefreshCw size={14} />
              </button>
              <button
                className="icon-button"
                onClick={() => onRemove(item.channel.id)}
                title="删除频道"
                type="button"
              >
                <Trash2 size={14} />
              </button>
            </div>
          </article>
        ))}
        {!channels.length ? <div className="muted">还没有接入任何频道。</div> : null}
      </div>
    </div>
  );
}
