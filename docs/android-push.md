# 安卓推送可靠性配置

安卓端使用极光推送。应用会在极光注册成功后绑定 `JPUSH_ALIAS`，并在失败时自动退避重试；服务端默认保留离线通知 3 天。

## 基础配置

APK 构建与后端必须使用相同的别名：

```text
JPUSH_ALIAS=market_tracker_user
```

后端还需要：

```text
JPUSH_APP_KEY=极光应用 AppKey
JPUSH_MASTER_SECRET=极光应用 Master Secret
JPUSH_TIME_TO_LIVE_SECONDS=259200
```

普通极光账户的离线保留上限通常为 3 天，VIP 可按账户能力把 `JPUSH_TIME_TO_LIVE_SECONDS` 提高到最多 864000（10 天）。

## 厂商离线通道

仅使用极光长连接时，应用进程被国产 ROM 清理后无法保证实时送达。生产 APK 应至少接入实际使用手机品牌对应的厂商通道。

通过 `JPUSH_ANDROID_VENDORS` 启用通道，多个值用逗号分隔：

```text
JPUSH_ANDROID_VENDORS=xiaomi,oppo,vivo,honor
```

各通道还需在构建环境提供对应参数，并在极光控制台配置相同凭据：

| 通道 | 构建环境变量/文件 |
| --- | --- |
| 小米 | `XIAOMI_APPKEY`、`XIAOMI_APPID` |
| OPPO | `OPPO_APPKEY`、`OPPO_APPID`、`OPPO_APPSECRET` |
| vivo | `VIVO_APPKEY`、`VIVO_APPID` |
| 魅族 | `MEIZU_APPKEY`、`MEIZU_APPID` |
| 荣耀 | `HONOR_APPID` |
| 华为 | `frontend/android/app/agconnect-services.json` |
| FCM | `frontend/android/app/google-services.json` |

魅族和 OPPO 的参数值要保留厂商要求的 `MZ-`、`OP-` 前缀。

华为/FCM 配置文件已加入 `.gitignore`，不要提交密钥文件。若启用了通道但缺少配置，Gradle 会直接终止构建，避免发布“看似接入、实际不可用”的 APK。

## 真机验收

1. 首次启动允许通知，在“我的 → 新消息通知”确认“系统通知”和“推送服务”均正常。
2. 在极光控制台确认当前 Registration ID 已绑定到配置的别名。
3. 分别测试前台、后台 10 分钟、锁屏、划掉任务、强制停止后的通知。
4. 厂商通道的有效判定以“划掉任务后仍能收到”为准；“强制停止”后 Android 通常会禁止任何推送，需用户再次启动应用。
5. 若国产 ROM 仍漏收，在系统设置中允许应用自启动，并把电池策略改为“不限制”。
