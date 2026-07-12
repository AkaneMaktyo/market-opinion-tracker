import { useState } from 'react';
import { api } from '../../api/client';

interface Props {
  sessionId?: string;
  text: string;
}

const PREVIEW_LENGTH = 420;

export function ExpandableQuote({ sessionId, text }: Props) {
  const cleaned = cleanRepeatedQuote(text);
  const [expanded, setExpanded] = useState(false);
  const [fullText, setFullText] = useState(cleaned);
  const [loading, setLoading] = useState(false);
  const long = cleaned.length > PREVIEW_LENGTH || cleaned.endsWith('...');
  const shown = expanded ? fullText : preview(cleaned);

  async function toggle() {
    if (expanded) return setExpanded(false);
    if (sessionId) {
      setLoading(true);
      try {
        const session = await api.session(sessionId);
        if (session.rawText?.trim()) setFullText(cleanRepeatedQuote(session.rawText));
      } finally {
        setLoading(false);
      }
    }
    setExpanded(true);
  }

  return (
    <div className="source-quote">
      <div>原文：{shown}</div>
      {long && (
        <button className="quote-toggle" disabled={loading} onClick={toggle} type="button">
          {loading ? '加载中…' : expanded ? '收起' : '展开全文'}
        </button>
      )}
    </div>
  );
}

function preview(value: string) {
  return value.length <= PREVIEW_LENGTH ? value : `${value.slice(0, PREVIEW_LENGTH)}…`;
}

export function cleanRepeatedQuote(value: string) {
  const kept: string[] = [];
  for (const rawLine of value.trim().split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || /^您订阅的【.+】有新的消息$/.test(line)) continue;
    const quoted = line.match(/^\[[^\]]+\]\s*[^:：]+[:：]\s*(.+)$/)?.[1]?.trim();
    const previous = kept.join('\n').toLocaleLowerCase();
    if (quoted && previous.includes(quoted.toLocaleLowerCase())) continue;
    kept.push(line);
  }
  return kept.join('\n');
}
