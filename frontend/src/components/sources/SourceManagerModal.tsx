import { RefreshCw, X } from 'lucide-react';
import type { WxPusherBlogger, WxPusherMessage, WxPusherNotifySettings, WxPusherSettings, WxPusherStatus } from '../../types';
import { BloggerPositionPanel } from './BloggerPositionPanel';
import { LlmCallLogPanel } from './llm/LlmCallLogPanel';
import { MessageAuditPanel } from './MessageAuditPanel';
import { SourceStatusPanel } from './SourceStatusPanel';
import type { BloggerDraft, PositionsByKol, SetDraft, SetNotifySettings, SetSettings } from './sourceTypes';
import { WxPusherSettingsPanel } from './WxPusherSettingsPanel';
import { YouTubePageButton } from './YouTubePageButton';

interface Props {
  settings: WxPusherSettings;
  notifySettings: WxPusherNotifySettings;
  status: WxPusherStatus | null;
  bloggers: WxPusherBlogger[];
  messages: WxPusherMessage[];
  positionsByKol: PositionsByKol;
  draft: BloggerDraft;
  loading: boolean;
  message: string;
  setSettings: SetSettings;
  setNotifySettings: SetNotifySettings;
  setDraft: SetDraft;
  setMessage: (value: string) => void;
  onClose: () => void;
  onRefresh: () => void;
  onSaveSettings: () => void;
  onSaveBlogger: () => void;
  onEditBlogger: (blogger: WxPusherBlogger) => void;
  onRetryMessage: (id: string) => void;
  onAddPosition: (kolId: string, symbol: string) => void;
  onClosePosition: (id: string) => void;
}

export function SourceManagerModal(props: Props) {
  const {
    settings,
    notifySettings,
    status,
    bloggers,
    messages,
    positionsByKol,
    draft,
    loading,
    message,
    setSettings,
    setNotifySettings,
    setDraft,
    setMessage,
    onClose,
    onRefresh,
    onSaveSettings,
    onSaveBlogger,
    onEditBlogger,
    onRetryMessage,
    onAddPosition,
    onClosePosition,
  } = props;

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <section className="entry source-modal" onMouseDown={(event) => event.stopPropagation()}>
        <div className="modal-head">
          <div>
            <div className="panel-title">来源管理</div>
            <p>管理 WxPusher 登录态、博主白名单、当前持仓和最近失败消息。</p>
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
          <SourceStatusPanel status={status} />
          <WxPusherSettingsPanel
            loading={loading}
            notifySettings={notifySettings}
            onSaveSettings={onSaveSettings}
            setNotifySettings={setNotifySettings}
            setSettings={setSettings}
            settings={settings}
          />
        </div>

        <div className="source-layout">
          <BloggerPositionPanel
            bloggers={bloggers}
            draft={draft}
            loading={loading}
            onAddPosition={onAddPosition}
            onClosePosition={onClosePosition}
            onEditBlogger={onEditBlogger}
            onSaveBlogger={onSaveBlogger}
            positionsByKol={positionsByKol}
            setDraft={setDraft}
            setMessage={setMessage}
          />
          <MessageAuditPanel
            loading={loading}
            messages={messages}
            onRetryMessage={onRetryMessage}
          />
        </div>
        <div className="source-panel">
          <div className="panel-title">YouTube 转写</div>
          <p className="muted">已改为独立页面，手机端查看会更顺手。</p>
          <YouTubePageButton />
        </div>

        <LlmCallLogPanel />

        {message ? <div className="form-message">{message}</div> : null}
      </section>
    </div>
  );
}
