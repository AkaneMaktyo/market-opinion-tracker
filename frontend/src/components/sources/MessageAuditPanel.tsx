import type { WxPusherMessage } from '../../types';

interface Props {
  messages: WxPusherMessage[];
  loading: boolean;
  onRetryMessage: (id: string) => void;
}

export function MessageAuditPanel({ messages, loading, onRetryMessage }: Props) {
  return (
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
  );
}

function trimTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '暂无';
}
