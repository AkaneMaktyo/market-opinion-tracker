import { X } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { WxPusherBlogger } from '../../types';

interface Props {
  onClose: () => void;
}

export function MobileNotifySettings({ onClose }: Props) {
  const [bloggers, setBloggers] = useState<WxPusherBlogger[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [savingId, setSavingId] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setBloggers(await api.wxpusherBloggers());
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '读取 KOL 列表失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function toggle(blogger: WxPusherBlogger) {
    const next = !blogger.notifyEnabled;
    setBloggers((items) => items.map((item) => item.id === blogger.id ? { ...item, notifyEnabled: next } : item));
    setSavingId(blogger.id);
    setError('');
    try {
      await api.updateWxPusherBlogger({
        id: blogger.id,
        bloggerName: blogger.bloggerName,
        aliases: blogger.aliases,
        enabled: blogger.enabled,
        notifyEnabled: next,
      });
    } catch (saveError) {
      setBloggers((items) => items.map((item) => item.id === blogger.id ? { ...item, notifyEnabled: blogger.notifyEnabled } : item));
      setError(saveError instanceof Error ? saveError.message : '保存失败，请重试');
    } finally {
      setSavingId('');
    }
  }

  return (
    <div className="modal-backdrop mobile-quick-backdrop" onMouseDown={onClose}>
      <section className="mobile-quick-sheet mobile-notify-sheet" onMouseDown={(event) => event.stopPropagation()}>
        <div className="mobile-sheet-handle" />
        <div className="mobile-sheet-title">
          <div><h2>新消息通知</h2><small>每个 KOL 可单独开启或关闭手机通知</small></div>
          <button aria-label="关闭" onClick={onClose} type="button"><X size={20} /></button>
        </div>
        {error ? <div className="mobile-card mobile-message-error">{error}</div> : null}
        {loading ? <div className="mobile-card mobile-empty">正在读取 KOL 列表…</div> : null}
        {!loading && bloggers.length === 0 ? <div className="mobile-card mobile-empty">还没有配置 KOL</div> : null}
        {!loading && bloggers.length > 0 ? (
          <div className="mobile-notify-list">
            {bloggers.map((blogger) => (
              <div className="mobile-notify-row" key={blogger.id}>
                <span className="mobile-notify-avatar">{blogger.bloggerName.slice(0, 1) || '讯'}</span>
                <span className="mobile-notify-copy">
                  <strong>{blogger.bloggerName}</strong>
                  <small>{blogger.messageCount} 条消息 · {blogger.importedMessageCount} 条已提取</small>
                </span>
                <button
                  aria-checked={blogger.notifyEnabled}
                  aria-label={`${blogger.bloggerName} 消息通知`}
                  className="mobile-switch"
                  disabled={savingId === blogger.id}
                  onClick={() => void toggle(blogger)}
                  role="switch"
                  type="button"
                >
                  <span />
                </button>
              </div>
            ))}
          </div>
        ) : null}
        <p className="mobile-notify-tip">通知在 App 运行或近期使用过时生效；长时间后台休眠的系统可能会暂停接收。</p>
      </section>
    </div>
  );
}
