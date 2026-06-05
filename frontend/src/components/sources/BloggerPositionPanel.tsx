import { Pencil, Plus, RotateCcw, Save, X } from 'lucide-react';
import { useState } from 'react';
import type { WxPusherBlogger } from '../../types';
import type { BloggerDraft, PositionsByKol, SetDraft } from './sourceTypes';

interface Props {
  bloggers: WxPusherBlogger[];
  positionsByKol: PositionsByKol;
  draft: BloggerDraft;
  loading: boolean;
  setDraft: SetDraft;
  setMessage: (value: string) => void;
  onSaveBlogger: () => void;
  onEditBlogger: (blogger: WxPusherBlogger) => void;
  onAddPosition: (kolId: string, symbol: string) => void;
  onClosePosition: (id: string) => void;
}

export function BloggerPositionPanel({
  bloggers,
  positionsByKol,
  draft,
  loading,
  setDraft,
  setMessage,
  onSaveBlogger,
  onEditBlogger,
  onAddPosition,
  onClosePosition,
}: Props) {
  const [symbols, setSymbols] = useState<Record<string, string>>({});

  function add(kolId: string) {
    const symbol = symbols[kolId]?.trim();
    if (!symbol) return;
    onAddPosition(kolId, symbol);
    setSymbols((current) => ({ ...current, [kolId]: '' }));
  }

  return (
    <section className="source-panel">
      <div className="panel-title">博主白名单</div>
      <div className="form-grid two">
        <label>博主名称<input value={draft.bloggerName} onChange={(e) => setDraft((s) => ({ ...s, bloggerName: e.target.value }))} /></label>
        <label>别名（逗号分隔）<input value={draft.aliasesText} onChange={(e) => setDraft((s) => ({ ...s, aliasesText: e.target.value }))} /></label>
      </div>
      <label className="toggle"><input checked={draft.enabled} onChange={(e) => setDraft((s) => ({ ...s, enabled: e.target.checked }))} type="checkbox" />启用并补抓最近 30 条</label>
      <div className="inline-actions">
        <button className="primary" disabled={loading} onClick={onSaveBlogger} type="button">
          <Save size={16} />
          {draft.id ? '更新博主' : '添加博主'}
        </button>
        {draft.id ? (
          <button
            className="primary secondary"
            onClick={() => {
              setDraft({ id: '', bloggerName: '', aliasesText: '', enabled: true });
              setMessage('');
            }}
            type="button"
          >
            <RotateCcw size={16} />
            取消编辑
          </button>
        ) : null}
      </div>
      <div className="blogger-list">
        {bloggers.map((blogger) => (
          <div className="blogger-row position-blogger-row" key={blogger.id}>
            <div>
              <strong>{blogger.bloggerName}</strong>
              <p className="muted">别名：{blogger.aliases.join(', ') || '无'} ｜ 种子：{blogger.seedCompletedAt ? '已完成' : '待补抓'}</p>
              <PositionTags
                positions={positionsByKol[blogger.kolId] || []}
                onClosePosition={onClosePosition}
              />
              <div className="position-add">
                <input
                  onChange={(event) => setSymbols((current) => ({ ...current, [blogger.kolId]: event.target.value }))}
                  onKeyDown={(event) => event.key === 'Enter' && add(blogger.kolId)}
                  placeholder="添加持仓代码"
                  value={symbols[blogger.kolId] || ''}
                />
                <button className="icon-button" disabled={loading} onClick={() => add(blogger.kolId)} title="添加持仓" type="button">
                  <Plus size={15} />
                </button>
              </div>
            </div>
            <div className="inline-actions">
              <span className={blogger.enabled ? 'status-pill active' : 'status-pill'}>{blogger.enabled ? '启用' : '停用'}</span>
              <button className="icon-button" onClick={() => onEditBlogger(blogger)} title="编辑" type="button">
                <Pencil size={15} />
              </button>
            </div>
          </div>
        ))}
        {bloggers.length === 0 ? <p className="muted">还没有配置任何博主。</p> : null}
      </div>
    </section>
  );
}

function PositionTags({
  positions,
  onClosePosition,
}: {
  positions: PositionsByKol[string];
  onClosePosition: (id: string) => void;
}) {
  if (positions.length === 0) {
    return <div className="position-tags muted">当前无持仓</div>;
  }
  return (
    <div className="position-tags">
      {positions.map((position) => (
        <span className="position-tag" key={position.id}>
          {position.symbol}
          <button onClick={() => onClosePosition(position.id)} title={`移出 ${position.symbol}`} type="button">
            <X size={12} />
          </button>
        </span>
      ))}
    </div>
  );
}
