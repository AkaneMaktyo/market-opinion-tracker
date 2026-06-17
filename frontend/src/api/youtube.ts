import type { YouTubeChannel, YouTubeVideo } from '../types/youtube';
import { apiBase, json } from './http';

export const youtubeApi = {
  youtubeChannels: () => json<{ channels: YouTubeChannel[] }>('/api/youtube/channels'),
  createYouTubeChannel: (body: { sourceUrl: string; name: string }) =>
    json<{ ok: boolean; result: { channel: YouTubeChannel['channel']; videos: YouTubeVideo[] } }>(
      '/api/youtube/channels',
      { method: 'POST', body: JSON.stringify(body) },
    ),
  syncYouTubeChannel: (channelRowId: string) =>
    json<{ ok: boolean; result: { channel: YouTubeChannel['channel']; videos: YouTubeVideo[] } }>(
      `/api/youtube/channels/${encodeURIComponent(channelRowId)}/sync`,
      { method: 'POST' },
    ),
  syncAllYouTubeChannels: () =>
    json<{ ok: boolean; results: Array<{ channel: YouTubeChannel['channel']; videos: YouTubeVideo[] }> }>(
      '/api/youtube/sync',
      { method: 'POST' },
    ),
  youtubeVideo: (videoId: string) =>
    json<{ ok: boolean; video: YouTubeVideo }>(`/api/youtube/videos/${encodeURIComponent(videoId)}`),
  deleteYouTubeChannel: (channelRowId: string) =>
    json<{ ok: boolean }>(`/api/youtube/channels/${encodeURIComponent(channelRowId)}`, {
      method: 'DELETE',
    }),
  youtubeAudioUrl: (videoId: string) => `${apiBase}/youtube/audio/${encodeURIComponent(videoId)}`,
};
