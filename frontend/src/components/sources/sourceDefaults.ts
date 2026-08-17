import type { WxPusherNotifySettings, WxPusherSettings } from '../../types';
import type { BloggerDraft } from './sourceTypes';

export const defaultSettings: WxPusherSettings = {
  deviceToken: '',
  pushToken: '',
  deviceUuid: '',
  platform: 'Chrome-Windows',
  version: '1.1.1',
  pollIntervalSeconds: 60,
  enablePolling: false,
  enableWebsocket: false,
};

export const defaultNotifySettings: WxPusherNotifySettings = {
  spt: '',
  appToken: '',
  uids: '',
  topicIds: '',
};

export const emptyDraft: BloggerDraft = { id: '', bloggerName: '', aliasesText: '', enabled: true };
