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

推送到 `main` 后，GitHub Actions 会构建前后端并通过 SSH 上传服务器。

需要在 GitHub 仓库 Secrets 中配置：

- `KMT_SSH_HOST`
- `KMT_SSH_PORT`
- `KMT_SSH_USER`
- `KMT_SSH_PASSWORD`

如果 Secrets 尚未配置，工作流会跳过部署，不会报失败。
