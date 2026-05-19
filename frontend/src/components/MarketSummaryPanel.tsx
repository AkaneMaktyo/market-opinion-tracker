import { ChevronDown, ChevronRight } from 'lucide-react';
import { useEffect, useState } from 'react';
import type { LiveSession } from '../types';

interface SummaryView {
  title: string;
  date: string;
  mainLine: string;
  lines: string[];
  themes: string[];
}

export function MarketSummaryPanel({ sessions }: { sessions: LiveSession[] }) {
  const [expanded, setExpanded] = useState(false);
  const summary = latestSummary(sessions);
  const summaryKey = summary ? `${summary.date}-${summary.title}` : '';
  const previewThemes = summary?.themes.slice(0, 3) || [];

  useEffect(() => {
    setExpanded(false);
  }, [summaryKey]);

  return (
    <section className={expanded ? 'market-summary expanded' : 'market-summary'}>
      <div className="summary-compact">
        {summary ? (
          <button
            aria-expanded={expanded}
            aria-label={expanded ? '收起总体主线' : '展开总体主线'}
            className="summary-toggle"
            onClick={() => setExpanded((value) => !value)}
            title={expanded ? '收起总体主线' : '展开总体主线'}
            type="button"
          >
            {expanded ? <ChevronDown size={17} /> : <ChevronRight size={17} />}
          </button>
        ) : null}
        <div className="summary-main">
          <div className="summary-meta">
            <span className="panel-title">总体主线</span>
            <time>{summary?.date || '暂无记录'}</time>
            {summary ? <span>{summary.title}</span> : null}
          </div>
          {summary ? (
            <>
              <p className="summary-preview">{summary.mainLine}</p>
              {!expanded && previewThemes.length > 0 ? (
                <div className="theme-strip compact">
                  {previewThemes.map((theme) => (
                    <span key={theme}>{theme}</span>
                  ))}
                </div>
              ) : null}
            </>
          ) : (
            <p className="summary-empty">最近记录里还没有总体摘要</p>
          )}
        </div>
      </div>

      {summary && expanded ? (
        <div className="summary-detail">
          <div className="summary-lines">
            {summary.lines.map((line) => (
              <p key={line}>{line}</p>
            ))}
          </div>
          {summary.themes.length > 0 ? (
            <div className="theme-strip">
              {summary.themes.map((theme) => (
                <span key={theme}>{theme}</span>
              ))}
            </div>
          ) : null}
        </div>
      ) : null}
    </section>
  );
}

function latestSummary(sessions: LiveSession[]): SummaryView | null {
  for (const session of sessions) {
    const parsed = parseSession(session);
    if (parsed) return parsed;
  }
  return null;
}

function parseSession(session: LiveSession): SummaryView | null {
  try {
    const root = JSON.parse(session.rawText || '{}') as Record<string, unknown>;
    const summary = summaryFields(root['总体摘要']);
    const themes = themeNames(root['按主题划分']);
    if (summary.lines.length === 0 && themes.length === 0) return null;
    return {
      title: session.title,
      date: session.sessionDate,
      mainLine: summary.mainLine || summary.lines[0] || themes[0] || '暂无主线',
      lines: summary.lines,
      themes,
    };
  } catch {
    return null;
  }
}

function summaryFields(value: unknown) {
  const lines = objectLines(value);
  if (!value || Array.isArray(value) || typeof value !== 'object') {
    return { mainLine: '', lines };
  }
  const mainLine = text((value as Record<string, unknown>)['主线']);
  return { mainLine, lines };
}

function objectLines(value: unknown) {
  if (!value || Array.isArray(value) || typeof value !== 'object') return [];
  return Object.entries(value as Record<string, unknown>)
    .map(([key, item]) => {
      const body = text(item);
      return body ? `${key}：${body}` : '';
    })
    .filter(Boolean);
}

function themeNames(value: unknown) {
  if (!Array.isArray(value)) return [];
  return value
    .map((item) => {
      if (!item || typeof item !== 'object') return text(item);
      return text((item as Record<string, unknown>)['主题']);
    })
    .filter(Boolean);
}

function text(value: unknown): string {
  if (Array.isArray(value)) return value.map(text).filter(Boolean).join('；');
  if (value == null) return '';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}
