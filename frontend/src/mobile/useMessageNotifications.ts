import { Capacitor } from '@capacitor/core';
import { LocalNotifications } from '@capacitor/local-notifications';
import { useEffect, useRef } from 'react';
import { api } from '../api/client';
import type { WxPusherRecentMessage } from '../types';

const POLL_MS = 30000;
const CURSOR_KEY = 'mot:notifyCursorMs';
export const OPEN_OPINIONS_EVENT = 'mot:open-opinions';

function hashCode(value: string): number {
  let hash = 0;
  for (let i = 0; i < value.length; i++) {
    hash = (hash * 31 + value.charCodeAt(i)) | 0;
  }
  return Math.abs(hash);
}

function displayName(name: string): string {
  const match = name?.match(/^\[([^\]]+)\]/);
  return match ? `[${match[1]}]` : name || '';
}

function bodyOf(item: WxPusherRecentMessage): string {
  const text = item.summary?.trim() || item.title?.trim() || '收到一条新消息';
  return text.length > 80 ? `${text.slice(0, 80)}…` : text;
}

function fullTextOf(item: WxPusherRecentMessage): string {
  const text = item.detailText?.trim() || item.summary?.trim() || item.title?.trim() || '';
  return text
    .replace(/^WXPUSHER_IMAGE_URL=\S+$/gm, '')
    .replace(/\[图片转文字 \d+]\s*/g, '')
    .replace(/\[\/图片转文字]/g, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
    .slice(0, 2000) || bodyOf(item);
}

async function ensurePermission(): Promise<boolean> {
  const current = await LocalNotifications.checkPermissions();
  if (current.display === 'granted') return true;
  const asked = await LocalNotifications.requestPermissions();
  return asked.display === 'granted';
}

/** 安卓端全局轮询新消息，按 KOL 通知开关筛选后弹系统通知栏。 */
export function useMessageNotifications() {
  const isAndroid = Capacitor.getPlatform() === 'android';
  const cursorRef = useRef(localStorage.getItem(CURSOR_KEY) || '');

  useEffect(() => {
    if (!isAndroid) return;
    let disposed = false;
    let running = false;

    void ensurePermission();
    void LocalNotifications.addListener('localNotificationActionPerformed', (action) => {
      const messageId = action.notification.extra?.messageId as string | undefined;
      window.dispatchEvent(new CustomEvent(OPEN_OPINIONS_EVENT, { detail: { messageId } }));
    });

    async function poll() {
      if (disposed || running) return;
      running = true;
      try {
        const [messages, bloggers] = await Promise.all([
          api.wxpusherRecentMessages(50),
          api.wxpusherBloggers(),
        ]);
        const switchByKol = new Map(bloggers.map((item) => [item.kolId, item.notifyEnabled]));
        const sorted = [...messages].sort(
          (left, right) => Date.parse(left.messageTime) - Date.parse(right.messageTime),
        );
        let cursor = cursorRef.current;
        let notified = false;
        for (const item of sorted) {
          const at = Date.parse(item.messageTime);
          if (!cursor) {
            cursor = String(at);
            continue;
          }
          if (at <= Number(cursor)) continue;
          cursor = String(at);
          if (switchByKol.get(item.kolId) === false) continue;
          await LocalNotifications.schedule({
            notifications: [{
              id: hashCode(item.id) || 1,
              title: displayName(item.bloggerName) || '新观点消息',
              body: bodyOf(item),
              largeBody: fullTextOf(item),
              smallIcon: 'ic_stat_market',
              extra: { messageId: item.id },
            }],
          });
          notified = true;
        }
        if (cursor !== cursorRef.current) {
          cursorRef.current = cursor;
          localStorage.setItem(CURSOR_KEY, cursor);
        }
      } catch {
        // 网络或通知失败，等待下一轮重试
      } finally {
        running = false;
      }
    }

    const timer = window.setInterval(() => void poll(), POLL_MS);
    return () => {
      disposed = true;
      window.clearInterval(timer);
    };
  }, [isAndroid]);
}
