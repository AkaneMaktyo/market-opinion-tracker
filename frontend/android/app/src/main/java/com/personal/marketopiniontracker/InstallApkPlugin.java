package com.personal.marketopiniontracker;

import android.content.Intent;
import android.net.Uri;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 下载 APK 并调起系统安装器。 */
@CapacitorPlugin(name = "InstallApk")
public class InstallApkPlugin extends Plugin {
  private static final String AUTHORITY = "com.personal.marketopiniontracker.fileprovider";
  private static final String MIME_APK = "application/vnd.android.package-archive";

  @PluginMethod
  public void install(PluginCall call) {
    String url = call.getString("url");
    String fileName = call.getString("fileName");
    if (url == null || url.isBlank()) {
      call.reject("下载地址不能为空");
      return;
    }
    String safeName = sanitize(fileName);
    getBridge().execute(() -> {
      try {
        File target = download(url, safeName, call);
        if (call.isSaved()) {
          openInstaller(target, call);
        }
      } catch (Exception error) {
        if (call.isSaved()) {
          call.reject("下载或安装失败: " + error.getMessage());
        }
      }
    });
  }

  private File download(String urlString, String fileName, PluginCall call) throws Exception {
    File dir = new File(getContext().getCacheDir(), "app-updates");
    if (!dir.exists() && !dir.mkdirs()) {
      throw new IllegalStateException("无法创建缓存目录");
    }
    File target = new File(dir, fileName);
    HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
    connection.setConnectTimeout(15000);
    connection.setReadTimeout(60000);
    connection.setInstanceFollowRedirects(true);
    try (InputStream input = connection.getInputStream();
         FileOutputStream output = new FileOutputStream(target)) {
      byte[] buffer = new byte[8192];
      int read;
      long downloaded = 0;
      long total = connection.getContentLengthLong();
      while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
        downloaded += read;
        if (downloaded % (512 * 1024) == 0) {
          JSObject progress = new JSObject();
          progress.put("downloaded", downloaded);
          progress.put("total", total);
          notifyListeners("progress", progress, false);
        }
      }
    } finally {
      connection.disconnect();
    }
    return target;
  }

  private void openInstaller(File target, PluginCall call) {
    Uri uri = FileProvider.getUriForFile(getContext(), AUTHORITY, target);
    Intent intent = new Intent(Intent.ACTION_VIEW);
    intent.setDataAndType(uri, MIME_APK);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    getContext().startActivity(intent);
    JSObject result = new JSObject();
    result.put("installed", true);
    call.resolve(result);
  }

  private static String sanitize(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return "update.apk";
    }
    return fileName.replaceAll("[^A-Za-z0-9._-]", "_");
  }
}
