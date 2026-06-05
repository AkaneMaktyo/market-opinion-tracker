import type { Dispatch, SetStateAction } from 'react';
import type { KolPosition } from '../../positionTypes';
import type { WxPusherSettings } from '../../types';

export interface BloggerDraft {
  id: string;
  bloggerName: string;
  aliasesText: string;
  enabled: boolean;
}

export type PositionsByKol = Record<string, KolPosition[]>;

export type SetSettings = Dispatch<SetStateAction<WxPusherSettings>>;
export type SetDraft = Dispatch<SetStateAction<BloggerDraft>>;
