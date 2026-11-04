# 1.27 修改索引与验证入口

| 内容 | 主要文件 |
|---|---|
| 严格控制语法与旧记录兼容 | ReplyMarkup.java、ReplyText.java、MediaReply.java、ChatEnvelope.java、MessagePresentation.java、StickerEngine.java、UiSnapshots.java、WebActivity.java |
| 朗读控制语法过滤/普通进度提示 | FishClient.java、MainActivity.java；VoicePlayer/缓存/音频焦点实现沿用 |
| 有界并发与独立取消 | RequestSlots.java、ModelClient.java、JevClient.java、ConversationEngine.java、ScheduleEngine.java、ScheduleCandidates.java、ScheduleQueue.java、WebActivity.java |
| 朋友圈错误、重试与未读 | SocialFailure.java、SocialEngine.java、SocialActions.java、SocialStore.java、SocialWorker.java、social.js、inbox.js |
| 真实IME状态、局部刷新、五栏与播放状态 | WebActivity.java、UiSnapshots.java；app.js、chat-updates.js、chat-time.js、media.js、settings.js、schedules.js、style.css、index.html |
| 新增回归 | RepairTests.java、repair-ui.cjs；扩展test_social_sql.py，既有social-ui入口改为明确导航定位，test.ps1加入新检查 |
| 版本/依赖 | build.gradle：1.27/code83、AndroidX Core1.15.0（SDK35兼容），test.ps1与构建产物目录更新；assets/update-notices.json |

ModelClient只在实际HTTP阶段持有请求槽，不在数据库事务中等待；后台槽最多2/全部4，公平等待、有界超时、取消检查及finally释放。AndroidX Core用于WindowInsetsCompat键盘状态与系统栏/IME合并避让，依据[Android官方IME说明](https://developer.android.com/develop/ui/views/layout/sw-keyboard)，不是输入框focus检测。原adjustResize保留。新增Core依赖Apache-2.0，见[AndroidX项目](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt)。既有第三方说明继续适用。

排查链路：ModelClient聚合原返回→MediaReply/ChatEnvelope→原pending/body/content持久化→原ReplyManager事务与效果提交→UiSnapshots/chatOlder只读兼容→messageText/mediaBubble安全文本节点→FishClient.speech。原始历史body/content不批量改写。新模型内容规范化后按原流程入库；不保存敏感原响应作为调试日志。

按任务隔离：日程手动请求使用requestId，排队取消不取消已抢到执行权的自动日程，迟到停止不作用于新身份。聊天仍按pending id+revision拥有自己的客户端集合；旧响应不提交到新会话。Social结果提交只核对自己的token、配置版本、资格与日期，不以别人的聊天/写作状态丢弃成功返回；任务账本不重开。

相关修改文件完整清单随公开提交，验证结果与实机限制见QA-1.27.md。开发用一次性patch脚本只留本地工作目录，不能重跑覆盖主源码。主源码仍是已有eidolon-android，publication仅为公开暂存。

补充：手动写信/日记和记忆整理的停止操作限定各自UI所属线程/客户端，默认聊天停止不再顺带取消其他写作。某角色的社交任务等待时只延后自身，继续检查其他角色。
