package com.personal.marketopiniontracker;

import android.content.Context;
import android.content.SharedPreferences;
import cn.jpush.android.api.JPushInterface;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** 把极光通知点击事件转发给 WebView。 */
@CapacitorPlugin(name = "Jpush")
public class JpushPlugin extends Plugin {
  private static final String PREFS = "mot_jpush_open";
  private static final String KEY_PENDING_ID = "pending_message_id";
  private static final String KEY_LAST_ID = "last_message_id";
  private static final String KEY_LAST_AT = "last_message_at";
  private static final long DUPLICATE_WINDOW_MS = 2_000L;
  private static volatile JpushPlugin instance;

  @Override
  public void load() {
    super.load();
    instance = this;
  }

  @Override
  protected void handleOnDestroy() {
    if (instance == this) {
      instance = null;
    }
    super.handleOnDestroy();
  }

  public static synchronized void emitOpened(Context context, String messageId) {
    String normalized = messageId == null ? "" : messageId.trim();
    if (normalized.isBlank()) {
      return;
    }
    SharedPreferences prefs = preferences(context);
    long now = System.currentTimeMillis();
    if (normalized.equals(prefs.getString(KEY_LAST_ID, ""))
        && now - prefs.getLong(KEY_LAST_AT, 0L) < DUPLICATE_WINDOW_MS) {
      return;
    }
    prefs.edit().putString(KEY_PENDING_ID, normalized).apply();
    JpushPlugin plugin = instance;
    if (plugin == null) {
      return;
    }
    JSObject data = new JSObject();
    data.put("messageId", normalized);
    plugin.notifyListeners("notificationOpened", data, true);
    markDelivered(prefs, normalized, now);
  }

  @PluginMethod
  public void ready(PluginCall call) {
    JSObject result = new JSObject();
    synchronized (JpushPlugin.class) {
      SharedPreferences prefs = preferences(getContext());
      String pendingId = prefs.getString(KEY_PENDING_ID, "");
      if (!pendingId.isBlank()) {
        result.put("messageId", pendingId);
        markDelivered(prefs, pendingId, System.currentTimeMillis());
      }
    }
    call.resolve(result);
  }

  @PluginMethod
  public void status(PluginCall call) {
    JPushInterface.triggerNotificationStateCheck(getContext());
    JSObject result = new JSObject();
    result.put("registered", JpushRegistration.isRegistered(getContext()));
    result.put("aliasBound", JpushRegistration.isReady(getContext()));
    result.put("notificationsEnabled", JPushInterface.isNotificationEnabled(getContext()) == 1);
    result.put("error", JpushRegistration.lastError(getContext()));
    call.resolve(result);
  }

  @PluginMethod
  public void openNotificationSettings(PluginCall call) {
    JPushInterface.goToAppNotificationSettings(getContext());
    call.resolve();
  }

  private static SharedPreferences preferences(Context context) {
    return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  private static void markDelivered(SharedPreferences prefs, String messageId, long deliveredAt) {
    prefs.edit()
        .remove(KEY_PENDING_ID)
        .putString(KEY_LAST_ID, messageId)
        .putLong(KEY_LAST_AT, deliveredAt)
        .apply();
  }
}
