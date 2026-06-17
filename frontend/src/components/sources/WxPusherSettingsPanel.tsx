import { Save } from 'lucide-react';
import type { SetNotifySettings, SetSettings } from './sourceTypes';
import type { WxPusherNotifySettings, WxPusherSettings } from '../../types';

interface Props {
  settings: WxPusherSettings;
  notifySettings: WxPusherNotifySettings;
  loading: boolean;
  setSettings: SetSettings;
  setNotifySettings: SetNotifySettings;
  onSaveSettings: () => void;
}

export function WxPusherSettingsPanel({
  settings,
  notifySettings,
  loading,
  setSettings,
  setNotifySettings,
  onSaveSettings,
}: Props) {
  return (
    <section className="source-panel">
      <div className="panel-title">WxPusher 配置</div>
      <div className="form-grid two">
        <Field label="deviceToken" value={settings.deviceToken} onChange={(value) => setSettings((s) => ({ ...s, deviceToken: value }))} />
        <Field label="pushToken" value={settings.pushToken} onChange={(value) => setSettings((s) => ({ ...s, pushToken: value }))} />
        <Field label="deviceUuid" value={settings.deviceUuid} onChange={(value) => setSettings((s) => ({ ...s, deviceUuid: value }))} />
        <label>轮询秒数<input min={30} type="number" value={settings.pollIntervalSeconds} onChange={(e) => setSettings((s) => ({ ...s, pollIntervalSeconds: Number(e.target.value) || 60 }))} /></label>
        <Field label="platform" value={settings.platform} onChange={(value) => setSettings((s) => ({ ...s, platform: value }))} />
        <Field label="version" value={settings.version} onChange={(value) => setSettings((s) => ({ ...s, version: value }))} />
      </div>
      <div className="toggle-row">
        <label className="toggle"><input checked={settings.enablePolling} onChange={(e) => setSettings((s) => ({ ...s, enablePolling: e.target.checked }))} type="checkbox" />启用 REST 轮询</label>
        <label className="toggle"><input checked={settings.enableWebsocket} onChange={(e) => setSettings((s) => ({ ...s, enableWebsocket: e.target.checked }))} type="checkbox" />启用 WebSocket</label>
      </div>
      <div className="panel-title" style={{ marginTop: 18 }}>转写完成推送</div>
      <div className="form-grid two">
        <Field label="SPT" placeholder="有 SPT 的话优先填这里" value={notifySettings.spt} onChange={(value) => setNotifySettings((s) => ({ ...s, spt: value }))} />
        <Field label="AppToken" placeholder="或者填 WxPusher AppToken" value={notifySettings.appToken} onChange={(value) => setNotifySettings((s) => ({ ...s, appToken: value }))} />
        <Field label="UIDS" placeholder="多个 UID 用逗号或空格分隔" value={notifySettings.uids} onChange={(value) => setNotifySettings((s) => ({ ...s, uids: value }))} />
        <Field label="Topic IDs" placeholder="多个 topic id 用逗号或空格分隔" value={notifySettings.topicIds} onChange={(value) => setNotifySettings((s) => ({ ...s, topicIds: value }))} />
      </div>
      <button className="primary" disabled={loading} onClick={onSaveSettings} type="button">
        <Save size={16} />
        保存来源配置
      </button>
    </section>
  );
}

function Field({
  label,
  value,
  placeholder = '',
  onChange,
}: {
  label: string;
  value: string;
  placeholder?: string;
  onChange: (value: string) => void;
}) {
  return <label>{label}<input placeholder={placeholder} value={value} onChange={(e) => onChange(e.target.value)} /></label>;
}
