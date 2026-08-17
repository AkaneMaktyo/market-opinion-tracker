import { RadioTower } from 'lucide-react';
import type { ReactNode } from 'react';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { WxPusherBlogger, WxPusherMessage, WxPusherNotifySettings, WxPusherSettings, WxPusherStatus } from '../../types';
import { SourceManagerModal } from './SourceManagerModal';
import { defaultNotifySettings, defaultSettings, emptyDraft } from './sourceDefaults';
import type { PositionsByKol, StatsByKol } from './sourceTypes';

interface Props {
  onChanged: () => void;
  trigger?: ReactNode;
  triggerClassName?: string;
}

export function SourceManagerButton({ onChanged, trigger, triggerClassName }: Props) {
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [settings, setSettings] = useState(defaultSettings);
  const [notifySettings, setNotifySettings] = useState(defaultNotifySettings);
  const [status, setStatus] = useState<WxPusherStatus | null>(null);
  const [bloggers, setBloggers] = useState<WxPusherBlogger[]>([]);
  const [messages, setMessages] = useState<WxPusherMessage[]>([]);
  const [draft, setDraft] = useState(emptyDraft);
  const [positionsByKol, setPositionsByKol] = useState<PositionsByKol>({});
  const [statsByKol, setStatsByKol] = useState<StatsByKol>({});

  useEffect(() => {
    if (open) {
      void loadAll();
    }
  }, [open]);

  async function loadAll() {
    setLoading(true);
    try {
      const [nextSettings, nextNotifySettings, nextStatus, nextBloggers, nextMessages] = await Promise.all([
        api.wxpusherSettings(),
        api.wxpusherNotifySettings(),
        api.wxpusherStatus(),
        api.wxpusherBloggers(),
        api.wxpusherOcrMessages(50),
      ]);
      const loaded = await Promise.all(
        nextBloggers.map(async (blogger) => {
          const [positions, stats] = await Promise.all([
            api.positions(blogger.kolId),
            api.positionStats(blogger.kolId),
          ]);
          return [blogger.kolId, positions, stats] as const;
        }),
      );
      setSettings({ ...defaultSettings, ...nextSettings });
      setNotifySettings({ ...defaultNotifySettings, ...nextNotifySettings });
      setStatus(nextStatus);
      setBloggers(nextBloggers);
      setMessages(nextMessages);
      setPositionsByKol(Object.fromEntries(loaded.map(([kolId, positions]) => [kolId, positions])));
      setStatsByKol(Object.fromEntries(loaded.map(([kolId, , stats]) => [kolId, stats])));
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '读取来源配置失败');
    } finally {
      setLoading(false);
    }
  }

  async function saveSettings() {
    setMessage('');
    setLoading(true);
    try {
      await Promise.all([
        api.updateWxPusherSettings(settings),
        api.updateWxPusherNotifySettings(notifySettings),
      ]);
      await loadAll();
      setMessage('WxPusher 配置已保存');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '保存配置失败');
      setLoading(false);
    }
  }

  async function saveBlogger() {
    if (!draft.bloggerName.trim()) {
      setMessage('请先填写博主名称');
      return;
    }
    setMessage('');
    setLoading(true);
    const body = { bloggerName: draft.bloggerName.trim(), aliases: splitAliases(draft.aliasesText), enabled: draft.enabled };
    try {
      if (draft.id) {
        await api.updateWxPusherBlogger({ id: draft.id, ...body });
      } else {
        await api.createWxPusherBlogger(body);
      }
      setDraft(emptyDraft);
      await loadAll();
      onChanged();
      setMessage('博主规则已保存');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '保存博主失败');
      setLoading(false);
    }
  }

  async function addPosition(kolId: string, symbol: string) {
    const value = symbol.trim().toUpperCase();
    if (!value) {
      setMessage('请先填写持仓代码');
      return;
    }
    setMessage('');
    setLoading(true);
    try {
      await api.openPosition({ kolId, symbol: value });
      await loadAll();
      onChanged();
      setMessage(`已加入当前持仓：${value}`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '添加持仓失败');
      setLoading(false);
    }
  }

  async function closePosition(id: string) {
    setMessage('');
    setLoading(true);
    try {
      await api.closePosition(id);
      await loadAll();
      onChanged();
      setMessage('已移出当前持仓');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '移出持仓失败');
      setLoading(false);
    }
  }

  async function retryMessage(id: string) {
    setMessage('');
    setLoading(true);
    try {
      await api.retryWxPusherMessage(id);
      await loadAll();
      onChanged();
      setMessage('已重新处理这条消息');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '重试失败');
      setLoading(false);
    }
  }

  return (
    <>
      <button className={triggerClassName || 'primary secondary'} onClick={() => setOpen(true)} type="button">
        {trigger || <><RadioTower size={16} />来源管理</>}
      </button>
      {open ? (
        <SourceManagerModal
          bloggers={bloggers}
          draft={draft}
          loading={loading}
          message={message}
          messages={messages}
          notifySettings={notifySettings}
          onAddPosition={(kolId, symbol) => void addPosition(kolId, symbol)}
          onClose={() => setOpen(false)}
          onClosePosition={(id) => void closePosition(id)}
          onEditBlogger={(blogger) => setDraft({ id: blogger.id, bloggerName: blogger.bloggerName, aliasesText: blogger.aliases.join(', '), enabled: blogger.enabled })}
          onRefresh={() => void loadAll()}
          onRetryMessage={(id) => void retryMessage(id)}
          onSaveBlogger={() => void saveBlogger()}
          onSaveSettings={() => void saveSettings()}
          positionsByKol={positionsByKol}
          statsByKol={statsByKol}
          setDraft={setDraft}
          setMessage={setMessage}
          setNotifySettings={setNotifySettings}
          setSettings={setSettings}
          settings={settings}
          status={status}
        />
      ) : null}
    </>
  );
}

function splitAliases(value: string) {
  return value.split(/[,，\n]/).map((item) => item.trim()).filter(Boolean);
}
