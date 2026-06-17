export function formatMediaClock(value: number) {
  const total = Math.floor((value || 0) / 1000);
  const hours = Math.floor(total / 3600);
  const minutes = String(Math.floor((total % 3600) / 60)).padStart(2, '0');
  const seconds = String(total % 60).padStart(2, '0');
  return hours > 0 ? `${hours}:${minutes}:${seconds}` : `${minutes}:${seconds}`;
}

export function formatDurationLabel(value: number) {
  if (!value) {
    return '时长未知';
  }
  return `时长 ${formatMediaClock(value)}`;
}

export function formatTranscriptStatus(status: string) {
  const labels: Record<string, string> = {
    ready: '已完成',
    error: '失败',
    pending: '处理中',
    retry_midnight: '零点重试',
  };
  return labels[status] || status || '未知';
}

export function formatDateTimeLabel(value?: string | null) {
  if (!value) {
    return '未知时间';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date).replace(/\//g, '-');
}

export function formatChannelHandle(value?: string | null) {
  if (!value) {
    return '';
  }
  try {
    return decodeURIComponent(value);
  } catch {
    return value;
  }
}
