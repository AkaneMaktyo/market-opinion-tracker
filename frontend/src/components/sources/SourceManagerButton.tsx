import { RadioTower } from 'lucide-react';
import { useEffect, useState } from 'react';
import { api } from '../../api/client';
import type {
  WxPusherBlogger,
  WxPusherMessage,
  WxPusherSettings,
  WxPusherStatus,
} from '../../types';
import { SourceManagerModal } from './SourceManagerModal';

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

const emptyDraft = { id: '', bloggerName: '', aliasesText: '', enabled: true };

export function SourceManagerButton({ onChanged }: { onChanged: () => void }) {
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState('');
  const [settings, setSettings] = useState<WxPusherSettings>(defaultSettings);
  const [status, setStatus] = useState<WxPusherStatus | null>(null);
  const [bloggers, setBloggers] = useState<WxPusherBlogger[]>([]);
  const [messages, setMessages] = useState<WxPusherMessage[]>([]);
  const [draft, setDraft] = useState(emptyDraft);

  async function loadAll() {
    setLoading(true);
    try {
      const [nextSettings, nextStatus, nextBloggers, nextMessages] = await Promise.all([
        api.wxpusherSettings(),
        api.wxpusherStatus(),
        api.wxpusherBloggers(),
        api.wxpusherMessages('FAILED', '', 30),
      ]);
      setSettings({ ...defaultSettings, ...nextSettings });
      setStatus(nextStatus);
      setBloggers(nextBloggers);
      setMessages(nextMessages);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '读取来源配置失败');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (open) {
      void loadAll();
    }
  }, [open]);

  async function saveSettings() {
    setMessage('');
    setLoading(true);
    try {
      await api.updateWxPusherSettings(settings);
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
    const body = {
      bloggerName: draft.bloggerName.trim(),
      aliases: splitAliases(draft.aliasesText),
      enabled: draft.enabled,
    };
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

  function editBlogger(blogger: WxPusherBlogger) {
    setDraft({
      id: blogger.id,
      bloggerName: blogger.bloggerName,
      aliasesText: blogger.aliases.join(', '),
      enabled: blogger.enabled,
    });
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
          onClose={() => setOpen(false)}
          onEditBlogger={editBlogger}
          onRefresh={() => void loadAll()}
          onRetryMessage={(id) => void retryMessage(id)}
          onSaveBlogger={() => void saveBlogger()}
          onSaveSettings={() => void saveSettings()}
          setDraft={setDraft}
          setMessage={setMessage}
          setSettings={setSettings}
          settings={settings}
          status={status}
        />
      ) : null}
    </>
  );
}

function splitAliases(value: string) {
  return value
    .split(/[,\n，]/)
    .map((item) => item.trim())
    .filter(Boolean);
}
