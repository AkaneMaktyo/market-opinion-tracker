import type { OpinionView } from '../types';
import { cleanRepeatedQuote, ExpandableQuote } from './opinions/ExpandableQuote';

interface Props {
  symbol: string;
  opinions: OpinionView[];
}

export function OpinionList({ symbol, opinions }: Props) {
  const sorted = [...opinions].sort((left, right) =>
    right.opinion.opinionTime.localeCompare(left.opinion.opinionTime),
  );

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
        {sorted.map(({ opinion, priceLevels }, index) => {
          const quote = originalText(opinion.sourceQuote, opinion.rawItemJson);
          const fallback = fallbackOpinion(opinion.rawItemJson);
          const message = opinion.status === 'MESSAGE';
          const previous = sorted[index - 1]?.opinion;
          const sameDay = previous && previous.opinionTime.slice(0, 10) === opinion.opinionTime.slice(0, 10);
          return (
            <article className={`opinion timeline-item${sameDay ? ' same-day' : ''}`} key={opinion.id}>
              <div className="opinion-top">
                <span className={`badge ${opinion.direction.toLowerCase()}`}>
                  {message ? '消息' : directionLabel(opinion.direction)}
                </span>
                <time>{opinion.opinionTime.slice(0, 10)}</time>
              </div>
              {fallback ? (
                <ExpandableQuote sessionId={opinion.sessionId} text={quote || opinion.thesis} />
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
              {!fallback && quote && <ExpandableQuote sessionId={opinion.sessionId} text={quote} />}
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

function originalText(sourceQuote?: string, rawItemJson?: string) {
  if (sourceQuote?.trim()) return cleanRepeatedQuote(sourceQuote);
  if (!rawItemJson) return '';
  try {
    const raw = JSON.parse(rawItemJson) as Record<string, unknown>;
    const value = raw['原文摘录'] || raw.sourceQuote || raw.source_quote;
    return typeof value === 'string' ? cleanRepeatedQuote(value) : '';
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
