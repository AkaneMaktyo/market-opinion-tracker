import { Capacitor, registerPlugin } from '@capacitor/core';
import { LocalNotifications } from '@capacitor/local-notifications';
import { useEffect } from 'react';

interface JpushPlugin {
  ready(): Promise<{ messageId?: string }>;
  status(): Promise<AndroidPushStatus>;
  openNotificationSettings(): Promise<void>;
  addListener(
    eventName: 'notificationOpened',
    handler: (data: { messageId?: string }) => void,
  ): Promise<{ remove: () => void }>;
}

const Jpush = registerPlugin<JpushPlugin>('Jpush');
export const OPEN_OPINIONS_EVENT = 'mot:open-opinions';

export interface AndroidPushStatus {
  registered: boolean;
  aliasBound: boolean;
  notificationsEnabled: boolean;
  error?: string;
}

const unavailableStatus: AndroidPushStatus = {
  registered: false,
  aliasBound: false,
  notificationsEnabled: false,
};

export async function readAndroidPushStatus(): Promise<AndroidPushStatus> {
  if (Capacitor.getPlatform() !== 'android') return unavailableStatus;
  return Jpush.status();
}

export async function enableAndroidNotifications(): Promise<boolean> {
  if (Capacitor.getPlatform() !== 'android') return false;
  const result = await LocalNotifications.requestPermissions();
  return result.display === 'granted';
}

export async function openAndroidNotificationSettings(): Promise<void> {
  if (Capacitor.getPlatform() !== 'android') return;
  await Jpush.openNotificationSettings();
}

function dispatchOpen(messageId?: string) {
  window.dispatchEvent(new CustomEvent(OPEN_OPINIONS_EVENT, {
    detail: { messageId },
  }));
}

async function requestInitialNotificationPermission() {
  const current = await LocalNotifications.checkPermissions();
  if (current.display !== 'granted') {
    await LocalNotifications.requestPermissions();
  }
}

/** 极光通知点击后跳转到观点页并定位消息。 */
export function useJpushOpen() {
  useEffect(() => {
    if (Capacitor.getPlatform() !== 'android') return;
    let removeListener = () => {};
    void Jpush.addListener('notificationOpened', (data) => dispatchOpen(data.messageId))
      .then((listener) => {
        removeListener = () => void listener.remove();
        return Jpush.ready();
      })
      .then((launchNotification) => {
        if (launchNotification.messageId) dispatchOpen(launchNotification.messageId);
      })
      .catch(() => {
        // 旧版 APK 没有极光插件，忽略
      });
    void requestInitialNotificationPermission().catch(() => {
      // 权限状态仍可在“我的 > 新消息通知”中手动修复
    });
    return () => removeListener();
  }, []);
}
