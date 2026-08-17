import { RefreshCw, Search, X } from 'lucide-react';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { WxPusherRecentMessage } from '../../types';
import { InstrumentLogo } from '../../components/instruments/InstrumentLogo';

const OCR_BLOCK = /\[图片转文字 \d+]\s*([\s\S]*?)\s*\[\/图片转文字]/g;
const IMAGE_LINE = /^WXPUSHER_IMAGE_URL=((?:https?:\/\/|data:image\/)[^\r\n]+)$/gm;

/** 持仓标的 → 最近观点搜索弹层：展示一个月内命中标题/正文/图片识别文字的消息。 */
export function PositionOpinionSheet({
  keyword,
  logoUrl,
  onClose,
  sourceKind,
}: {
  keyword: string;
  logoUrl?: string;
  onClose: () => void;
  sourceKind: 'stock' | 'crypto';
}) {
  const [items, setItems] = useState<WxPusherRecentMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    setLoading(true);
    void api.wxpusherSearchMessages(keyword, 31, 50).then((result) => {
      if (!active) return;
      setItems(result);
      setError('');
    }).catch((searchError) => {
      if (!active) return;
      setError(searchError instanceof Error ? searchError.message : '搜索失败');
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [keyword]);

  return (
    <div className="mobile-trade-backdrop mobile-opinion-sheet-backdrop" onClick={onClose} role="presentation">
      <section className="mobile-trade-sheet mobile-opinion-sheet" onClick={(event) => event.stopPropagation()} role="dialog" aria-label={`${keyword} 最近观点`}>
        <header className="mobile-opinion-sheet-head">
          <InstrumentLogo logoUrl={logoUrl} size={34} sourceKind={sourceKind} symbol={keyword} />
          <div>
            <strong>{keyword} · 最近观点</strong>
            <small>近一个月内命中标题或正文（含图片识别文字）的消息</small>
          </div>
          <button aria-label="关闭" onClick={onClose} type="button"><X size={18} /></button>
        </header>

        {loading ? <p className="mobile-opinion-sheet-status"><RefreshCw className="spinning" size={15} /> 正在搜索…</p> : null}
        {!loading && error ? <p className="mobile-opinion-sheet-status error">{error}</p> : null}
        {!loading && !error && items.length === 0 ? (
          <p className="mobile-opinion-sheet-status">一个月内没有提到「{keyword}」的消息</p>
        ) : null}

        <div className="mobile-opinion-sheet-list">
          {items.map((item) => <OpinionHitRow item={item} keyword={keyword} key={item.id} />)}
        </div>
      </section>
    </div>
  );
}

function OpinionHitRow({ item, keyword }: { item: WxPusherRecentMessage; keyword: string }) {
  const { body, hitInOcr } = extract(item, keyword);
  return (
    <article className="mobile-opinion-hit">
      <div className="mobile-opinion-hit-top">
        <strong>{item.bloggerName || '未知 KOL'}</strong>
        <small>{new Date(item.messageTime).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })}</small>
      </div>
      {item.title ? <p className="mobile-opinion-hit-title"><Search size={11} />{item.title}</p> : null}
      <p className="mobile-opinion-hit-body">{body}</p>
      {hitInOcr ? <p className="mobile-opinion-hit-ocr">命中图片识别文字</p> : null}
    </article>
  );
}

function extract(item: WxPusherRecentMessage, keyword: string) {
  const detail = item.detailText || '';
  const images = [...detail.matchAll(IMAGE_LINE)].map((match) => match[1]);
  const ocrParts = [...detail.matchAll(OCR_BLOCK)].map((match) => match[1].trim()).filter(Boolean);
  const lowerKeyword = keyword.toLowerCase();
  const hitInOcr = ocrParts.some((part) => part.toLowerCase().includes(lowerKeyword))
    || (item.title || '').toLowerCase().includes(lowerKeyword);
  const body = detail
    .replace(IMAGE_LINE, '')
    .replace(OCR_BLOCK, '')
    .replace(/^\[图片]$/gm, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim() || ocrParts.join('\n') || item.summary || item.title || '';
  return {
    body: images.length > 0 ? `${body}\n[图片 x${images.length}]` : body,
    hitInOcr,
  };
}
