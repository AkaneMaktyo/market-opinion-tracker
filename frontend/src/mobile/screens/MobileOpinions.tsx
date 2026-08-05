import { ImageIcon, RefreshCw, Search, X } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../../api/client';
import type { WxPusherRecentMessage } from '../../types';
import { formatDate } from './MobileOverview';

const REFRESH_MS = 30000;
const IMAGE_LINE = /^WXPUSHER_IMAGE_URL=((?:https?:\/\/|data:image\/)[^\r\n]+)$/gm;
const OCR_BLOCK = /\[图片转文字 \d+]\s*([\s\S]*?)\s*\[\/图片转文字]/g;
const IMAGE_HINT = /\.(?:jpe?g|png|gif|webp)(?:\s|$|[?#])/i;
const KOL_PREFIX = /^\[([^\]]+)\]/;

/** 从 bloggerName 中提取简洁的显示名称。如 "[🟢｜颜驰] 颜驰Bit..." -> "[🟢｜颜驰]" */
function displayKolName(name: string): string {
  const match = name.match(KOL_PREFIX);
  if (match) return `[${match[1]}]`;
  // 如果名称超过30字符，截断
  if (name.length > 30) return name.slice(0, 30) + '\u2026';
  return name;
}

export function MobileOpinions() {
  const [query, setQuery] = useState('');
  const [kolName, setKolName] = useState('');
  const [messages, setMessages] = useState<WxPusherRecentMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [preview, setPreview] = useState('');
  const requestId = useRef(0);
  const detailCache = useRef(new Map<string, WxPusherRecentMessage>());
  const kolNames = useMemo(() => [...new Set(messages.map((item) => item.bloggerName).filter(Boolean))]
    .sort((left, right) => left.localeCompare(right, 'zh-CN')), [messages]);
  const items = useMemo(() => filterMessages(messages, query, kolName), [messages, query, kolName]);

  async function load(silent = false) {
    const current = ++requestId.current;
    if (!silent) setLoading(true);
    try {
      const next = await api.wxpusherRecentMessages(50);
      if (current !== requestId.current) return;
      const cached = next.map((item) => detailCache.current.get(item.id) || item);
      setMessages(cached);
      setError('');
      void hydrateImages(cached, current);
    } catch (loadError) {
      if (current !== requestId.current) return;
      setError(loadError instanceof Error ? loadError.message : '读取最新消息失败');
    } finally {
      if (current === requestId.current) setLoading(false);
    }
  }

  async function hydrateImages(items: WxPusherRecentMessage[], current: number) {
    const targets = items.filter((item) => needsImageDetail(item) && !detailCache.current.has(item.id));
    if (targets.length === 0) return;
    await Promise.all(targets.map(async (item) => {
      try {
        detailCache.current.set(item.id, await api.wxpusherRecentMessageDetail(item.id));
      } catch {
        detailCache.current.set(item.id, item);
      }
    }));
    if (current !== requestId.current) return;
    setMessages((currentItems) => currentItems.map((item) => detailCache.current.get(item.id) || item));
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
  }, []);

  return (
    <div className="mobile-screen-content mobile-opinions-screen">
      <div className="mobile-opinion-toolbar">
        <label className="mobile-search-box">
          <Search aria-hidden="true" size={18} />
          <input onChange={(event) => setQuery(event.target.value)} placeholder="搜索消息内容" type="search" value={query} />
        </label>
        <select aria-label="选择 KOL" onChange={(event) => setKolName(event.target.value)} value={kolName}>
          <option value="">全部 KOL</option>
          {kolNames.map((name) => <option key={name} value={name}>{displayKolName(name)}</option>)}
        </select>
      </div>

      <div className="mobile-list-title">
        <div><strong>最新接收消息</strong><small>{kolName || '全部 KOL'}</small></div>
        <button aria-label="刷新最新消息" className={loading ? 'spinning' : ''} onClick={() => void load()} type="button"><RefreshCw size={17} /></button>
      </div>
      {error ? <div className="mobile-card mobile-message-error">{error}</div> : null}
      <section className="mobile-opinion-feed" aria-busy={loading}>
        {loading && messages.length === 0 ? <div className="mobile-card mobile-empty">正在读取最新消息…</div> : null}
        {!loading && items.length === 0 ? <div className="mobile-card mobile-empty">暂时没有符合条件的消息</div> : null}
        {items.map((item) => <MessageCard item={item} key={item.id} onPreview={setPreview} />)}
      </section>
      {preview ? <ImagePreview onClose={() => setPreview('')} source={preview} /> : null}
    </div>
  );
}

function MessageCard({ item, onPreview }: { item: WxPusherRecentMessage; onPreview: (source: string) => void }) {
  const parsed = parseMessage(item);
  return (
    <article className="mobile-card mobile-feed-card mobile-message-card">
      <div className="mobile-feed-top">
        <span className="mobile-avatar">{displayKolName(item.bloggerName).slice(0, 1) || '讯'}</span>
        <div><strong>{displayKolName(item.bloggerName) || '未知 KOL'}</strong><small>{formatDate(item.messageTime)}</small></div>
        <b className={`mobile-message-status mobile-message-status-${statusTone(item.status)}`}>{statusLabel(item.status)}</b>
      </div>
      <p className="mobile-feed-thesis">{parsed.body}</p>
      {parsed.images.length > 0 ? (
        <div className={`mobile-message-images count-${Math.min(parsed.images.length, 3)}`}>
          {parsed.images.map((source, index) => (
            <button aria-label={`放大第 ${index + 1} 张图片`} key={`${source.slice(0, 80)}-${index}`} onClick={() => onPreview(source)} type="button">
              <img alt={`消息图片 ${index + 1}`} decoding="async" referrerPolicy="no-referrer" src={source} />
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
  const [zoomed, setZoomed] = useState(false);
  return (
    <div className={'modal-backdrop mobile-image-backdrop' + (zoomed ? ' mobile-image-zoomed-container' : '')} data-mobile-overlay onMouseDown={onClose}>
      <button aria-label="关闭图片" className="mobile-image-close" onClick={onClose} type="button"><X size={22} /></button>
      <img
        alt="放大的消息图片"
        className={zoomed ? 'mobile-image-zoomed' : ''}
        onClick={() => setZoomed(prev => !prev)}
        onMouseDown={(event) => event.stopPropagation()}
        src={source}
      />
    </div>
  );
}

function parseMessage(item: WxPusherRecentMessage) {
  const detail = item.detailText?.trim() || '';
  const images = [...detail.matchAll(IMAGE_LINE)].map((match) => match[1].trim());
  const ocrParts = [...detail.matchAll(OCR_BLOCK)]
    .map((match) => match[1].trim().replace(/\n{3,}/g, '\n\n'))
    .filter(Boolean);
  const ocrText = [...new Set(ocrParts)].join('\n\n');
  const body = detail
    .replace(IMAGE_LINE, '')
    .replace(OCR_BLOCK, '')
    .replace(/^\[图片]$/gm, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
  return {
    body: body || ocrText || item.summary || item.title || '这条消息没有文字内容',
    images: [...new Set(images)],
    ocrText,
  };
}

function filterMessages(items: WxPusherRecentMessage[], query: string, kolName: string) {
  const keyword = query.trim().toLowerCase();
  return [...items]
    .filter((item) => !kolName || item.bloggerName === kolName)
    .filter((item) => !keyword || [item.bloggerName, item.title, item.summary, item.detailText]
      .some((value) => value?.toLowerCase().includes(keyword)))
    .sort((left, right) => Date.parse(right.messageTime) - Date.parse(left.messageTime));
}

function needsImageDetail(item: WxPusherRecentMessage) {
  return parseMessage(item).images.length === 0
    && IMAGE_HINT.test(`${item.summary || ''}\n${item.detailText || ''}`);
}

function statusLabel(status: string) {
  return ({ IMPORTED: '已提取', PROCESSING: '处理中', PENDING: '待处理', FAILED: '仅消息', SKIPPED: '仅消息' } as Record<string, string>)[status] || '已接收';
}

function statusTone(status: string) {
  if (status === 'IMPORTED') return 'ready';
  if (status === 'PROCESSING' || status === 'PENDING') return 'working';
  return 'plain';
}
