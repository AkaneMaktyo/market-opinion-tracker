import type { Dispatch, SetStateAction } from 'react';
import type { PositionStats, PositionView } from '../../positionTypes';
import type { WxPusherNotifySettings, WxPusherSettings } from '../../types';

export interface BloggerDraft {
  id: string;
  bloggerName: string;
  aliasesText: string;
  enabled: boolean;
}

export type PositionsByKol = Record<string, PositionView[]>;
export type StatsByKol = Record<string, PositionStats>;

export type SetSettings = Dispatch<SetStateAction<WxPusherSettings>>;
export type SetNotifySettings = Dispatch<SetStateAction<WxPusherNotifySettings>>;
export type SetDraft = Dispatch<SetStateAction<BloggerDraft>>;
