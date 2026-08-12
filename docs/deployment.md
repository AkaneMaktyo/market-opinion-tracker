# 云服务器部署说明

当前部署目标：

- Web 地址：`http://103.236.98.149:8888/market/`
- 后端健康检查：`http://103.236.98.149:8888/market/api/health`
- SSH：`103.236.98.149:29453`
- 应用目录：`/opt/market-opinion-tracker`
- 前端目录：`/var/www/market-opinion-tracker/market`
- 环境变量：`/etc/market-opinion-tracker/app.env`
- 数据库：云端 MySQL，`market_opinion_tracker`
- systemd 服务：`market-opinion-tracker`

## 端口约定

这台服务器是 NAT 映射型，公网只固定使用：

- `29453 -> 22`：SSH 和自动部署
- `8888 -> 8888`：两个系统共用的 Web 入口

不要再新增 `18080`。MySQL 不开放公网。

## 自动部署

发布被拆分为三条独立流水线，避免某一类构建失败拖累其他服务：

- `Deploy to cloud`：只发布后端、网页和安卓热更新包。
- `Build and publish Android APK`：只在安卓原生代码或安卓版本文件变化时构建并发布 APK。构建使用 GitHub 托管的 Android 环境，部署机只负责上传。
- `YouTube OSS fetch`：按小时独立抓取 YouTube 音频，不再随普通代码提交重复运行。

安卓版本只有一个来源：`deploy/mobile/android-version.json`。需要发新版 APK 时先修改这个文件，网页热更新和 APK 清单会读取同一个版本号。

每条流水线都设置了并发锁和发布后的线上校验。单条失败时只重跑该流程，不需要重复部署已经成功的部分。

需要在 GitHub 仓库 Secrets 中配置：

- `KMT_SSH_HOST`
- `KMT_SSH_PORT`
- `KMT_SSH_USER`
- `KMT_SSH_PASSWORD`
- `JPUSH_APP_KEY`
- `ANDROID_RELEASE_JKS_B64`
- `ANDROID_SIGNING_PROPERTIES_B64`

部署机无法直连 GitHub 时，会优先使用 `DEPLOY_HTTP_PROXY` / `DEPLOY_HTTPS_PROXY`，未设置时回退到本机 `127.0.0.1:7897`。必要的 Secret 缺失会直接失败并指出问题，避免产生“显示成功但实际未发布”的假成功。
