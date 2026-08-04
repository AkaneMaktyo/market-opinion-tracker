import { BellPlus, ImageIcon, RefreshCw, Search, X } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../../api/client';
import type { WxPusherMessage } from '../../types';
import { formatDate } from './MobileOverview';
import type { DashboardModel } from './mobileTypes';

interface Props {
  dashboard: DashboardModel;
  onQuickAdd: () => void;
}

const REFRESH_MS = 30000;
const IMAGE_LINE = /^WXPUSHER_IMAGE_URL=((?:https?:\/\/|data:image\/)[^\r\n]+)$/gm;
const OCR_BLOCK = /\[图片转文字 \d+]\s*([\s\S]*?)\s*\[\/图片转文字]/g;

export function MobileOpinions({ dashboard, onQuickAdd }: Props) {
  const [query, setQuery] = useState('');
  const [kolId, setKolId] = useState('');
  const [messages, setMessages] = useState<WxPusherMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [preview, setPreview] = useState('');
  const requestId = useRef(0);
  const items = useMemo(() => filterMessages(messages, query), [messages, query]);

  async function load(silent = false) {
    const current = ++requestId.current;
    if (!silent) setLoading(true);
    try {
      const next = await api.wxpusherMessages('', kolId, 50);
      if (current !== requestId.current) return;
      setMessages(next);
      setError('');
    } catch (loadError) {
      if (current !== requestId.current) return;
      setError(loadError instanceof Error ? loadError.message : '读取最新消息失败');
    } finally {
      if (current === requestId.current) setLoading(false);
    }
  }

  useEffect(() => {
    void load();
    const timer = window.setInterval(() => {
      if (!document.hidden) void load(true);
    }, REFRESH_MS);
    return () => {
      requestId.current += 1;
      window.clearInterval(timer);
    };
  }, [kolId]);

  return (
    <div className="mobile-screen-content mobile-opinions-screen">
      <div className="mobile-opinion-toolbar">
        <label className="mobile-search-box">
          <Search aria-hidden="true" size={18} />
          <input onChange={(event) => setQuery(event.target.value)} placeholder="搜索消息内容" type="search" value={query} />
        </label>
        <select aria-label="选择 KOL" onChange={(event) => setKolId(event.target.value)} value={kolId}>
          <option value="">全部 KOL</option>
          {dashboard.kols.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
        </select>
      </div>

      <div className="mobile-list-title">
        <div><strong>最新接收消息</strong><small>{kolName(dashboard, kolId)}</small></div>
        <button aria-label="刷新最新消息" className={loading ? 'spinning' : ''} onClick={() => void load()} type="button"><RefreshCw size={17} /></button>
      </div>
      {error ? <div className="mobile-card mobile-message-error">{error}</div> : null}
      <section className="mobile-opinion-feed" aria-busy={loading}>
        {loading && messages.length === 0 ? <div className="mobile-card mobile-empty">正在读取最新消息…</div> : null}
        {!loading && items.length === 0 ? <div className="mobile-card mobile-empty">暂时没有符合条件的消息</div> : null}
        {items.map((item) => <MessageCard item={item} key={item.id} onPreview={setPreview} />)}
      </section>
      <button className="mobile-floating-action" onClick={onQuickAdd} type="button"><BellPlus size={19} />记录新观点</button>
      {preview ? <ImagePreview onClose={() => setPreview('')} source={preview} /> : null}
    </div>
  );
}

function MessageCard({ item, onPreview }: { item: WxPusherMessage; onPreview: (source: string) => void }) {
  const parsed = parseMessage(item);
  return (
    <article className="mobile-card mobile-feed-card mobile-message-card">
      <div className="mobile-feed-top">
        <span className="mobile-avatar">{item.bloggerName.slice(0, 1) || '讯'}</span>
        <div><strong>{item.bloggerName || '未知 KOL'}</strong><small>{formatDate(item.messageTime)}</small></div>
        <b className={`mobile-message-status mobile-message-status-${statusTone(item.status)}`}>{statusLabel(item.status)}</b>
      </div>
      <p className="mobile-feed-thesis">{parsed.body}</p>
      {parsed.images.length > 0 ? (
        <div className={`mobile-message-images count-${Math.min(parsed.images.length, 3)}`}>
          {parsed.images.map((source, index) => (
            <button aria-label={`放大第 ${index + 1} 张图片`} key={`${source.slice(0, 80)}-${index}`} onClick={() => onPreview(source)} type="button">
              <img alt={`消息图片 ${index + 1}`} decoding="async" loading="lazy" src={source} />
              <span><ImageIcon size={15} />点击放大</span>
            </button>
          ))}
        </div>
      ) : null}
      {parsed.ocrText ? <details><summary>查看图片识别文字</summary><p>{parsed.ocrText}</p></details> : null}
    </article>
  );
}

function ImagePreview({ source, onClose }: { source: string; onClose: () => void }) {
  return (
    <div className="modal-backdrop mobile-image-backdrop" data-mobile-overlay onMouseDown={onClose}>
      <button aria-label="关闭图片" className="mobile-image-close" onClick={onClose} type="button"><X size={22} /></button>
      <img alt="放大的消息图片" onMouseDown={(event) => event.stopPropagation()} src={source} />
    </div>
  );
}

function parseMessage(item: WxPusherMessage) {
  const detail = item.detailText?.trim() || '';
  const images = [...detail.matchAll(IMAGE_LINE)].map((match) => match[1].trim());
  const ocrText = [...detail.matchAll(OCR_BLOCK)].map((match) => match[1].trim()).filter(Boolean).join('\n\n');
  const body = detail
    .replace(IMAGE_LINE, '')
    .replace(OCR_BLOCK, '')
    .replace(/^\[图片]$/gm, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
  return {
    body: body || item.summary || item.title || '这条消息没有文字内容',
    images: [...new Set(images)],
    ocrText,
  };
}

function filterMessages(items: WxPusherMessage[], query: string) {
  const keyword = query.trim().toLowerCase();
  return [...items]
    .filter((item) => !keyword || [item.bloggerName, item.title, item.summary, item.detailText]
      .some((value) => value?.toLowerCase().includes(keyword)))
    .sort((left, right) => right.messageTime.localeCompare(left.messageTime));
}

function kolName(dashboard: DashboardModel, kolId: string) {
  if (!kolId) return '全部 KOL';
  return dashboard.kols.find((item) => item.id === kolId)?.name || '所选 KOL';
}

function statusLabel(status: string) {
  return ({ IMPORTED: '已提取', PROCESSING: '处理中', PENDING: '待处理', FAILED: '仅消息', SKIPPED: '仅消息' } as Record<string, string>)[status] || '已接收';
}

function statusTone(status: string) {
  if (status === 'IMPORTED') return 'ready';
  if (status === 'PROCESSING' || status === 'PENDING') return 'working';
  return 'plain';
}
