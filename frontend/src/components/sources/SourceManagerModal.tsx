import { Pencil, RefreshCw, RotateCcw, Save, X } from 'lucide-react';
import type { Dispatch, SetStateAction } from 'react';
import type {
  WxPusherBlogger,
  WxPusherMessage,
  WxPusherSettings,
  WxPusherStatus,
} from '../../types';

interface Props {
  settings: WxPusherSettings;
  status: WxPusherStatus | null;
  bloggers: WxPusherBlogger[];
  messages: WxPusherMessage[];
  draft: { id: string; bloggerName: string; aliasesText: string; enabled: boolean };
  loading: boolean;
  message: string;
  setSettings: Dispatch<SetStateAction<WxPusherSettings>>;
  setDraft: Dispatch<SetStateAction<{ id: string; bloggerName: string; aliasesText: string; enabled: boolean }>>;
  setMessage: (value: string) => void;
  onClose: () => void;
  onRefresh: () => void;
  onSaveSettings: () => void;
  onSaveBlogger: () => void;
  onEditBlogger: (blogger: WxPusherBlogger) => void;
  onRetryMessage: (id: string) => void;
}

export function SourceManagerModal(props: Props) {
  const {
    settings,
    status,
    bloggers,
    messages,
    draft,
    loading,
    message,
    setSettings,
    setDraft,
    setMessage,
    onClose,
    onRefresh,
    onSaveSettings,
    onSaveBlogger,
    onEditBlogger,
    onRetryMessage,
  } = props;

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <section className="entry source-modal" onMouseDown={(event) => event.stopPropagation()}>
        <div className="modal-head">
          <div>
            <div className="panel-title">来源管理</div>
            <p>管理 WxPusher 登录态、博主白名单和最近失败消息。</p>
          </div>
          <div className="inline-actions">
            <button className="icon-button" onClick={onRefresh} title="刷新" type="button">
              <RefreshCw size={16} />
            </button>
            <button className="icon-button" onClick={onClose} title="关闭" type="button">
              <X size={18} />
            </button>
          </div>
        </div>

        <div className="source-layout">
          <section className="source-panel">
            <div className="panel-title">运行状态</div>
            {status ? (
              <div className="status-grid">
                <StatusItem label="监听状态" value={status.websocketState} />
                <StatusItem label="轮询 / 实时" value={`${flag(status.pollingEnabled)} / ${flag(status.websocketEnabled)}`} />
                <StatusItem label="博主数" value={`${status.enabledBloggers} / ${status.totalBloggers}`} />
                <StatusItem label="模型状态" value={status.llmConfigured ? (status.llmReachable ? '可用' : '不可用') : '未配置'} />
                <StatusItem label="最近轮询" value={trimTime(status.lastPollAt)} />
                <StatusItem label="最近心跳" value={trimTime(status.lastHeartbeatAt)} />
              </div>
            ) : (
              <p className="muted">正在读取状态…</p>
            )}
            {status?.llmMessage ? <p className="muted">模型检查：{status.llmMessage}</p> : null}
            {status?.lastError ? <div className="form-message">{status.lastError}</div> : null}
          </section>

          <section className="source-panel">
            <div className="panel-title">WxPusher 配置</div>
            <div className="form-grid two">
              <label>deviceToken<input value={settings.deviceToken} onChange={(e) => setSettings((s) => ({ ...s, deviceToken: e.target.value }))} /></label>
              <label>pushToken<input value={settings.pushToken} onChange={(e) => setSettings((s) => ({ ...s, pushToken: e.target.value }))} /></label>
              <label>deviceUuid<input value={settings.deviceUuid} onChange={(e) => setSettings((s) => ({ ...s, deviceUuid: e.target.value }))} /></label>
              <label>轮询秒数<input min={30} type="number" value={settings.pollIntervalSeconds} onChange={(e) => setSettings((s) => ({ ...s, pollIntervalSeconds: Number(e.target.value) || 60 }))} /></label>
              <label>platform<input value={settings.platform} onChange={(e) => setSettings((s) => ({ ...s, platform: e.target.value }))} /></label>
              <label>version<input value={settings.version} onChange={(e) => setSettings((s) => ({ ...s, version: e.target.value }))} /></label>
            </div>
            <div className="toggle-row">
              <label className="toggle"><input checked={settings.enablePolling} onChange={(e) => setSettings((s) => ({ ...s, enablePolling: e.target.checked }))} type="checkbox" />启用 REST 轮询</label>
              <label className="toggle"><input checked={settings.enableWebsocket} onChange={(e) => setSettings((s) => ({ ...s, enableWebsocket: e.target.checked }))} type="checkbox" />启用 WebSocket</label>
            </div>
            <button className="primary" disabled={loading} onClick={onSaveSettings} type="button">
              <Save size={16} />
              保存来源配置
            </button>
          </section>
        </div>

        <div className="source-layout">
          <section className="source-panel">
            <div className="panel-title">博主白名单</div>
            <div className="form-grid two">
              <label>博主名称<input value={draft.bloggerName} onChange={(e) => setDraft((s) => ({ ...s, bloggerName: e.target.value }))} /></label>
              <label>别名（逗号分隔）<input value={draft.aliasesText} onChange={(e) => setDraft((s) => ({ ...s, aliasesText: e.target.value }))} /></label>
            </div>
            <label className="toggle"><input checked={draft.enabled} onChange={(e) => setDraft((s) => ({ ...s, enabled: e.target.checked }))} type="checkbox" />启用并补抓最近 30 条</label>
            <div className="inline-actions">
              <button className="primary" disabled={loading} onClick={onSaveBlogger} type="button">
                <Save size={16} />
                {draft.id ? '更新博主' : '添加博主'}
              </button>
              {draft.id ? (
                <button
                  className="primary secondary"
                  onClick={() => {
                    setDraft({ id: '', bloggerName: '', aliasesText: '', enabled: true });
                    setMessage('');
                  }}
                  type="button"
                >
                  <RotateCcw size={16} />
                  取消编辑
                </button>
              ) : null}
            </div>
            <div className="blogger-list">
              {bloggers.map((blogger) => (
                <div className="blogger-row" key={blogger.id}>
                  <div>
                    <strong>{blogger.bloggerName}</strong>
                    <p className="muted">别名：{blogger.aliases.join(', ') || '无'} ｜ 种子：{blogger.seedCompletedAt ? '已完成' : '待补抓'}</p>
                  </div>
                  <div className="inline-actions">
                    <span className={blogger.enabled ? 'status-pill active' : 'status-pill'}>{blogger.enabled ? '启用' : '停用'}</span>
                    <button className="icon-button" onClick={() => onEditBlogger(blogger)} title="编辑" type="button">
                      <Pencil size={15} />
                    </button>
                  </div>
                </div>
              ))}
              {bloggers.length === 0 ? <p className="muted">还没有配置任何博主。</p> : null}
            </div>
          </section>

          <section className="source-panel">
            <div className="panel-title">最近失败消息</div>
            <div className="message-audit">
              {messages.map((item) => (
                <div className="message-row" key={item.id}>
                  <div>
                    <strong>{item.bloggerName}</strong>
                    <p>{item.title || item.summary || '无标题消息'}</p>
                    <p className="muted">{trimTime(item.messageTime)} ｜ {item.errorMessage || '待重试'}</p>
                  </div>
                  <button className="primary secondary" disabled={loading} onClick={() => onRetryMessage(item.id)} type="button">
                    重试
                  </button>
                </div>
              ))}
              {messages.length === 0 ? <p className="muted">最近没有失败消息。</p> : null}
            </div>
          </section>
        </div>

        {message ? <div className="form-message">{message}</div> : null}
      </section>
    </div>
  );
}

function StatusItem({ label, value }: { label: string; value?: string }) {
  return (
    <div className="status-item">
      <span>{label}</span>
      <strong>{value || '暂无'}</strong>
    </div>
  );
}

function flag(value: boolean) {
  return value ? '开' : '关';
}

function trimTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '暂无';
}
