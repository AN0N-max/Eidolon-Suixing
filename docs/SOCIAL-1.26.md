# 1.26 实施与文件索引

主开发工程仍是已有eidolon-android；没有克隆替换、重写聊天/通话/数据库。公开副本使用可移植工具，不包含本机签名脚本或凭据。

| 作用 | 文件 |
|---|---|
| 原上游规则、动态存储及备份校验 | SocialData.java、SocialStore.java、SocialBackup.java |
| 角色圈子、日程事件和地点材料 | SocialCircle.java、SocialWork.java、PlacePool.java；assets/places.json、social-profiles.json |
| 生成、延迟任务及清理 | SocialEngine.java、SocialWorker.java、SocialCleanupWorker.java、SocialScheduler.java |
| 页面动作/城市定位 | SocialActions.java、CityLocator.java |
| 天气解析、HTTP、缓存及认证 | WeatherData.java、WeatherClient.java、WeatherRuntime.java、QWeatherSigner.java |
| 共享只读上下文 | WorldContext.java；ConversationEngine.java、ReachExecution.java内最小接入 |
| 日程修复 | ScheduleData.java、ScheduleGeneration.java、ScheduleAttempts.java、ScheduleQueueRules.java、ScheduleCandidates.java、ScheduleQueue.java、ScheduleFillJob.java、ScheduleEngine.java、ScheduleRuntime.java |
| 新页面及总设置入口 | assets/ui/social.js、social.css、app.js、settings.js、schedules.js、index.html |
| 生命周期/迁移接入 | ChatStore.java、BackupValidator.java、EidolonApp.java、ReplyRescheduleReceiver.java、WritingEngine.java、WritingJob.java、WebActivity.java、UiSnapshots.java、AndroidManifest.xml |
| 版本和依赖 | build.gradle、assets/update-notices.json、assets/licenses/bouncycastle-notice.txt |
| 新增验证 | SocialRulesTests.java、ScheduleQueueTests.java、test_social_sql.py、social-ui.cjs；既有完整回归继续执行 |

原上游参考固定为596368d7f7253d58840eb4f0e6f02f57c4418552；视频仅用于UI和配置归属对照，不包含在应用/仓库。原项目AGPL-3.0继续适用，地点含© OpenStreetMap contributors/ODbL；新依赖Bouncy Castle1.86用于JWT Ed25519轻量实现，不安装全局Provider。来源说明见[第三方](THIRD-PARTY-1.26.md)。

人工验收建议：先覆盖安装并核对聊天/表情/历史安排；观察行程原因，旧失败手动重试；配置城市/天气后单独确认服务权限；最后开启朋友圈并观察一次生成与延迟互动。人工操作是否调用服务由用户决定，开发过程未执行这些真实请求。系统后台、28人吞吐与手机定位不能以桌面模拟替代。
