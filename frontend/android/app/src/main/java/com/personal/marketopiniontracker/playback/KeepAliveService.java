package com.personal.marketopiniontracker.playback;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.personal.marketopiniontracker.MainActivity;
import com.personal.marketopiniontracker.R;

/**
 * 双模式前台保活服务：
 * - playback：转写音频播放期间（WebView 出声，服务防冻结 + WakeLock + MediaSession）
 * - app：整个应用切后台期间，保护主进程与极光推送长连接不被系统冻结回收
 */
public class KeepAliveService extends Service {
  public static final String ACTION_SET_PLAYBACK = "com.personal.marketopiniontracker.playback.SET_PLAYBACK";
  public static final String ACTION_SET_APP = "com.personal.marketopiniontracker.playback.SET_APP";
  public static final String EXTRA_ACTIVE = "active";
  private static final String CHANNEL_ID = "keep_alive";
  private static final int NOTIFICATION_ID = 4210;
  private static final long PLAYBACK_WAKE_TIMEOUT_MS = 4L * 60 * 60 * 1000;
  private static volatile boolean playbackActive = false;
  private static volatile boolean appActive = false;
  private PowerManager.WakeLock wakeLock;
  private MediaSession mediaSession;

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) {
      refreshState();
      return START_NOT_STICKY;
    }
    boolean active = intent.getBooleanExtra(EXTRA_ACTIVE, false);
    String action = intent.getAction();
    if (ACTION_SET_PLAYBACK.equals(action)) {
      playbackActive = active;
    } else if (ACTION_SET_APP.equals(action)) {
      appActive = active;
    }
    refreshState();
    return START_NOT_STICKY;
  }

  /** 依据两个模式的目标状态，启停前台服务并更新通知。 */
  private synchronized void refreshState() {
    boolean running = playbackActive || appActive;
    if (!running) {
      releaseMediaSession();
      releaseWakeLock();
      stopForeground(true);
      stopSelf();
      return;
    }
    ensureChannel();
    if (playbackActive) {
      acquireWakeLock();
      activateMediaSession();
    } else {
      releaseMediaSession();
      releaseWakeLock();
    }
    startInForeground(buildNotification());
  }

  private void startInForeground(Notification notification) {
    int type = playbackActive ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        : ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      startForeground(NOTIFICATION_ID, notification, type);
    } else {
      startForeground(NOTIFICATION_ID, notification);
    }
  }

  private void ensureChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return;
    }
    NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
      return;
    }
    NotificationChannel channel = new NotificationChannel(
        CHANNEL_ID, getString(R.string.keepalive_channel_name), NotificationManager.IMPORTANCE_LOW);
    manager.createNotificationChannel(channel);
  }

  private Notification buildNotification() {
    Intent open = new Intent(this, MainActivity.class)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    PendingIntent pending = PendingIntent.getActivity(
        this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_market)
        .setContentIntent(pending)
        .setOngoing(true)
        .setOnlyAlertOnce(true);
    if (playbackActive) {
      builder.setContentTitle(getString(R.string.keepalive_playback_title))
          .setContentText(getString(R.string.keepalive_playback_text));
    } else {
      builder.setContentTitle(getString(R.string.keepalive_app_title))
          .setContentText(getString(R.string.keepalive_app_text));
    }
    return builder.build();
  }

  private void acquireWakeLock() {
    if (wakeLock != null && wakeLock.isHeld()) {
      return;
    }
    PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
    if (manager == null) {
      return;
    }
    wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mot:transcript_audio");
    wakeLock.setReferenceCounted(false);
    wakeLock.acquire(PLAYBACK_WAKE_TIMEOUT_MS);
  }

  private void releaseWakeLock() {
    if (wakeLock != null && wakeLock.isHeld()) {
      wakeLock.release();
    }
    wakeLock = null;
  }

  /** Android 11+ 要求 mediaPlayback 前台服务必须存在活动 MediaSession。 */
  private void activateMediaSession() {
    if (mediaSession != null && mediaSession.isActive()) {
      return;
    }
    releaseMediaSession();
    mediaSession = new MediaSession(this, "transcript_audio");
    mediaSession.setCallback(new MediaSession.Callback() {});
    mediaSession.setPlaybackState(new PlaybackState.Builder()
        .setState(PlaybackState.STATE_PLAYING, 0, 1f)
        .build());
    mediaSession.setActive(true);
  }

  private void releaseMediaSession() {
    if (mediaSession == null) {
      return;
    }
    try {
      mediaSession.setActive(false);
      mediaSession.release();
    } catch (IllegalStateException ignored) {
      // 会话已被系统释放
    }
    mediaSession = null;
  }

  @Override
  public void onDestroy() {
    releaseMediaSession();
    releaseWakeLock();
    super.onDestroy();
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
    playbackActive = false;
    appActive = false;
    stopSelf();
    super.onTaskRemoved(rootIntent);
  }

  public static void setPlayback(Context context, boolean active) {
    setMode(context, ACTION_SET_PLAYBACK, active);
  }

  public static void setApp(Context context, boolean active) {
    setMode(context, ACTION_SET_APP, active);
  }

  private static void setMode(Context context, String action, boolean active) {
    Intent intent = new Intent(context, KeepAliveService.class)
        .setAction(action)
        .putExtra(EXTRA_ACTIVE, active);
    try {
      if (active) {
        ContextCompat.startForegroundService(context, intent);
      } else {
        context.startService(intent);
      }
    } catch (RuntimeException ignored) {
      // 个别 ROM 超过后台启动前台服务的宽限期，保活失败但不崩溃
    }
  }
}
