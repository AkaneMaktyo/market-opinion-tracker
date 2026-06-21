import { api } from '../../../api/client';
import type { YouTubeChannel, YouTubeVideo } from '../../../types/youtube';

export async function syncAllChannels(
  loadChannels: (videoId?: string) => Promise<void>,
  setLoading: (value: boolean) => void,
  setMessage: (value: string) => void,
  activeVideoId: string,
) {
  setMessage('');
  setLoading(true);
  try {
    await api.syncAllYouTubeChannels();
    setMessage('全部频道同步完成');
    await loadChannels(activeVideoId);
  } catch (error) {
    setMessage(error instanceof Error ? error.message : '同步全部频道失败');
    setLoading(false);
  }
}

export async function syncOneChannel(
  channelRowId: string,
  loadChannels: (videoId?: string) => Promise<void>,
  setLoading: (value: boolean) => void,
  setMessage: (value: string) => void,
  activeVideoId: string,
) {
  setMessage('');
  setLoading(true);
  try {
    await api.syncYouTubeChannel(channelRowId);
    setMessage('频道同步完成');
    await loadChannels(activeVideoId);
  } catch (error) {
    setMessage(error instanceof Error ? error.message : '同步频道失败');
    setLoading(false);
  }
}

export async function removeOneChannel(
  channelRowId: string,
  loadChannels: () => Promise<void>,
  setLoading: (value: boolean) => void,
  setMessage: (value: string) => void,
) {
  setMessage('');
  setLoading(true);
  try {
    await api.deleteYouTubeChannel(channelRowId);
    setMessage('频道已删除');
    await loadChannels();
  } catch (error) {
    setMessage(error instanceof Error ? error.message : '删除频道失败');
    setLoading(false);
  }
}

export async function openVideoDetail(
  videoId: string,
  activeVideoId: string,
  setActiveVideoId: (value: string) => void,
  setActiveVideo: (value: YouTubeVideo | null) => void,
  setActiveMs: (value: number) => void,
  setDurationMs: (value: number) => void,
  setPlaying: (value: boolean) => void,
  setMessage: (value: string) => void,
) {
  if (videoId === activeVideoId) {
    return;
  }
  setActiveVideoId(videoId);
  setActiveMs(0);
  setDurationMs(0);
  setPlaying(false);
  try {
    const detail = await api.youtubeVideo(videoId);
    setActiveVideo(detail.video);
    setDurationMs(detail.video.audioDurationMs || 0);
  } catch (error) {
    setMessage(error instanceof Error ? error.message : '读取视频转写失败');
  }
}

export function hasVideo(channels: YouTubeChannel[], videoId: string) {
  return channels.some((channel) =>
    channel.videos.some((video) => video.videoId === videoId),
  );
}

export function seekAudio(
  nextMs: number,
  audio: HTMLAudioElement | null,
  setActiveMs: (value: number) => void,
) {
  if (!audio) {
    return;
  }
  audio.currentTime = nextMs / 1000;
  setActiveMs(nextMs);
  void audio.play().catch(() => undefined);
}

export function toggleAudio(audio: HTMLAudioElement | null) {
  if (!audio) {
    return;
  }
  if (audio.paused) {
    void audio.play().catch(() => undefined);
    return;
  }
  audio.pause();
}
