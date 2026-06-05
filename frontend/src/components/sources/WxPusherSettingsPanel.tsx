import { Save } from 'lucide-react';
import type { SetSettings } from './sourceTypes';
import type { WxPusherSettings } from '../../types';

interface Props {
  settings: WxPusherSettings;
  loading: boolean;
  setSettings: SetSettings;
  onSaveSettings: () => void;
}

export function WxPusherSettingsPanel({
  settings,
  loading,
  setSettings,
  onSaveSettings,
}: Props) {
  return (
    <section className="source-panel">
      <div className="panel-title">WxPusher 配置</div>
      <div className="form-grid two">
        <label>deviceToken<input value={settings.deviceToken} onChange={(e) => setSettings((s) => ({ ...s, deviceToken: e.target.value }))} /></label>
        <label>pushToken<input value={settings.pushToken} onChange={(e) => setSettings((s) => ({ ...s, pushToken: e.target.value }))} /></label>
        <label>deviceUuid<input value={settings.deviceUuid} onChange={(e) => setSettings((s) => ({ ...s, deviceUuid: e.target.value }))} /></label>
        <label>轮询秒数<input min={30} type="number" value={settings.pollIntervalSeconds} onChange={(e) => setSettings((s) => ({ ...s, pollIntervalSeconds: Number(e.target.value) || 60 }))} /></label>
        <label>platform<input value={settings.platform} onChange={(e) => setSettings((s) => ({ ...s, platform: e.target.value }))} /></label>
        <label>version<input value={settings.version} onChange={(e) => setSettings((s) => ({ ...s, version: e.target.value }))} /></label>
      </div>
      <div className="toggle-row">
        <label className="toggle"><input checked={settings.enablePolling} onChange={(e) => setSettings((s) => ({ ...s, enablePolling: e.target.checked }))} type="checkbox" />启用 REST 轮询</label>
        <label className="toggle"><input checked={settings.enableWebsocket} onChange={(e) => setSettings((s) => ({ ...s, enableWebsocket: e.target.checked }))} type="checkbox" />启用 WebSocket</label>
      </div>
      <button className="primary" disabled={loading} onClick={onSaveSettings} type="button">
        <Save size={16} />
        保存来源配置
      </button>
    </section>
  );
}
