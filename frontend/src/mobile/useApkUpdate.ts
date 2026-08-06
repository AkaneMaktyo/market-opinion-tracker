import { App } from '@capacitor/app';
import { Capacitor, registerPlugin } from '@capacitor/core';
import { useCallback, useEffect, useRef, useState } from 'react';

interface InstallApkPlugin {
  install(options: { url: string; fileName?: string }): Promise<{ installed: boolean }>;
}

const InstallApk = registerPlugin<InstallApkPlugin>('InstallApk');
const MANIFEST_PATH = 'apk/apk.json';

export interface ApkManifest {
  versionName: string;
  versionCode: number;
  url: string;
  size?: number;
  updatedAt?: string;
}

interface ApkUpdateState {
  currentVersion: string;
  hasUpdate: boolean;
  installing: boolean;
  message: string;
  check: () => Promise<void>;
  downloadAndInstall: () => Promise<void>;
}

/** 检查服务器上的 APK 安装包，有新版时下载并调起系统安装器。 */
export function useApkUpdate(): ApkUpdateState {
  const isAndroid = Capacitor.getPlatform() === 'android';
  const [manifest, setManifest] = useState<ApkManifest | null>(null);
  const [currentVersion, setCurrentVersion] = useState('0');
  const [currentCode, setCurrentCode] = useState(0);
  const [installing, setInstalling] = useState(false);
  const [message, setMessage] = useState('');
  const checkedAt = useRef(0);

  const check = useCallback(async () => {
    if (Capacitor.getPlatform() !== 'android') return;
    try {
      const response = await fetch(`${MANIFEST_PATH}?t=${Date.now()}`, { cache: 'no-store' });
      if (!response.ok) return;
      setManifest((await response.json()) as ApkManifest);
    } catch {
      // 服务器暂未上传安装包时静默跳过
    }
  }, []);

  useEffect(() => {
    if (!isAndroid) return;
    void App.getInfo().then((info) => {
      setCurrentVersion(info.version || '0');
      setCurrentCode(Number(info.build) || 0);
      if (Date.now() - checkedAt.current > 60_000) {
        checkedAt.current = Date.now();
        void check();
      }
    });
    const timer = window.setInterval(() => void check(), 5 * 60_000);
    return () => window.clearInterval(timer);
  }, [isAndroid, check]);

  const hasUpdate = manifest !== null && manifest.versionCode > currentCode;

  const downloadAndInstall = useCallback(async () => {
    if (!manifest) return;
    setInstalling(true);
    setMessage('正在下载安装包…');
    try {
      await InstallApk.install({ url: manifest.url, fileName: 'market-opinion-tracker.apk' });
      setMessage('已下载，请在系统弹窗中确认安装');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '下载失败，请重试');
    } finally {
      setInstalling(false);
    }
  }, [manifest]);

  return { currentVersion, hasUpdate, installing, message, check, downloadAndInstall };
}
