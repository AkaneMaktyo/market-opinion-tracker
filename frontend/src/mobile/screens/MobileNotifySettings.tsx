import { BellRing, CircleAlert, RadioTower, Settings, X } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { WxPusherBlogger } from '../../types';
import {
  enableAndroidNotifications,
  openAndroidNotificationSettings,
  readAndroidPushStatus,
  type AndroidPushStatus,
} from '../useJpushOpen';

interface Props {
  onClose: () => void;
}

export function MobileNotifySettings({ onClose }: Props) {
  const [bloggers, setBloggers] = useState<WxPusherBlogger[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [savingId, setSavingId] = useState('');
  const [pushStatus, setPushStatus] = useState<AndroidPushStatus | null>(null);
  const [enablingSystem, setEnablingSystem] = useState(false);

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
    void refreshPushStatus();
  }, [load]);

  async function refreshPushStatus() {
    try {
      setPushStatus(await readAndroidPushStatus());
    } catch {
      setPushStatus(null);
    }
  }

  async function enableSystemNotifications() {
    setEnablingSystem(true);
    try {
      const granted = await enableAndroidNotifications();
      if (!granted) await openAndroidNotificationSettings();
      await refreshPushStatus();
    } finally {
      setEnablingSystem(false);
    }
  }

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
        {pushStatus ? (
          <div className="mobile-push-health">
            <div className={pushStatus.notificationsEnabled ? 'healthy' : 'warning'}>
              {pushStatus.notificationsEnabled ? <BellRing size={18} /> : <CircleAlert size={18} />}
              <span><strong>系统通知</strong><small>{pushStatus.notificationsEnabled ? '已允许显示通知' : '未开启，所有推送都会被系统拦截'}</small></span>
              {!pushStatus.notificationsEnabled ? <button disabled={enablingSystem} onClick={() => void enableSystemNotifications()} type="button"><Settings size={15} />{enablingSystem ? '处理中' : '去开启'}</button> : null}
            </div>
            <div className={pushStatus.registered && pushStatus.aliasBound ? 'healthy' : 'warning'}>
              <RadioTower size={18} />
              <span><strong>推送服务</strong><small>{pushServiceText(pushStatus)}</small></span>
            </div>
          </div>
        ) : null}
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
        <p className="mobile-notify-tip">应用会在后台保持推送连接。若系统仍限制接收，请允许应用自启动，并把电池策略设为“不限制”；进程被彻底清理时需依赖已配置的手机厂商通道。</p>
      </section>
    </div>
  );
}

function pushServiceText(status: AndroidPushStatus): string {
  if (status.registered && status.aliasBound) return '设备已注册，推送目标绑定正常';
  if (status.error) return status.error;
  if (!status.registered) return '正在连接极光推送服务';
  return '设备已注册，正在绑定推送目标';
}
