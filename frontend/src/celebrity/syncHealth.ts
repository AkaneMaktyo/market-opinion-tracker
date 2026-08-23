import type { CelebritySyncStatus } from './types';

export type CelebritySyncTone = 'success' | 'running' | 'partial' | 'failed' | 'muted' | 'pending';

export interface CelebritySyncHealth {
  tone: CelebritySyncTone;
  label: string;
  title: string;
  message: string;
  details?: string;
}

export function celebritySyncHealth(status?: CelebritySyncStatus): CelebritySyncHealth {
  if (!status) {
    return { tone: 'pending', label: '读取状态中', title: '正在读取数据状态', message: '请稍候确认最近一次公开披露同步结果。' };
  }
  if (!status.enabled) {
    return { tone: 'muted', label: '同步已关闭', title: '公开披露同步已关闭', message: '页面只展示此前已入库的公开披露。' };
  }
  if (status.running) {
    return { tone: 'running', label: '正在同步', title: '正在同步公开披露', message: '同步完成前继续展示最近一次有效快照。' };
  }
  if (status.lastOutcome === 'SUCCESS') {
    return { tone: 'success', label: '已完成同步', title: '最近一次同步成功', message: '当前页面展示最近一次成功入库的公开披露。' };
  }
  if (status.lastOutcome === 'PARTIAL' || status.lastOutcome === 'FAILED') {
    const failed = status.lastOutcome === 'FAILED';
    return {
      tone: failed ? 'failed' : 'partial',
      label: failed ? '刷新未完成' : '部分来源待恢复',
      title: failed ? '本轮披露刷新未完成' : '本轮仅部分来源完成同步',
      message: failureSummary(status.lastError),
      details: status.lastError,
    };
  }
  return { tone: 'pending', label: '尚未同步', title: '尚未开始同步', message: '点击刷新后将从公开来源异步导入披露。' };
}

function failureSummary(error?: string) {
  const notices: string[] = [];
  if (error?.includes('木头姐')) {
    notices.push('ARK 官方日度来源暂时不可达，页面保留最近一次有效快照。');
  }
  if (error?.includes('SEC_USER_AGENT')) {
    notices.push('SEC 13F 等待合规身份配置，不会伪造或猜测名人持仓。');
  }
  return notices.length > 0
    ? notices.join(' ')
    : '页面保留最近一次有效快照；网络失败和缺失行情不会被写成 0。';
}
