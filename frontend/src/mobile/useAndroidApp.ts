import { App as CapacitorApp } from '@capacitor/app';
import { Capacitor } from '@capacitor/core';
import { useEffect } from 'react';
import { goToDashboard } from '../hashRoute';

export function isAndroidEnvironment() {
  return Capacitor.getPlatform() === 'android'
    || new URLSearchParams(window.location.search).get('platform') === 'android';
}

function closeTopLayer(): boolean {
  const backdrops = document.querySelectorAll<HTMLElement>('.modal-backdrop');
  const backdrop = backdrops.item(backdrops.length - 1);
  if (backdrop) {
    backdrop.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    return true;
  }
  if (document.querySelector('.chart-stage.fullscreen')) {
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    return true;
  }
  return false;
}

export function useAndroidApp() {
  const isAndroid = isAndroidEnvironment();
  useEffect(() => {
    if (!isAndroid) return;
    const isPreview = Capacitor.getPlatform() !== 'android';
    document.body.classList.add('native-android');
    if (isPreview) {
      return () => document.body.classList.remove('native-android');
    }
    let disposed = false;
    let removeListener = () => {};

    void CapacitorApp.addListener('backButton', async ({ canGoBack }) => {
      if (closeTopLayer()) return;
      if (window.location.hash === '#/youtube') {
        goToDashboard();
        return;
      }
      if (canGoBack) {
        window.history.back();
        return;
      }
      await CapacitorApp.minimizeApp();
    }).then((listener) => {
      if (disposed) {
        void listener.remove();
        return;
      }
      removeListener = () => void listener.remove();
    });

    return () => {
      disposed = true;
      removeListener();
      document.body.classList.remove('native-android');
    };
  }, [isAndroid]);
  return isAndroid;
}
