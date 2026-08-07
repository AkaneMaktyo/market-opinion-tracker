package com.personal.marketopiniontracker;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** 把极光通知点击事件转发给 WebView。 */
@CapacitorPlugin(name = "Jpush")
public class JpushPlugin extends Plugin {
  private static JpushPlugin instance;

  @Override
  public void load() {
    instance = this;
  }

  public static void emitOpened(String messageId) {
    JpushPlugin plugin = instance;
    if (plugin == null) {
      return;
    }
    JSObject data = new JSObject();
    data.put("messageId", messageId == null ? "" : messageId);
    plugin.notifyListeners("notificationOpened", data, true);
  }

  @PluginMethod
  public void ready(PluginCall call) {
    call.resolve();
  }
}
