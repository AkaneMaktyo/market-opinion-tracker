package com.personal.marketopiniontracker;

import android.app.Application;
import cn.jpush.android.api.JPushInterface;

/** 极光推送初始化，并绑定固定别名供服务端推送。 */
public class MyApplication extends Application {
  private static final String PUSH_ALIAS = "market_tracker_user";

  @Override
  public void onCreate() {
    super.onCreate();
    JPushInterface.setDebugMode(false);
    JPushInterface.init(this);
    JPushInterface.setAlias(this, 0, PUSH_ALIAS);
  }
}
