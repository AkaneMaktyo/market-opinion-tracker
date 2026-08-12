package com.personal.marketopiniontracker;

import android.content.Context;
import cn.jpush.android.api.JPushMessage;
import cn.jpush.android.api.NotificationMessage;
import cn.jpush.android.service.JPushMessageReceiver;

/** 接收极光推送事件，点击通知时把消息 id 转给前端。 */
public class JpushMessageReceiver extends JPushMessageReceiver {
  @Override
  public void onRegister(Context context, String registrationId) {
    super.onRegister(context, registrationId);
    JpushRegistration.onRegistered(context, registrationId);
  }

  @Override
  public void onConnected(Context context, boolean isConnected) {
    super.onConnected(context, isConnected);
    if (isConnected) {
      JpushRegistration.onConnected(context);
    }
  }

  @Override
  public void onAliasOperatorResult(Context context, JPushMessage message) {
    super.onAliasOperatorResult(context, message);
    JpushRegistration.onAliasResult(context, message);
  }

  @Override
  public void onNotifyMessageOpened(Context context, NotificationMessage message) {
    super.onNotifyMessageOpened(context, message);
    String extras = message == null ? null : message.notificationExtras;
    String messageId = "";
    if (extras != null && !extras.isBlank()) {
      try {
        org.json.JSONObject json = new org.json.JSONObject(extras);
        messageId = json.optString("messageId", "");
      } catch (org.json.JSONException ignored) {
        // extras 不是 JSON，忽略
      }
    }
    JpushPlugin.emitOpened(context, messageId);
  }
}
