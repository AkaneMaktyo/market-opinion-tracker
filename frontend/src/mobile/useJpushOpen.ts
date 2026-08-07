import { Capacitor, registerPlugin } from '@capacitor/core';
import { useEffect } from 'react';

interface JpushPlugin {
  ready(): Promise<void>;
  addListener(
    eventName: 'notificationOpened',
    handler: (data: { messageId?: string }) => void,
  ): Promise<{ remove: () => void }>;
}

const Jpush = registerPlugin<JpushPlugin>('Jpush');
export const OPEN_OPINIONS_EVENT = 'mot:open-opinions';

/** 极光通知点击后跳转到观点页并定位消息。 */
export function useJpushOpen() {
  useEffect(() => {
    if (Capacitor.getPlatform() !== 'android') return;
    let removeListener = () => {};
    void Jpush.ready()
      .then(() => Jpush.addListener('notificationOpened', (data) => {
        window.dispatchEvent(new CustomEvent(OPEN_OPINIONS_EVENT, {
          detail: { messageId: data.messageId },
        }));
      }))
      .then((listener) => {
        removeListener = () => void listener.remove();
      })
      .catch(() => {
        // 旧版 APK 没有极光插件，忽略
      });
    return () => removeListener();
  }, []);
}
