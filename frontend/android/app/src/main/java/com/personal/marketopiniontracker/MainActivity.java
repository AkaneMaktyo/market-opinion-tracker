package com.personal.marketopiniontracker;

import android.content.Intent;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(InstallApkPlugin.class);
    registerPlugin(JpushPlugin.class);
    super.onCreate(savedInstanceState);
    captureNotification(getIntent());
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    captureNotification(intent);
  }

  private void captureNotification(Intent intent) {
    if (intent == null) {
      return;
    }
    JpushPlugin.emitOpened(this, intent.getStringExtra("messageId"));
  }
}
