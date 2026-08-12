package com.personal.marketopiniontracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import cn.jpush.android.api.JPushInterface;
import cn.jpush.android.api.JPushMessage;

/** 在极光注册完成后绑定别名，并对网络/时序失败执行退避重试。 */
final class JpushRegistration {
  private static final String TAG = "JpushRegistration";
  private static final String PREFS = "mot_jpush_registration";
  private static final String KEY_RID = "bound_registration_id";
  private static final String KEY_ALIAS = "bound_alias";
  private static final String KEY_ERROR = "last_error";
  private static final long[] RETRY_DELAYS_MS = {
      8_000L, 15_000L, 30_000L, 60_000L, 120_000L, 300_000L, 600_000L
  };
  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  private static final Object LOCK = new Object();
  private static int retryIndex;
  private static int pendingSequence;
  private static boolean requestInFlight;

  private JpushRegistration() {
  }

  static void start(Context context) {
    synchronized (LOCK) {
      retryIndex = 0;
      requestInFlight = false;
    }
    schedule(context, RETRY_DELAYS_MS[0]);
  }

  static void onRegistered(Context context, String registrationId) {
    if (registrationId == null || registrationId.isBlank()) {
      scheduleRetry(context, "极光注册 ID 为空");
      return;
    }
    synchronized (LOCK) {
      retryIndex = 0;
      requestInFlight = false;
    }
    schedule(context, 0L);
  }

  static void onConnected(Context context) {
    if (!isReady(context)) {
      schedule(context, 1_000L);
    }
  }

  static void onAliasResult(Context context, JPushMessage message) {
    if (message == null) {
      scheduleRetry(context, "极光别名回调为空");
      return;
    }
    synchronized (LOCK) {
      if (message.getSequence() != pendingSequence) {
        return;
      }
      requestInFlight = false;
    }
    if (message.getErrorCode() == 0) {
      String registrationId = registrationId(context);
      preferences(context).edit()
          .putString(KEY_RID, registrationId)
          .putString(KEY_ALIAS, alias(context))
          .remove(KEY_ERROR)
          .apply();
      synchronized (LOCK) {
        retryIndex = 0;
      }
      Log.i(TAG, "极光别名绑定成功，registrationId=" + masked(registrationId));
      return;
    }
    scheduleRetry(context, aliasError(message.getErrorCode()));
  }

  static boolean isReady(Context context) {
    String registrationId = registrationId(context);
    SharedPreferences prefs = preferences(context);
    return !registrationId.isBlank()
        && registrationId.equals(prefs.getString(KEY_RID, ""))
        && alias(context).equals(prefs.getString(KEY_ALIAS, ""));
  }

  static boolean isRegistered(Context context) {
    return !registrationId(context).isBlank();
  }

  static String lastError(Context context) {
    return preferences(context).getString(KEY_ERROR, "");
  }

  private static void attempt(Context context) {
    Context appContext = context.getApplicationContext();
    if (isReady(appContext)) {
      return;
    }
    String registrationId = registrationId(appContext);
    if (registrationId.isBlank()) {
      scheduleRetry(appContext, "等待极光注册完成");
      return;
    }
    int sequence;
    synchronized (LOCK) {
      if (requestInFlight) {
        return;
      }
      requestInFlight = true;
      pendingSequence = nextSequence();
      sequence = pendingSequence;
    }
    JPushInterface.setAlias(appContext, sequence, alias(appContext));
    MAIN.postDelayed(() -> onAliasTimeout(appContext, sequence), 30_000L);
  }

  private static void onAliasTimeout(Context context, int sequence) {
    synchronized (LOCK) {
      if (!requestInFlight || pendingSequence != sequence) {
        return;
      }
      requestInFlight = false;
    }
    scheduleRetry(context, "极光别名绑定超时");
  }

  private static void scheduleRetry(Context context, String error) {
    preferences(context).edit().putString(KEY_ERROR, error).apply();
    long delay;
    synchronized (LOCK) {
      delay = RETRY_DELAYS_MS[Math.min(retryIndex, RETRY_DELAYS_MS.length - 1)];
      retryIndex++;
    }
    Log.w(TAG, error + "，将在 " + delay + "ms 后重试");
    schedule(context, delay);
  }

  private static void schedule(Context context, long delayMs) {
    Context appContext = context.getApplicationContext();
    MAIN.postDelayed(() -> attempt(appContext), delayMs);
  }

  private static SharedPreferences preferences(Context context) {
    return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  private static String registrationId(Context context) {
    String value = JPushInterface.getRegistrationID(context);
    return value == null ? "" : value.trim();
  }

  private static String alias(Context context) {
    return context.getString(R.string.jpush_alias).trim();
  }

  private static int nextSequence() {
    return (int) (System.currentTimeMillis() & 0x7fffffff);
  }

  private static String aliasError(int code) {
    if (code == 6017 || code == 6027) {
      return "极光别名绑定设备已达上限（错误码 " + code + "）";
    }
    return "极光别名绑定失败（错误码 " + code + "）";
  }

  private static String masked(String value) {
    return value.length() <= 8 ? value : value.substring(0, 4) + "…" + value.substring(value.length() - 4);
  }
}
