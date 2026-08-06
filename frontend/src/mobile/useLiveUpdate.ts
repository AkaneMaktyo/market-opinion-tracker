import { LiveUpdate } from '@capawesome/capacitor-live-update';
import { App as CapacitorApp } from '@capacitor/app';
import { Capacitor } from '@capacitor/core';
import { useCallback, useEffect, useState } from 'react';

interface UpdateManifest {
  bundleId: string;
  url: string;
  checksum: string;
  signature: string;
  nativeVersionCode: string;
  createdAt: string;
}

export type LiveUpdatePhase = 'idle' | 'checking' | 'current' | 'downloading' | 'apk-required' | 'failed';

export interface LiveUpdateController {
  phase: LiveUpdatePhase;
  message: string;
  checkedAt: number | null;
  checkNow: () => void;
}

type Reporter = (phase: LiveUpdatePhase, message: string) => void;

const manifestUrl = import.meta.env.VITE_LIVE_UPDATE_MANIFEST_URL as string | undefined;
const acceptedAtKey = 'live-update-accepted-at';
let checking = false;
let lastCheckedAt = 0;

function parseManifest(value: unknown): UpdateManifest {
  if (!value || typeof value !== 'object') throw new Error('更新清单格式无效。');
  const item = value as Record<string, unknown>;
  const manifest = {
    bundleId: String(item.bundleId || ''),
    url: String(item.url || ''),
    checksum: String(item.checksum || ''),
    signature: String(item.signature || ''),
    nativeVersionCode: String(item.nativeVersionCode || ''),
    createdAt: String(item.createdAt || ''),
  };
  if (!/^[\w.-]{1,100}$/.test(manifest.bundleId)) throw new Error('更新版本号无效。');
  if (!/^[a-f\d]{64}$/.test(manifest.checksum)) throw new Error('更新校验值无效。');
  if (!/^[A-Za-z\d+/=]+$/.test(manifest.signature)) throw new Error('更新签名无效。');
  if (!/^\d+$/.test(manifest.nativeVersionCode)) throw new Error('原生版本号无效。');
  if (!Number.isFinite(Date.parse(manifest.createdAt))) throw new Error('更新时间无效。');
  return manifest;
}

async function fetchManifest(): Promise<UpdateManifest> {
  const endpoint = new URL(manifestUrl!);
  endpoint.searchParams.set('time', String(Date.now()));
  const response = await fetch(endpoint, { cache: 'no-store' });
  if (!response.ok) throw new Error(`更新检查失败：HTTP ${response.status}`);
  const manifest = parseManifest(await response.json());
  const bundleUrl = new URL(manifest.url, endpoint);
  if (bundleUrl.origin !== endpoint.origin || !bundleUrl.pathname.startsWith('/market/updates/')) {
    throw new Error('更新包地址不在允许范围内。');
  }
  manifest.url = bundleUrl.toString();
  return manifest;
}

async function checkForUpdate(report: Reporter, force = false) {
  if (checking) return;
  if (!manifestUrl) {
    report('failed', '未配置在线更新地址');
    return;
  }
  if (!force && Date.now() - lastCheckedAt < 15 * 60 * 1000) return;
  checking = true;
  lastCheckedAt = Date.now();
  report('checking', '正在检查更新…');
  try {
    const manifest = await fetchManifest();
    const acceptedAt = localStorage.getItem(acceptedAtKey);
    if (acceptedAt && Date.parse(manifest.createdAt) < Date.parse(acceptedAt)) {
      report('current', '当前已是最新版本');
      return;
    }
    const [{ versionCode }, current, next, blocked, bundles] = await Promise.all([
      LiveUpdate.getVersionCode(),
      LiveUpdate.getCurrentBundle(),
      LiveUpdate.getNextBundle(),
      LiveUpdate.getBlockedBundles(),
      LiveUpdate.getBundles(),
    ]);
    if (manifest.nativeVersionCode !== versionCode) {
      report('apk-required', '本次包含安卓能力升级，需要安装新版 APK');
      return;
    }
    if (current.bundleId === manifest.bundleId || blocked.bundleIds.includes(manifest.bundleId)) {
      report('current', '当前已是最新版本');
      return;
    }
    report('downloading', '发现新版本，正在后台更新…');
    if (!bundles.bundleIds.includes(manifest.bundleId)) {
      await LiveUpdate.downloadBundle({
        artifactType: 'zip',
        bundleId: manifest.bundleId,
        checksum: manifest.checksum,
        signature: manifest.signature,
        url: manifest.url,
      });
    }
    if (next.bundleId !== manifest.bundleId) {
      await LiveUpdate.setNextBundle({ bundleId: manifest.bundleId });
    }
    localStorage.setItem(acceptedAtKey, manifest.createdAt);
    await LiveUpdate.reload();
  } catch (error) {
    console.warn('在线更新暂不可用，将继续使用当前版本。', error);
    report('failed', error instanceof Error ? error.message : '暂时无法检查更新');
  } finally {
    checking = false;
  }
}

export function useLiveUpdate(): LiveUpdateController {
  const [phase, setPhase] = useState<LiveUpdatePhase>('idle');
  const [message, setMessage] = useState('启动后自动检查更新');
  const [checkedAt, setCheckedAt] = useState<number | null>(null);
  const report = useCallback<Reporter>((nextPhase, nextMessage) => {
    setPhase(nextPhase);
    setMessage(nextMessage);
    if (nextPhase !== 'checking' && nextPhase !== 'downloading') setCheckedAt(Date.now());
  }, []);
  const checkNow = useCallback(() => {
    if (Capacitor.getPlatform() === 'android') void checkForUpdate(report, true);
  }, [report]);

  useEffect(() => {
    if (Capacitor.getPlatform() !== 'android') return;
    let disposed = false;
    let removeListener = () => {};
    void LiveUpdate.ready()
      .catch((error) => console.warn('在线更新启动确认失败。', error))
      .finally(() => void checkForUpdate(report));
    void CapacitorApp.addListener('appStateChange', ({ isActive }) => {
      if (isActive) void checkForUpdate(report);
    }).then((listener) => {
      if (disposed) void listener.remove();
      else removeListener = () => void listener.remove();
    });
    return () => {
      disposed = true;
      removeListener();
    };
  }, [report]);

  return { phase, message, checkedAt, checkNow };
}
