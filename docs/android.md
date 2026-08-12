# 安卓应用说明

项目使用 Capacitor 将现有 React 前端打包为安卓应用，后端继续运行在云服务器。

## 当前配置

- 应用名称：`美股观点追踪`
- 应用 ID：`com.personal.marketopiniontracker`
- 版本以 `deploy/mobile/android-version.json` 为准，发布流水线和热更新清单共用这一处配置。
- 最低系统：Android 7（API 24）
- 目标系统：Android 16（API 36）
- 生产接口：`http://103.236.98.149:8888/market/api`
- 更新清单：`http://103.236.98.149:8888/market/live-update.json`

## 手机端交互

- 底部固定为“概览、观点、快速添加、转写、我的”五个入口。
- 页面只有一个纵向滚动容器；切换入口时自动回到顶部，避免旧滚动位置造成卡住错觉。
- 长转写列表使用延迟渲染，底部导航与快捷操作始终保持可点击。
- “我的 → 数据与更新”可以查看自动更新状态并手动检查更新。
- 本地开发时访问 `?platform=android` 可以预览安卓手机布局。

## 已安装的构建环境

- Microsoft OpenJDK 21 LTS
- Android SDK Platform 36
- Android Build Tools 36.0.0
- Android Platform Tools
- Gradle 8.14.3

`ANDROID_HOME` 和 `ANDROID_SDK_ROOT` 已写入当前 Windows 用户环境变量。JDK 21 仅在安卓构建命令中选用，不会覆盖其他项目使用的 JDK。

## 生成 APK

```powershell
cd D:\_code\personal\market-opinion-tracker\frontend
npm run android:sync

$jdk = Get-ChildItem 'C:\Program Files\Microsoft' -Directory -Filter 'jdk-21*' |
  Sort-Object LastWriteTime -Descending | Select-Object -First 1
$env:JAVA_HOME = $jdk.FullName
$env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
cd android
.\gradlew.bat :app:assembleDebug
```

生成位置：`frontend/android/app/build/outputs/apk/debug/app-debug.apk`。

本次正式签名交付包位于：`artifacts/market-opinion-tracker-1.0.apk`。调试包只用于开发测试，请优先安装正式包。

## 安装到手机

打开手机的“允许安装未知应用”，把 APK 发送到手机后点开安装。USB 调试已连接时也可执行：

```powershell
adb install -r D:\_code\personal\market-opinion-tracker\artifacts\market-opinion-tracker-1.0.apk
```

## 在线更新机制

普通 React 页面、样式和业务功能不再需要重新传 APK：

1. `main` 分支的现有发布流程同时构建网站和安卓更新包。
2. 更新包使用独立 RSA 私钥签名，应用使用内置公钥验证。
3. 应用启动时检查更新，下载成功后自动切换到新版本。
4. 新版本若未在 10 秒内正常启动，应用会回退到 APK 内置版本，并阻止再次加载故障版本。

只有新增安卓权限、升级 Capacitor 插件、修改 Java/Kotlin 或变更应用图标等原生能力时，才需要重新生成并安装 APK。

在线更新私钥保存在当前用户的 `C:\Users\<用户名>\.market-opinion-tracker\live-update-private.pem`，GitHub Actions 使用加密变量 `LIVE_UPDATE_PRIVATE_KEY_B64`。APK 正式签名保存在同目录下的 `android-release.jks` 和 `android-signing.properties`。这些文件都不进入 Git；一旦丢失，已安装应用将无法接受对应类型的升级，因此必须备份整个目录。

## 网络说明

当前云端入口仍是 HTTP。安卓工程只对 `103.236.98.149` 放行明文访问，更新包再通过 RSA 签名防止被替换。服务器升级到 HTTPS 后，应同步修改 `frontend/.env.android`，并移除安卓清单中的明文网络例外。

## 正式发布说明

当前产物是长期密钥签名的 release APK，可直接侧载。若以后上架应用商店，应继续使用同一正式签名密钥构建 APK 或 AAB；签名文件和密码不可提交到 Git。
