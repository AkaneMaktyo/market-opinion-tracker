import { json } from './http';
import type {
  WxPusherBlogger,
  WxPusherMessage,
  WxPusherSettings,
  WxPusherStatus,
} from '../types';

export const wxpusherApi = {
  wxpusherSettings: () => json<WxPusherSettings>('/api/wxpusher/settings'),
  updateWxPusherSettings: (body: WxPusherSettings) =>
    json<WxPusherSettings>('/api/wxpusher/settings', {
      method: 'PUT',
      body: JSON.stringify(body),
    }),
  wxpusherStatus: () => json<WxPusherStatus>('/api/wxpusher/status'),
  wxpusherBloggers: () => json<WxPusherBlogger[]>('/api/wxpusher/bloggers'),
  createWxPusherBlogger: (body: {
    bloggerName: string;
    aliases: string[];
    enabled: boolean;
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
  retryWxPusherMessage: (id: string) =>
    json<WxPusherMessage>(`/api/wxpusher/messages/${encodeURIComponent(id)}/retry`, {
      method: 'POST',
    }),
};
