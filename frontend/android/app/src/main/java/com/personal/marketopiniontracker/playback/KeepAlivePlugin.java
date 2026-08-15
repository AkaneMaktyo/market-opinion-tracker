package com.personal.marketopiniontracker.playback;

import android.content.Context;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** 把音频播放状态同步给保活服务；App 级后台保活由 MainActivity 生命周期原生驱动。 */
@CapacitorPlugin(name = "KeepAlive")
public class KeepAlivePlugin extends Plugin {

  @PluginMethod
  public void setPlayback(PluginCall call) {
    boolean active = Boolean.TRUE.equals(call.getBoolean("active"));
    KeepAliveService.setPlayback(getContext(), active);
    JSObject result = new JSObject();
    result.put("playback", active);
    call.resolve(result);
  }

  @Override
  protected void handleOnDestroy() {
    KeepAliveService.setPlayback(getContext(), false);
    super.handleOnDestroy();
  }
}
