import { CheckCircle2, CircleDot, XCircle } from 'lucide-react';
import { api } from '../api/client';
import type { OpinionView } from '../types';

interface Props {
  opinions: OpinionView[];
  onChanged: () => void;
}

export function OpinionList({ opinions, onChanged }: Props) {
  async function review(id: string, outcome: string) {
    await api.review(id, { outcome });
    onChanged();
  }

  return (
    <section className="opinions">
      <div className="panel-title">观点时间线</div>
      {opinions.length === 0 && <div className="empty">当前品种还没有观点</div>}
      {opinions.map(({ opinion, priceLevels, review: result }) => (
        <article className="opinion" key={opinion.id}>
          <div className="opinion-top">
            <span className={`badge ${opinion.direction.toLowerCase()}`}>
              {directionLabel(opinion.direction)}
            </span>
            <time>{opinion.opinionTime.slice(0, 10)}</time>
          </div>
          <h3>{opinion.thesis}</h3>
          <p>{opinion.horizon}</p>
          <div className="levels">
            {priceLevels.map((level) => (
              <span key={`${level.levelType}-${level.price}`}>
                {levelLabel(level.levelType)} {level.price}
              </span>
            ))}
          </div>
          {opinion.priceNotesText && <p className="detail-text">{opinion.priceNotesText}</p>}
          {opinion.catalystsText && <p className="detail-text">催化：{opinion.catalystsText}</p>}
          {opinion.risksText && <p className="detail-text">风险：{opinion.risksText}</p>}
          <div className="review-row">
            <span>{result ? outcomeLabel(result.outcome) : '待复盘'}</span>
            <button onClick={() => review(opinion.id, 'HIT')} title="标记命中">
              <CheckCircle2 size={16} />
            </button>
            <button onClick={() => review(opinion.id, 'PARTIAL')} title="标记部分命中">
              <CircleDot size={16} />
            </button>
            <button onClick={() => review(opinion.id, 'MISS')} title="标记失败">
              <XCircle size={16} />
            </button>
          </div>
        </article>
      ))}
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
