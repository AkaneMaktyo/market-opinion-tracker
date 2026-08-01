import type { WxPusherMessage } from '../../types';

interface Props {
  messages: WxPusherMessage[];
  loading: boolean;
  onRetryMessage: (id: string) => void;
}

export function MessageAuditPanel({ messages, loading, onRetryMessage }: Props) {
  return (
    <section className="source-panel">
      <div className="panel-title">最近图片转文字</div>
      <div className="message-audit">
        {messages.map((item) => (
          <div className="message-row" key={item.id}>
            <div className="message-row-content">
              <strong>{item.bloggerName}</strong>
              <p>{item.summary || item.title || '无标题消息'}</p>
              <div className="ocr-text">
                <span>识别文字</span>
                <pre>{extractOcrText(item.detailText)}</pre>
              </div>
              <p className="muted">
                {trimTime(item.messageTime)} · {statusLabel(item.status)}
                {item.errorMessage ? ` · ${item.errorMessage}` : ''}
              </p>
            </div>
            {item.status !== 'IMPORTED' ? (
              <button
                className="primary secondary"
                disabled={loading}
                onClick={() => onRetryMessage(item.id)}
                type="button"
              >
                重试观点提取
              </button>
            ) : null}
          </div>
        ))}
        {messages.length === 0 ? <p className="muted">最近还没有图片转文字记录。</p> : null}
      </div>
    </section>
  );
}

function extractOcrText(value?: string) {
  if (!value) return '未识别到文字';
  const blocks = [...value.matchAll(/\[图片转文字 \d+]\s*([\s\S]*?)\s*\[\/图片转文字]/g)];
  return blocks.length > 0
    ? blocks.map((match) => match[1].trim()).filter(Boolean).join('\n\n')
    : value;
}

function statusLabel(value: string) {
  if (value === 'IMPORTED') return '观点已入库';
  if (value === 'FAILED') return '文字已识别，观点提取失败';
  return value;
}

function trimTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '暂无';
}
