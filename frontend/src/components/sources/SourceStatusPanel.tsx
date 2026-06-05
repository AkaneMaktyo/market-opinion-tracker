import type { WxPusherStatus } from '../../types';

export function SourceStatusPanel({ status }: { status: WxPusherStatus | null }) {
  return (
    <section className="source-panel">
      <div className="panel-title">运行状态</div>
      {status ? (
        <div className="status-grid">
          <StatusItem label="监听状态" value={status.websocketState} />
          <StatusItem label="轮询 / 实时" value={`${flag(status.pollingEnabled)} / ${flag(status.websocketEnabled)}`} />
          <StatusItem label="博主数" value={`${status.enabledBloggers} / ${status.totalBloggers}`} />
          <StatusItem label="模型状态" value={status.llmConfigured ? (status.llmReachable ? '可用' : '不可用') : '未配置'} />
          <StatusItem label="最近轮询" value={trimTime(status.lastPollAt)} />
          <StatusItem label="最近心跳" value={trimTime(status.lastHeartbeatAt)} />
        </div>
      ) : (
        <p className="muted">正在读取状态…</p>
      )}
      {status?.llmMessage ? <p className="muted">模型检查：{status.llmMessage}</p> : null}
      {status?.lastError ? <div className="form-message">{status.lastError}</div> : null}
    </section>
  );
}

function StatusItem({ label, value }: { label: string; value?: string }) {
  return (
    <div className="status-item">
      <span>{label}</span>
      <strong>{value || '暂无'}</strong>
    </div>
  );
}

function flag(value: boolean) {
  return value ? '开' : '关';
}

function trimTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '暂无';
}
