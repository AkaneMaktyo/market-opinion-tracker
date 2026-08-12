package com.personal.marketopiniontracker;

import android.app.Application;
import cn.jpush.android.api.JPushInterface;

/** 初始化极光推送；别名会在注册成功后由 JpushRegistration 可靠绑定。 */
public class MyApplication extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    JPushInterface.setDebugMode(false);
    JPushInterface.setKeepLongConnInBackground(this, true);
    JPushInterface.setPowerSaveMode(this, false);
    JPushInterface.init(this);
    JpushRegistration.start(this);
  }
}
