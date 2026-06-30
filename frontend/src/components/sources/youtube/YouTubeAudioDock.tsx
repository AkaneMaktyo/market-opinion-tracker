import { type ChangeEvent, useEffect, useState } from 'react';
import { LocateFixed, Pause, Play } from 'lucide-react';
import { formatMediaClock } from './youtubeFormat';

interface Props {
  currentMs: number;
  durationMs: number;
  playing: boolean;
  title: string;
  onSeek: (nextMs: number) => void;
  onToggle: () => void;
}

export function YouTubeAudioDock({
  currentMs,
  durationMs,
  playing,
  title,
  onSeek,
  onToggle,
}: Props) {
  const max = Math.max(durationMs, 1000);
  const displayTitle = title || '尚未选择音频';
  const [draftMs, setDraftMs] = useState(currentMs);
  const [seeking, setSeeking] = useState(false);
  const progress = Math.min(seeking ? draftMs : currentMs, max);
  const percent = `${Math.max(0, Math.min(100, (progress / max) * 100))}%`;

  useEffect(() => {
    if (!seeking) {
      setDraftMs(currentMs);
    }
  }, [currentMs, seeking]);

  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    setDraftMs(Number(event.target.value) || 0);
  }

  function commitSeek() {
    if (!seeking) {
      return;
    }
    setSeeking(false);
    onSeek(draftMs);
  }

  function locateCurrentTranscript() {
    window.dispatchEvent(new Event('youtube:locate-current-transcript'));
  }

  return (
    <div className="youtube-audio-dock" style={{ ['--progress' as string]: percent }}>
      <button
        aria-label={playing ? '暂停音频' : '播放音频'}
        className="youtube-audio-toggle"
        onClick={onToggle}
        type="button"
      >
        {playing ? <Pause size={22} /> : <Play size={22} />}
      </button>
      <button
        aria-label="跳转到当前转写片段"
        className="youtube-audio-jump"
        onClick={locateCurrentTranscript}
        title="跳转到当前转写片段"
        type="button"
      >
        <LocateFixed size={18} />
      </button>
      <div className="youtube-audio-main">
        <div className="youtube-audio-labels">
          <div className="youtube-audio-title-group">
            <span className="youtube-audio-kicker">当前播放</span>
            <strong className="youtube-audio-title-marquee" title={displayTitle}>
              <span className="youtube-audio-title-track">
                <span>{displayTitle}</span>
                <span aria-hidden="true">{displayTitle}</span>
              </span>
            </strong>
          </div>
          <span className="youtube-audio-clock">
            {formatMediaClock(progress)} / {formatMediaClock(durationMs)}
          </span>
        </div>
        <div className="youtube-audio-wave">
          <div className="youtube-audio-progress" />
          <input
            aria-label="音频进度"
            className="youtube-audio-range"
            max={max}
            min={0}
            onChange={handleChange}
            onKeyUp={commitSeek}
            onMouseDown={() => setSeeking(true)}
            onMouseUp={commitSeek}
            onTouchEnd={commitSeek}
            onTouchStart={() => setSeeking(true)}
            type="range"
            value={progress}
          />
        </div>
      </div>
    </div>
  );
}
