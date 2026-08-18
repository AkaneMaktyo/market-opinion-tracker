import { json } from './http';
import type {
  WxPusherBlogger,
  WxPusherMessage,
  WxPusherNotifySettings,
  WxPusherRecentMessage,
  WxPusherSettings,
  WxPusherStatus,
} from '../types';
import type { PriceAlertRecognitionResult } from '../types/alerts';

export const wxpusherApi = {
  wxpusherSettings: () => json<WxPusherSettings>('/api/wxpusher/settings'),
  updateWxPusherSettings: (body: WxPusherSettings) =>
    json<WxPusherSettings>('/api/wxpusher/settings', {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  wxpusherNotifySettings: () => json<WxPusherNotifySettings>('/api/wxpusher/notify-settings'),
  updateWxPusherNotifySettings: (body: WxPusherNotifySettings) =>
    json<WxPusherNotifySettings>('/api/wxpusher/notify-settings', {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  wxpusherStatus: () => json<WxPusherStatus>('/api/wxpusher/status'),
  wxpusherBloggers: () => json<WxPusherBlogger[]>('/api/wxpusher/bloggers'),
  createWxPusherBlogger: (body: {
    bloggerName: string;
    aliases: string[];
    enabled: boolean;
    notifyEnabled?: boolean;
  }) =>
    json<WxPusherBlogger>('/api/wxpusher/bloggers', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  updateWxPusherBlogger: (body: {
    id: string;
    bloggerName: string;
    aliases: string[];
    enabled: boolean;
    notifyEnabled?: boolean;
  }) =>
    json<WxPusherBlogger>('/api/wxpusher/bloggers', {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  wxpusherMessages: (status = 'FAILED', kolId = '', limit = 30) => {
    const params = new URLSearchParams();
    if (status) params.set('status', status);
    if (kolId) params.set('kolId', kolId);
    params.set('limit', String(limit));
    return json<WxPusherMessage[]>(`/api/wxpusher/messages?${params.toString()}`);
  },
  wxpusherRecentMessages: (limit = 50) =>
    json<WxPusherRecentMessage[]>(`/api/wxpusher/messages/recent?limit=${limit}`),
  wxpusherSearchMessages: (keyword: string, sinceDays = 365, limit = 100, signal?: AbortSignal) => {
    const params = new URLSearchParams({ keyword, sinceDays: String(sinceDays), limit: String(limit) });
    return json<WxPusherRecentMessage[]>(`/api/wxpusher/messages/recent/search?${params.toString()}`, { signal });
  },
  wxpusherRecentMessageDetail: (id: string) =>
    json<WxPusherRecentMessage>(`/api/wxpusher/messages/recent/${encodeURIComponent(id)}`),
  recognizeWxPusherPriceAlerts: (id: string, kolId: string) =>
    json<PriceAlertRecognitionResult>(
      `/api/wxpusher/messages/recent/${encodeURIComponent(id)}/price-alert-recognition?kolId=${encodeURIComponent(kolId)}`,
      { method: 'POST' },
    ),
  wxpusherOcrMessages: (limit = 50) =>
    json<WxPusherMessage[]>(`/api/wxpusher/messages/ocr?limit=${limit}`),
  retryWxPusherMessage: (id: string) =>
    json<WxPusherMessage>(`/api/wxpusher/messages/${encodeURIComponent(id)}/retry`, {
      method: 'POST',
    }),
};
