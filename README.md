# Market Opinion Tracker

用于记录直播观点、管理品种、保存结构化交易判断，并结合 K 线做复盘的本地项目。

## 现在能做什么

- 按 `KOL` 管理不同来源的直播记录
- 粘贴 JSON 后预览，再批量导入观点
- 按品种查看历史观点、关键价位和复盘结果
- 展示本地行情 K 线，并在图上标注多空观点
- 维护品种信息：重命名、归并、分组
- 为品种显示图标，支持自动抓取和手动覆盖
- 对单个品种或全部品种做历史行情回填

## 最近这次改动

- 首页标题补了图标和品牌标识
- 左侧品种栏增加“管理”入口
- 新增品种管理中心，可搜索并进入单个品种管理
- 单个品种支持：
  - 重命名代码和名称
  - 把错误代码归并到正确代码
  - 设置或新建分组
  - 设置图标地址
- 品种图标支持多级回退：
  - 手动图标地址
  - TradingView Logo
  - FMP 股票图片
  - 首字母占位图标
- 前端结构做了拆分，避免单文件过长
- `vite` 启动脚本改为当前环境可直接运行的方式

## 技术栈

- 后端：Java 17、Spring Boot、Spring JDBC、MySQL
- 前端：React、TypeScript、Vite、TradingView Lightweight Charts

## 目录概览

```text
backend/
  src/main/java/com/personal/tracker/
    config/       异常处理、跨域配置
    domain/       领域对象
    repository/   JDBC 数据访问
    service/      业务逻辑、行情同步、JSON 解析
    web/          HTTP 接口
  src/main/resources/
    application.yml
    schema.sql

frontend/
  src/
    api/          前端接口封装
    components/
      brand/      品牌头部
      instruments/ 品种侧栏、管理中心、图标
    styles/       拆分后的样式文件
```

## 启动方式

### 1. 准备数据库

后端必须显式配置线上 MySQL，不再提供本地库兜底。服务器读取
`/etc/market-opinion-tracker/app.env`；本机开发可在 `backend/.env`
配置 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME` 和
`SPRING_DATASOURCE_PASSWORD`。

本机访问线上库时先建立 SSH 隧道，再让 `SPRING_DATASOURCE_URL` 指向
隧道端口，例如 `127.0.0.1:13306/market_opinion_tracker`。

### 2. 启动后端

可先执行：

```powershell
powershell -ExecutionPolicy Bypass -File deploy/start-db-tunnel.ps1 -SshPassword "<你的 SSH 密码>"
```

```powershell
cd D:\_code\personal\market-opinion-tracker\backend
mvn -DskipTests package
java -jar target\market-opinion-tracker-0.1.0.jar
```

默认地址：`http://localhost:8080`

健康检查：

```text
GET /api/health
```

### 3. 启动前端

```powershell
cd D:\_code\personal\market-opinion-tracker\frontend
npm install
npm run dev
```

默认地址：`http://localhost:5173`

## 关键交互路径

### 导入观点

1. 先选择或创建 `KOL`
2. 粘贴 JSON
3. 在预览里修正代码、方向、文本
4. 提交保存

### 管理品种

1. 在左侧品种栏点“管理”
2. 搜索目标品种
3. 进入单个品种管理弹窗
4. 执行重命名、归并、分组或图标设置

### 归并错误代码

适合把 `TSMC` 这类错误代码归并到 `TSM`：

1. 打开 `TSMC` 的管理弹窗
2. 切到“归并”
3. 选择目标品种 `TSM`
4. 确认后，观点和行情数据会迁移到目标品种

## 当前默认端口

- 前端：`5173`
- 后端：`8080`
- MySQL：不开放公网；本机通过 SSH 隧道访问线上库时使用本地端口 `13306`

## 当前状态备注

- 前后端本地构建已通过
- 前端首页和后端健康检查已验证可访问
- 仓库里还有一些本地辅助目录未纳入提交，比如 `.codex-logs/`、`.claude/`、`skills/`
- 业务代码默认使用线上数据库；本地库不再作为启动兜底
