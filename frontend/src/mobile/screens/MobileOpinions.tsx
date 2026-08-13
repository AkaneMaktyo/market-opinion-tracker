import { BellRing, ImageIcon, RefreshCw, Search, X, Info } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../../api/client';
import type { WxPusherRecentMessage } from '../../types';
import type { PriceAlertRecognitionResult } from '../../types/alerts';
import { PriceAlertRecognitionSheet } from '../recognition/PriceAlertRecognitionSheet';
import { formatDate } from './MobileOverview';

const REFRESH_MS = 30000;
const IMAGE_LINE = /^WXPUSHER_IMAGE_URL=((?:https?:\/\/|data:image\/)[^\r\n]+)$/gm;
const OCR_BLOCK = /\[图片转文字 \d+]\s*([\s\S]*?)\s*\[\/图片转文字]/g;
const IMAGE_HINT = /\.(?:jpe?g|png|gif|webp)(?:\s|$|[?#])/i;
const KOL_PREFIX = /^\[([^\]]+)\]/;

/** 从 bloggerName 中提取简洁的显示名称。
 *  优先级：1) 匹配 "[前缀]"  2) 无前缀时在 :：\u3000「 处截断  3) 超过30字符强制截断 */
function displayKolName(name: string): string {
  if (!name) return '';
  const match = name.match(KOL_PREFIX);
  if (match) return `[${match[1]}]`;
  // 在常见分隔符处截断
  const cutAt = name.search(/[：:「\u3000]/);
  const cleaned = cutAt > 0 ? name.slice(0, cutAt).trim() : name;
  if (cleaned.length > 30) return cleaned.slice(0, 30) + '\u2026';
  return cleaned;
}

/** 简洁格式化部署时间 */
function fmtDeployTime(iso: string): string {
  try {
    const d = new Date(iso);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
  } catch { return iso; }
}

interface Props {
  focusMessageId?: string;
  focusRequestKey?: number;
  kolId: string;
  onClearFocus: () => void;
  onWatchlistChanged: () => void;
}

export function MobileOpinions({ focusMessageId = '', focusRequestKey = 0, kolId, onClearFocus, onWatchlistChanged }: Props) {
  const [query, setQuery] = useState('');
  const [kolName, setKolName] = useState('');
  const [messages, setMessages] = useState<WxPusherRecentMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [preview, setPreview] = useState('');
  const [activeRecognition, setActiveRecognition] = useState<PriceAlertRecognitionResult | null>(null);
  const [focusedMessage, setFocusedMessage] = useState<WxPusherRecentMessage | null>(null);
  const [focusLoading, setFocusLoading] = useState(false);
  const [recognizingIds, setRecognizingIds] = useState<Set<string>>(new Set());
  const [recognitionErrors, setRecognitionErrors] = useState<Record<string, string>>({});
  const requestId = useRef(0);
  const detailCache = useRef(new Map<string, WxPusherRecentMessage>());
  const handledFocus = useRef('');
  const [hydratingIds, setHydratingIds] = useState<Set<string>>(new Set());
  const [deployInfo, setDeployInfo] = useState<{ bundleId: string; createdAt: string } | null>(null);
  const kolNames = useMemo(() => [...new Set(messages.map((item) => item.bloggerName).filter(Boolean))]
    .sort((left, right) => left.localeCompare(right, 'zh-CN')), [messages]);
  const items = useMemo(() => focusMessageId
    ? focusedMessage ? [focusedMessage] : []
    : filterMessages(messages, query, kolName), [focusMessageId, focusedMessage, messages, query, kolName]);

  const fetchDeployInfo = useCallback(() => {
    const env = (import.meta as unknown as { env?: Record<string, string> }).env;
    const manifestUrl = env?.VITE_LIVE_UPDATE_MANIFEST_URL;
    if (!manifestUrl) return;
    fetch(`${manifestUrl}?t=${Date.now()}`, { cache: 'no-store' })
      .then((r) => r.ok ? r.json() : null)
      .then((data) => {
        if (data?.bundleId && data?.createdAt) {
          setDeployInfo({ bundleId: String(data.bundleId), createdAt: String(data.createdAt) });
        }
      })
      .catch(() => {});
  }, []);

  async function load(silent = false) {
    const current = ++requestId.current;
    if (!silent) setLoading(true);
    try {
      const next = await api.wxpusherRecentMessages(50);
      if (current !== requestId.current) return;
      const cached = next.map((item) => detailCache.current.get(item.id) || item);
      setMessages(cached);
      setError('');
      void hydrateDetails(cached, current);
    } catch (loadError) {
      if (current !== requestId.current) return;
      setError(loadError instanceof Error ? loadError.message : '读取最新消息失败');
    } finally {
      if (current === requestId.current) setLoading(false);
    }
  }

  async function hydrateDetails(items: WxPusherRecentMessage[], current: number) {
    const targets = items.filter((item) => needsDetail(item) && !detailCache.current.has(item.id));
    if (targets.length === 0) return;
    setHydratingIds(new Set(targets.map((t) => t.id)));
    const BATCH = 3;
    for (let i = 0; i < targets.length; i += BATCH) {
      if (current !== requestId.current) return;
      const batch = targets.slice(i, i + BATCH);
      await Promise.all(batch.map(async (item) => {
        try {
          detailCache.current.set(item.id, await api.wxpusherRecentMessageDetail(item.id));
        } catch {
          detailCache.current.set(item.id, item);
        }
      }));
      if (current !== requestId.current) return;
      setMessages((currentItems) => currentItems.map((item) => detailCache.current.get(item.id) || item));
      setHydratingIds((prev) => {
        const next = new Set(prev);
        batch.forEach((b) => next.delete(b.id));
        return next;
      });
    }
  }

  async function recognizePriceAlerts(item: WxPusherRecentMessage) {
    setRecognizingIds((current) => new Set(current).add(item.id));
    setRecognitionErrors((current) => ({ ...current, [item.id]: '' }));
    try {
      const result = await api.recognizeWxPusherPriceAlerts(item.id, kolId);
      const patch: Partial<WxPusherRecentMessage> = {
        priceAlertRecognitionStatus: result.status,
        priceAlertRecognitionId: result.recognitionId,
        priceAlertCandidateCount: result.candidates.length,
      };
      setMessages((current) => current.map((message) => message.id === item.id ? { ...message, ...patch } : message));
      const cached = detailCache.current.get(item.id);
      if (cached) detailCache.current.set(item.id, { ...cached, ...patch });
      if (result.status === 'SUCCESS' && result.candidates.length > 0) setActiveRecognition(result);
      if (result.status === 'SUCCESS' && result.candidates.length > 0) onWatchlistChanged();
      if (result.status === 'FAILED') {
        setRecognitionErrors((current) => ({ ...current, [item.id]: result.errorMessage || '智能识别失败，请重试' }));
      }
    } catch (recognitionError) {
      const message = recognitionError instanceof Error ? recognitionError.message : '智能识别失败，请重试';
      setMessages((current) => current.map((messageItem) => messageItem.id === item.id
        ? { ...messageItem, priceAlertRecognitionStatus: 'FAILED' }
        : messageItem));
      setRecognitionErrors((current) => ({ ...current, [item.id]: message }));
    } finally {
      setRecognizingIds((current) => {
        const next = new Set(current);
        next.delete(item.id);
        return next;
      });
    }
  }

  useEffect(() => {
    void load();
    fetchDeployInfo();
    const timer = window.setInterval(() => {
      if (!document.hidden) void load(true);
    }, REFRESH_MS);
    return () => {
      requestId.current += 1;
      window.clearInterval(timer);
    };
  }, [fetchDeployInfo]);

  useEffect(() => {
    if (!focusMessageId) {
      setFocusedMessage(null);
      setFocusLoading(false);
      return;
    }
    let disposed = false;
    handledFocus.current = '';
    setFocusedMessage(null);
    setFocusLoading(true);
    setQuery('');
    setKolName('');
    void api.wxpusherRecentMessageDetail(focusMessageId).then((item) => {
      if (disposed) return;
      detailCache.current.set(item.id, item);
      setFocusedMessage(item);
      setFocusLoading(false);
      setError('');
    }).catch(() => {
      if (!disposed) {
        setFocusLoading(false);
        setError('未能读取该价格提醒对应的原始消息');
      }
    });
    return () => { disposed = true; };
  }, [focusMessageId, focusRequestKey]);

  useEffect(() => {
    if (!focusMessageId || handledFocus.current === focusMessageId) return;
    const target = document.getElementById(`mobile-msg-${focusMessageId}`);
    if (!target) return;
    handledFocus.current = focusMessageId;
    target.scrollIntoView({ block: 'center', behavior: 'smooth' });
    target.classList.add('mobile-msg-highlight');
    window.setTimeout(() => target.classList.remove('mobile-msg-highlight'), 2500);
  }, [focusMessageId, items]);

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
        <div><strong>{focusMessageId ? '价格提醒来源消息' : '最新接收消息'}</strong><small>{focusMessageId ? '创建当前提醒时智能识别的原文' : kolName || '全部 KOL'}</small></div>
        {focusMessageId
          ? <button aria-label="返回全部消息" onClick={onClearFocus} type="button"><X size={17} /></button>
          : <button aria-label="刷新最新消息" className={loading ? 'spinning' : ''} onClick={() => void load()} type="button"><RefreshCw size={17} /></button>}
      </div>
      {error ? <div className="mobile-card mobile-message-error">{error}</div> : null}
      <section className="mobile-opinion-feed" aria-busy={loading}>
        {(focusLoading || loading && messages.length === 0) ? <div className="mobile-card mobile-empty">{focusMessageId ? '正在读取价格提醒来源消息…' : '正在读取最新消息…'}</div> : null}
        {!focusLoading && !loading && items.length === 0 ? <div className="mobile-card mobile-empty">暂时没有符合条件的消息</div> : null}
        {items.map((item) => (
          <MessageCard
            error={recognitionErrors[item.id]}
            hydrating={hydratingIds.has(item.id)}
            item={item}
            key={item.id}
            onPreview={setPreview}
            onRecognize={() => void recognizePriceAlerts(item)}
            recognizing={recognizingIds.has(item.id)}
          />
        ))}
      </section>
      {preview ? <ImagePreview onClose={() => setPreview('')} source={preview} /> : null}
      {activeRecognition ? <PriceAlertRecognitionSheet kolId={kolId} onClose={() => setActiveRecognition(null)} onWatchlistChanged={onWatchlistChanged} result={activeRecognition} /> : null}
      {deployInfo ? (
        <div className="mobile-card mobile-deploy-info">
          <Info size={14} />
          <span>版本 <b>{deployInfo.bundleId.slice(0, 12)}</b></span>
          <span>部署 {fmtDeployTime(deployInfo.createdAt)}</span>
        </div>
      ) : null}
    </div>
  );
}

function MessageCard({ item, onPreview, onRecognize, hydrating, recognizing, error }: {
  item: WxPusherRecentMessage;
  onPreview: (source: string) => void;
  onRecognize: () => void;
  hydrating: boolean;
  recognizing: boolean;
  error?: string;
}) {
  const parsed = parseMessage(item);
  return (
    <article className="mobile-card mobile-feed-card mobile-message-card" id={`mobile-msg-${item.id}`}>
      <div className="mobile-feed-top">
        <span className="mobile-avatar">{displayKolName(item.bloggerName).slice(0, 1) || '讯'}</span>
        <div><strong>{displayKolName(item.bloggerName) || '未知 KOL'}</strong><small>{formatDate(item.messageTime)}</small></div>
        <b className={`mobile-message-status mobile-message-status-${statusTone(item.status)}`}>{statusLabel(item.status)}</b>
      </div>
      <p className="mobile-feed-thesis">{parsed.body}</p>
      {hydrating ? <div className="mobile-feed-hint"><RefreshCw size={12} className="spinning" /> 正在获取完整内容…</div> : null}
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
      <div className="mobile-recognition-action">
        <button
          disabled={recognizing || item.priceAlertRecognitionStatus === 'EMPTY'}
          onClick={onRecognize}
          type="button"
        >
          {recognizing ? <RefreshCw className="spinning" size={15} /> : <BellRing size={15} />}
          {recognitionButtonLabel(item, recognizing)}
        </button>
        {error ? <small>{error}</small> : null}
      </div>
    </article>
  );
}

function recognitionButtonLabel(item: WxPusherRecentMessage, recognizing: boolean) {
  if (recognizing) return '识别中…';
  if (item.priceAlertRecognitionStatus === 'SUCCESS') return `添加价格提醒 ${item.priceAlertCandidateCount || ''}`.trim();
  if (item.priceAlertRecognitionStatus === 'EMPTY') return '未发现价格提醒';
  if (item.priceAlertRecognitionStatus === 'FAILED') return '重新识别';
  if (item.priceAlertRecognitionStatus === 'PROCESSING') return '继续查看识别';
  return '智能识别';
}

function ImagePreview({ source, onClose }: { source: string; onClose: () => void }) {
  const [scale, setScale] = useState(1);
  const [tx, setTx] = useState(0);
  const [ty, setTy] = useState(0);
  const touchRef = useRef<{ mode: 'none' | 'pan' | 'pinch'; startX: number; startY: number; startTx: number; startTy: number; startDist: number; startScale: number; lastTap: number }>({
    mode: 'none', startX: 0, startY: 0, startTx: 0, startTy: 0, startDist: 0, startScale: 1, lastTap: 0,
  });

  const handleBackdrop = (e: React.MouseEvent) => {
    if ((e.target as HTMLElement).closest('.mobile-image-pinched')) return;
    onClose();
  };

  const handleTouchStart = (e: React.TouchEvent) => {
    if (e.touches.length === 1) {
      const now = Date.now();
      const t = touchRef.current;
      // 双击切换缩放
      if (now - t.lastTap < 300) {
        if (scale > 1) { setScale(1); setTx(0); setTy(0); }
        else { setScale(2.5); }
        t.lastTap = 0;
        return;
      }
      t.lastTap = now;
      t.mode = 'pan';
      t.startX = e.touches[0].clientX;
      t.startY = e.touches[0].clientY;
      t.startTx = tx;
      t.startTy = ty;
    } else if (e.touches.length === 2) {
      const t = touchRef.current;
      t.mode = 'pinch';
      const dx = e.touches[0].clientX - e.touches[1].clientX;
      const dy = e.touches[0].clientY - e.touches[1].clientY;
      t.startDist = Math.hypot(dx, dy);
      t.startScale = scale;
    }
  };

  const handleTouchMove = (e: React.TouchEvent) => {
    e.preventDefault();
    const t = touchRef.current;
    if (t.mode === 'pan' && e.touches.length === 1) {
      setTx(t.startTx + (e.touches[0].clientX - t.startX));
      setTy(t.startTy + (e.touches[0].clientY - t.startY));
    } else if (t.mode === 'pinch' && e.touches.length === 2) {
      const dx = e.touches[0].clientX - e.touches[1].clientX;
      const dy = e.touches[0].clientY - e.touches[1].clientY;
      const dist = Math.hypot(dx, dy);
      const next = Math.max(1, Math.min(5, t.startScale * (dist / t.startDist)));
      setScale(next);
      if (next === 1) { setTx(0); setTy(0); }
    }
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    if (e.touches.length === 0) touchRef.current.mode = 'none';
  };

  return (
    <div className="modal-backdrop mobile-image-backdrop" onClick={handleBackdrop}>
      <button aria-label="关闭图片" className="mobile-image-close" onClick={(e) => { e.stopPropagation(); onClose(); }} type="button"><X size={22} /></button>
      <div className="mobile-image-stage">
        <img
          alt="放大的消息图片"
          className="mobile-image-pinched"
          onTouchEnd={handleTouchEnd}
          onTouchMove={handleTouchMove}
          onTouchStart={handleTouchStart}
          src={source}
          style={{ transform: `translate(${tx}px, ${ty}px) scale(${scale})` }}
        />
      </div>
    </div>
  );
}

/** 清理消息正文里常见的 KOL 自我介绍前缀。
 *  例如：[🟢｜颜驰] 颜驰Bit「主动联系都是诈骗」 */
function cleanSelfIntro(body: string): string {
  const lines = body.split('\n');
  if (lines.length === 0) return body;
  const first = lines[0].trim();
  // 匹配 [前缀｜名字] 名字...  或 [名字] 名字...  的自我介绍行
  if (/^\[[^\]]+[｜|\|][^\]]+\]\s*\S+/.test(first) || /^\[[^\]]+\]\s*\S+/.test(first)) {
    lines.shift();
    return lines.join('\n').trim();
  }
  return body;
}

function parseMessage(item: WxPusherRecentMessage) {
  const detail = item.detailText?.trim() || '';
  const images = [...detail.matchAll(IMAGE_LINE)].map((match) => match[1].trim());
  const ocrParts = [...detail.matchAll(OCR_BLOCK)]
    .map((match) => match[1].trim().replace(/\n{3,}/g, '\n\n'))
    .filter(Boolean);
  const ocrText = [...new Set(ocrParts)].join('\n\n');
  const body = cleanSelfIntro(
    detail
      .replace(IMAGE_LINE, '')
      .replace(OCR_BLOCK, '')
      .replace(/^\[图片]$/gm, '')
      .replace(/\n{3,}/g, '\n\n')
      .trim()
  );
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

function needsDetail(item: WxPusherRecentMessage) {
  const detail = (item.detailText || '').trim();
  const summary = (item.summary || '').trim();
  // detailText 为空，或与 summary 相同（说明 detailText 没抓到，回退到了截断的 summary）
  if (!detail || detail === summary) return true;
  // 消息提到图片但没有图片链接，也需要拉取 detail
  return parseMessage(item).images.length === 0
    && IMAGE_HINT.test(`${summary}\n${detail}`);
}

function statusLabel(status: string) {
  return ({ IMPORTED: '已提取', PROCESSING: '处理中', PENDING: '待处理', FAILED: '仅消息', SKIPPED: '仅消息' } as Record<string, string>)[status] || '已接收';
}

function statusTone(status: string) {
  if (status === 'IMPORTED') return 'ready';
  if (status === 'PROCESSING' || status === 'PENDING') return 'working';
  return 'plain';
}
