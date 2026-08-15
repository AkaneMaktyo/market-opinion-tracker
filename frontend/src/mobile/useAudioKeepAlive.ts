import { Capacitor, registerPlugin } from '@capacitor/core';
import { useEffect, useRef } from 'react';

interface KeepAlivePlugin {
  setPlayback(options: { active: boolean }): Promise<{ playback: boolean }>;
}

const KeepAlive = registerPlugin<KeepAlivePlugin>('KeepAlive');

/** 播放期间保持安卓媒体前台服务存活，避免切后台后转写音频被系统暂停。 */
export function useAudioKeepAlive(playing: boolean, title: string) {
  const isAndroid = Capacitor.getPlatform() === 'android';
  const activeRef = useRef(false);

  useEffect(() => {
    if (!isAndroid) return;
    if (playing) {
      activeRef.current = true;
      void KeepAlive.setPlayback({ active: true }).catch(() => undefined);
      return;
    }
    if (!activeRef.current) return;
    activeRef.current = false;
    void KeepAlive.setPlayback({ active: false }).catch(() => undefined);
  }, [isAndroid, playing, title]);

  useEffect(() => {
    if (!isAndroid) return;
    return () => {
      void KeepAlive.setPlayback({ active: false }).catch(() => undefined);
    };
  }, [isAndroid]);
}
