import { Pause, Play, RefreshCw, Sparkles } from 'lucide-react';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { api } from '../../api/client';
import type { YouTubeChannel, YouTubeTranscriptSegment, YouTubeVideo } from '../../types/youtube';
import { formatMediaClock, formatTranscriptStatus } from '../../components/sources/youtube/youtubeFormat';

interface Props {
  active: boolean;
  onCreateOpinion: (text: string) => void;
}

export function MobileTranscript({ active, onCreateOpinion }: Props) {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [channels, setChannels] = useState<YouTubeChannel[]>([]);
  const [activeVideo, setActiveVideo] = useState<YouTubeVideo | null>(null);
  const [activeMs, setActiveMs] = useState(0);
  const [durationMs, setDurationMs] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState('');
  const videos = useMemo(() => channels.flatMap((item) => item.videos || []), [channels]);
  const activeSegment = activeVideo?.transcriptSegments.find((segment) => activeMs >= segment.startMs && activeMs < segment.endMs);

  const load = useCallback(async (requestedVideoId = '') => {
    setLoading(true);
    setMessage('');
    try {
      const data = await api.youtubeChannels();
      const nextChannels = data.channels || [];
      const available = nextChannels.flatMap((item) => item.videos || []);
      const videoId = available.some((item) => item.videoId === requestedVideoId) ? requestedVideoId : available[0]?.videoId;
      setChannels(nextChannels);
      if (!videoId) {
        setActiveVideo(null);
        return;
      }
      const detail = await api.youtubeVideo(videoId);
      const nextVideo = detail.video;
      if (!nextVideo.readAt) void api.markYouTubeVideoRead(videoId).catch(() => undefined);
      setActiveVideo(nextVideo);
      setActiveMs(0);
      setDurationMs(nextVideo.audioDurationMs || 0);
      setPlaying(false);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '读取视频转写失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (active) void load();
  }, [active, load]);

  async function selectVideo(videoId: string) {
    setLoading(true);
    setMessage('');
    try {
      const detail = await api.youtubeVideo(videoId);
      setActiveVideo(detail.video);
      setActiveMs(0);
      setDurationMs(detail.video.audioDurationMs || 0);
      setPlaying(false);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '打开视频失败');
    } finally {
      setLoading(false);
    }
  }

  async function togglePlayback() {
    const audio = audioRef.current;
    if (!audio) return;
    if (audio.paused) await audio.play();
    else audio.pause();
  }

  function seek(nextMs: number) {
    setActiveMs(nextMs);
    if (audioRef.current) audioRef.current.currentTime = nextMs / 1000;
  }

  return (
    <div className="mobile-screen-content mobile-transcript-screen">
      <section className="mobile-card mobile-now-playing">
        <div className="mobile-video-cover"><button aria-label={playing ? '暂停' : '播放'} disabled={!activeVideo?.audioPath} onClick={() => void togglePlayback()} type="button">{playing ? <Pause size={23} /> : <Play size={23} />}</button><span>{formatMediaClock(durationMs)}</span></div>
        <div className="mobile-video-meta"><small>正在查看</small><h2>{activeVideo?.title || (loading ? '正在载入…' : '暂无转写视频')}</h2><span>{activeVideo ? formatTranscriptStatus(activeVideo.transcriptStatus) : '请先在来源管理中添加频道'}</span></div>
        <button className="mobile-refresh-button" aria-label="刷新并显示最新转写" disabled={loading} onClick={() => void load()} type="button"><RefreshCw className={loading ? 'spinning' : ''} size={18} /></button>
      </section>

      {videos.length > 0 ? <label className="mobile-video-picker">切换视频<select onChange={(event) => void selectVideo(event.target.value)} value={activeVideo?.videoId || ''}>{videos.map((video) => <option key={video.videoId} value={video.videoId}>{video.title}</option>)}</select></label> : null}

      <section className="mobile-card mobile-audio-card">
        <div><strong>{formatMediaClock(activeMs)}</strong><small>{formatMediaClock(durationMs)}</small></div>
        <input aria-label="音频进度" disabled={!activeVideo?.audioPath} max={Math.max(durationMs, 1)} min="0" onChange={(event) => seek(Number(event.target.value))} type="range" value={Math.min(activeMs, Math.max(durationMs, 1))} />
        {activeVideo?.audioPath ? <audio onDurationChange={(event) => setDurationMs(Math.round(event.currentTarget.duration * 1000))} onEnded={() => setPlaying(false)} onPause={() => setPlaying(false)} onPlay={() => setPlaying(true)} onTimeUpdate={(event) => setActiveMs(Math.round(event.currentTarget.currentTime * 1000))} ref={audioRef} src={api.youtubeAudioUrl(activeVideo.videoId)} /> : null}
      </section>

      <div className="mobile-list-title"><strong>逐段转写</strong><small>{activeVideo?.transcriptSegments.length || 0} 段</small></div>
      <section className="mobile-transcript-list">
        {message ? <div className="mobile-card form-message">{message}</div> : null}
        {!message && activeVideo?.transcriptSegments.length === 0 ? <div className="mobile-card mobile-empty">{emptyTranscriptMessage(activeVideo)}</div> : null}
        {activeVideo?.transcriptSegments.map((segment) => <TranscriptItem active={segment === activeSegment} key={`${segment.startMs}-${segment.text}`} onClick={() => seek(segment.startMs)} segment={segment} />)}
      </section>

      <button className="mobile-floating-action" disabled={!activeSegment?.text} onClick={() => onCreateOpinion(activeSegment?.text || '')} type="button"><Sparkles size={19} />提取当前段为观点</button>
    </div>
  );
}

function emptyTranscriptMessage(video: YouTubeVideo) {
  if (video.errorMessage?.trim()) return video.errorMessage;
  if (video.transcriptStatus === 'error') return '转写失败，请稍后重试';
  if (video.transcriptStatus === 'retry_midnight') return '转写额度暂时不足，系统将在午夜自动重试';
  if (video.transcriptStatus === 'ready') return '转写已完成，但没有识别到可展示的文字';
  return '转写仍在处理中，刷新后再查看';
}

function TranscriptItem({ active, onClick, segment }: { active: boolean; onClick: () => void; segment: YouTubeTranscriptSegment }) {
  return <button className={`mobile-transcript-item${active ? ' active' : ''}`} onClick={onClick} type="button"><time>{formatMediaClock(segment.startMs)}</time><p>{segment.text}</p>{active ? <span>正在播放</span> : null}</button>;
}
