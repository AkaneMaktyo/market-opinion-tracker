import { CheckCircle2, CircleDot, XCircle } from 'lucide-react';
import { api } from '../api/client';
import type { OpinionView } from '../types';

interface Props {
  symbol: string;
  opinions: OpinionView[];
  onChanged: () => void;
}

export function OpinionList({ symbol, opinions, onChanged }: Props) {
  const sorted = [...opinions].sort((left, right) =>
    right.opinion.opinionTime.localeCompare(left.opinion.opinionTime),
  );

  async function review(id: string, outcome: string) {
    await api.review(id, { outcome });
    onChanged();
  }

  return (
    <section className="opinions">
      <div className="history-head">
        <div>
          <div className="panel-title">观点与消息</div>
          <h2>{symbol}</h2>
        </div>
        <span>{sorted.length} 条</span>
      </div>
      {sorted.length === 0 && <div className="empty">当前标的还没有观点或消息</div>}
      <div className="timeline">
        {sorted.map(({ opinion, priceLevels, review: result }) => {
          const quote = originalText(opinion.sourceQuote, opinion.rawItemJson);
          const fallback = fallbackOpinion(opinion.rawItemJson);
          const message = opinion.status === 'MESSAGE';
          return (
            <article className="opinion timeline-item" key={opinion.id}>
              <div className="opinion-top">
                <span className={`badge ${opinion.direction.toLowerCase()}`}>
                  {message ? '消息' : directionLabel(opinion.direction)}
                </span>
                <time>{opinion.opinionTime.slice(0, 10)}</time>
              </div>
              {fallback ? (
                <p className="source-quote">原文：{quote || opinion.thesis}</p>
              ) : (
                <>
                  {opinion.rawDirection && <p className="raw-direction">{opinion.rawDirection}</p>}
                  <h3>{opinion.thesis}</h3>
                  <p>周期：{opinion.horizon}</p>
                </>
              )}
              {!fallback && priceLevels.length > 0 ? (
                <div className="levels">
                  {priceLevels.map((level) => (
                    <span key={`${level.levelType}-${level.price}`}>
                      {levelLabel(level.levelType)} {level.price}
                    </span>
                  ))}
                </div>
              ) : null}
              {!fallback && opinion.priceNotesText && <p className="detail-text">{opinion.priceNotesText}</p>}
              {!fallback && opinion.catalystsText && <p className="detail-text">催化：{opinion.catalystsText}</p>}
              {!fallback && opinion.risksText && <p className="detail-text">风险：{opinion.risksText}</p>}
              {!fallback && quote && <p className="source-quote">原文：{quote}</p>}
              {fallback ? null : message ? (
                <div className="review-row"><span>来自 KOL 历史消息</span></div>
              ) : (
                <div className="review-row">
                  <span>{result ? outcomeLabel(result.outcome) : '待复盘'}</span>
                  <button onClick={() => review(opinion.id, 'HIT')} title="标记命中" type="button">
                    <CheckCircle2 size={16} />
                  </button>
                  <button onClick={() => review(opinion.id, 'PARTIAL')} title="标记部分命中" type="button">
                    <CircleDot size={16} />
                  </button>
                  <button onClick={() => review(opinion.id, 'MISS')} title="标记失败" type="button">
                    <XCircle size={16} />
                  </button>
                </div>
              )}
            </article>
          );
        })}
      </div>
    </section>
  );
}

function directionLabel(value: string) {
  return {
    BULLISH: '看多',
    BEARISH: '看空',
    RANGE: '震荡',
    WATCH: '观望',
  }[value] || value;
}

function levelLabel(value: string) {
  return {
    SUPPORT: '支撑',
    RESISTANCE: '压力',
    TARGET: '目标',
    STOP: '止损',
    NOTE: '价位',
  }[value] || value;
}

function outcomeLabel(value: string) {
  return {
    HIT: '命中',
    PARTIAL: '部分命中',
    MISS: '失败',
    PENDING: '未触发',
  }[value] || value;
}

function originalText(sourceQuote?: string, rawItemJson?: string) {
  if (sourceQuote?.trim()) return sourceQuote.trim();
  if (!rawItemJson) return '';
  try {
    const raw = JSON.parse(rawItemJson) as Record<string, unknown>;
    const value = raw['原文摘录'] || raw.sourceQuote || raw.source_quote;
    return typeof value === 'string' ? value.trim() : '';
  } catch {
    return '';
  }
}

function fallbackOpinion(rawItemJson?: string) {
  if (!rawItemJson) return false;
  try {
    const raw = JSON.parse(rawItemJson) as Record<string, unknown>;
    return raw.fallback === 'keyword';
  } catch {
    return false;
  }
}
