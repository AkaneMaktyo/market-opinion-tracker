# Market Opinion Tracker

个人美股直播观点跟踪系统。

## 当前第一版能力

- 录入每日直播原文。
- 按 KOL 管理不同来源的直播观点。
- 粘贴 JSON 后预览确认，并批量保存多个品种观点。
- 按品种查看历史观点。
- 自动生成本地示例 K 线，方便先验证交互。
- 在 K 线上显示看多、看空、震荡、观望标记。
- 记录支撑、压力、目标、止损等关键价位。
- 对观点做命中、失败、未触发等复盘。

## JSON 导入

进入首页后，先在顶部选择或新建 KOL，再粘贴 JSON。

系统会优先识别：

- `总体摘要`
- `按具体品种划分`
- `待确认映射`

预览阶段可以勾选或取消品种、修正代码、方向和观点文本。非明确交易项会被跳过，不进入 K 线图。

## 技术栈

- 后端：Java 17、Spring Boot、Spring JDBC、SQLite。
- 前端：React、TypeScript、Vite、TradingView Lightweight Charts。

## 启动

后端：

```powershell
cd D:\_code\personal\market-opinion-tracker\backend
mvn -DskipTests package
java -jar target\market-opinion-tracker-0.1.0.jar
```

前端：

```powershell
cd D:\_code\personal\market-opinion-tracker\frontend
npm install
npm run dev
```

默认访问：`http://localhost:5173`

若本机 npm 配置了失效代理，可用：

```powershell
npm install --proxy=null --https-proxy=null
```

## 设计原则

- 原文必须保留，结构化观点可修正。
- 观点必须绑定品种、时间、方向、周期和关键价位。
- K 线只是展示层，长期价值来自观点复盘数据。
- 第一版本地优先，后续再接真实行情源和 AI 自动抽取。
