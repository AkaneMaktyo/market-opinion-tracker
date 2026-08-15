package com.personal.marketopiniontracker.playback;

import android.content.Context;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

/**
 * 进程级前后台监听：整个应用切后台时启动前台保活服务，
 * 防止主进程与极光推送长连接被系统冻结回收；回到前台自动撤销。
 */
public final class BackgroundKeepAlive implements DefaultLifecycleObserver {
  private static volatile BackgroundKeepAlive instance;
  private final Context appContext;

  private BackgroundKeepAlive(Context appContext) {
    this.appContext = appContext;
  }

  public static void install(Context context) {
    if (instance != null) {
      return;
    }
    synchronized (BackgroundKeepAlive.class) {
      if (instance != null) {
        return;
      }
      instance = new BackgroundKeepAlive(context.getApplicationContext());
      ProcessLifecycleOwner.get().getLifecycle().addObserver(instance);
    }
  }

  @Override
  public void onStart(LifecycleOwner owner) {
    KeepAliveService.setApp(appContext, false);
  }

  @Override
  public void onStop(LifecycleOwner owner) {
    KeepAliveService.setApp(appContext, true);
  }
}
