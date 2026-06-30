import { RefreshCw } from 'lucide-react';
import { type FormEvent, useEffect, useRef, useState } from 'react';
import { api } from '../../../api/client';
import type { YouTubeChannel, YouTubeVideo } from '../../../types/youtube';
import { YouTubeAudioDock } from './YouTubeAudioDock';
import { YouTubeChannelColumn } from './YouTubeChannelColumn';
import { YouTubeDetailColumn } from './YouTubeDetailColumn';
import {
  hasVideo,
  openVideoDetail,
  removeOneChannel,
  seekAudio,
  syncAllChannels,
  syncOneChannel,
  toggleAudio,
} from './youtubeWorkspaceActions';
export function YouTubeWorkspace({ mode }: { mode: 'page' | 'panel' }) {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [channels, setChannels] = useState<YouTubeChannel[]>([]);
  const [activeVideo, setActiveVideo] = useState<YouTubeVideo | null>(null);
  const [activeVideoId, setActiveVideoId] = useState('');
  const [sourceUrl, setSourceUrl] = useState('');
  const [name, setName] = useState('');
  const [message, setMessage] = useState('');
  const [loading, setLoading] = useState(false);
  const [activeMs, setActiveMs] = useState(0);
  const [durationMs, setDurationMs] = useState(0);
  const [playing, setPlaying] = useState(false);
  const videos = channels.flatMap((item) => item.videos || []);
  const unfinishedVideos = videos.filter((item) => item.transcriptStatus !== 'ready').length;
  useEffect(() => {
    void loadChannels();
  }, []);

  useEffect(() => {
    if (playing) return;
    const timer = window.setInterval(() => void loadChannels(activeVideoId), 60000);
    return () => window.clearInterval(timer);
  }, [activeVideoId, playing]);

  useEffect(() => {
    if (!playing || !audioRef.current) return;
    let frameId = 0;
    const sync = () => {
      const audio = audioRef.current;
      if (!audio) return;
      setActiveMs(Math.round(audio.currentTime * 20) * 50);
      frameId = window.requestAnimationFrame(sync);
    };
    sync();
    return () => window.cancelAnimationFrame(frameId);
  }, [activeVideoId, playing]);

  async function loadChannels(nextVideoId = activeVideoId) {
    setLoading(true);
    try {
      const data = await api.youtubeChannels();
      const nextChannels = data.channels || [];
      setChannels(nextChannels);
      const targetVideoId = nextVideoId && hasVideo(nextChannels, nextVideoId) ? nextVideoId : firstVideoId(nextChannels);
      if (!targetVideoId) {
        resetSelection();
        return;
      }
      const detail = await api.youtubeVideo(targetVideoId);
      setActiveVideoId(targetVideoId);
      setActiveVideo(detail.video);
      setDurationMs(detail.video.audioDurationMs || 0);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '读取 YouTube 转写失败');
    } finally {
      setLoading(false);
    }
  }

  function resetSelection() {
    setActiveVideoId('');
    setActiveVideo(null);
    setActiveMs(0);
    setDurationMs(0);
    setPlaying(false);
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!sourceUrl.trim()) {
      setMessage('请先填写频道链接、@handle 或 UC 开头的频道 ID');
      return;
    }
    setMessage('');
    setLoading(true);
    try {
      await api.createYouTubeChannel({ sourceUrl: sourceUrl.trim(), name: name.trim() });
      setSourceUrl('');
      setName('');
      setMessage('频道已加入并完成同步');
      await loadChannels();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '新增频道失败');
      setLoading(false);
    }
  }

  return (
    <section className={panelClass(mode)}>
      <div className="youtube-head">
        <div>
          <div className="panel-title">YouTube 音频转写</div>
          <p className="muted">
            同步频道后会下载最近视频音频，通过阿里云生成可跳转分段转写，
            转写完成后自动触发 WxPusher 通知。
          </p>
        </div>
        <button
          className="icon-button"
          disabled={loading}
          onClick={() => void syncAllChannels(loadChannels, setLoading, setMessage, activeVideoId)}
          title="同步全部频道"
          type="button"
        >
          <RefreshCw size={16} />
        </button>
      </div>

      <form className="youtube-form" onSubmit={(event) => void handleSubmit(event)}>
        <input
          onChange={(event) => setSourceUrl(event.target.value)}
          placeholder="频道链接、@handle 或 UC 开头频道 ID"
          value={sourceUrl}
        />
        <input
          onChange={(event) => setName(event.target.value)}
          placeholder="可选显示名称"
          value={name}
        />
        <button className="primary" disabled={loading} type="submit">
          添加并同步
        </button>
      </form>

      <div className="youtube-stats">
        <StatItem label="频道" value={channels.length} />
        <StatItem label="视频" value={videos.length} />
        <StatItem label="完成" value={videos.filter((item) => item.transcriptStatus === 'ready').length} />
        <StatItem label="待处理" value={unfinishedVideos} />
      </div>

      <div className="youtube-grid">
        <YouTubeChannelColumn
          activeVideoId={activeVideoId}
          channels={channels}
          loading={loading}
          onRemove={(channelRowId) =>
            void removeOneChannel(channelRowId, loadChannels, setLoading, setMessage)
          }
          onSync={(channelRowId, nextVideoId) =>
            void syncOneChannel(channelRowId, loadChannels, setLoading, setMessage, nextVideoId)
          }
        />
        <YouTubeDetailColumn
          activeMs={activeMs}
          activeVideo={activeVideo}
          activeVideoId={activeVideoId}
          audioRef={audioRef}
          durationMs={durationMs}
          onOpenVideo={(videoId) =>
            void openVideoDetail(
              videoId,
              activeVideoId,
              setActiveVideoId,
              setActiveVideo,
              setActiveMs,
              setDurationMs,
              setPlaying,
              setMessage,
            )
          }
          onSeek={(nextMs) => seekAudio(nextMs, audioRef.current, setActiveMs)}
          setActiveMs={setActiveMs}
          setDurationMs={setDurationMs}
          setPlaying={setPlaying}
          videos={videos}
        />
      </div>

      {mode === 'page' && activeVideo ? (
        <YouTubeAudioDock
          currentMs={activeMs}
          durationMs={durationMs}
          onSeek={(nextMs) => seekAudio(nextMs, audioRef.current, setActiveMs)}
          onToggle={() => toggleAudio(audioRef.current)}
          playing={playing}
          title={activeVideo.title}
        />
      ) : null}

      {message ? <div className="form-message">{message}</div> : null}
    </section>
  );
}

function panelClass(mode: 'page' | 'panel') { return `source-panel youtube-panel${mode === 'page' ? ' youtube-panel-page youtube-panel-docked' : ''}`; }
function StatItem({ label, value }: { label: string; value: number }) { return <div><span className="muted">{label}</span><strong>{value}</strong></div>; }
function firstVideoId(channels: YouTubeChannel[]) { return channels.flatMap((item) => item.videos || [])[0]?.videoId || ''; }
