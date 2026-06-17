import { RadioTower } from 'lucide-react';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import type { WxPusherBlogger, WxPusherMessage, WxPusherNotifySettings, WxPusherSettings, WxPusherStatus } from '../../types';
import { SourceManagerModal } from './SourceManagerModal';
import type { BloggerDraft, PositionsByKol } from './sourceTypes';

const defaultSettings: WxPusherSettings = {
  deviceToken: '',
  pushToken: '',
  deviceUuid: '',
  platform: 'Chrome-Windows',
  version: '1.1.1',
  pollIntervalSeconds: 60,
  enablePolling: false,
  enableWebsocket: false,
};

const defaultNotifySettings: WxPusherNotifySettings = {
  spt: '',
  appToken: '',
  uids: '',
  topicIds: '',
};

const emptyDraft: BloggerDraft = { id: '', bloggerName: '', aliasesText: '', enabled: true };

export function SourceManagerButton({ onChanged }: { onChanged: () => void }) {
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
        api.wxpusherMessages('FAILED', '', 30),
      ]);
      const positionEntries = await Promise.all(
        nextBloggers.map(async (blogger) => [blogger.kolId, await api.positions(blogger.kolId)] as const),
      );
      setSettings({ ...defaultSettings, ...nextSettings });
      setNotifySettings({ ...defaultNotifySettings, ...nextNotifySettings });
      setStatus(nextStatus);
      setBloggers(nextBloggers);
      setMessages(nextMessages);
      setPositionsByKol(Object.fromEntries(positionEntries));
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
      <button className="primary secondary" onClick={() => setOpen(true)} type="button">
        <RadioTower size={16} />
        来源管理
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
