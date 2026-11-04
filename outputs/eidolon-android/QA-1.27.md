# 1.27 验证记录

2026-11-04，1.27/code83，DB/备份18不变；从既有1.26原工程增量修改，没有克隆覆盖。源码基线与上次交付哈希一致，远端基线0edeb2df916eaa5ddd1e05186d69396c48585740。

最终Release/lint成功，原v2/v3升级签名、16KB对齐、包名/min26/target35/版本核对通过；全部APK assets逐字节匹配主源码；全部Java class时间不早于对应源码，实际DEX包含新增内容/并发/诊断类和所有Manifest应用组件。公开文本统一LF，部分旧文件保留原换行。

APK：Eidolon-1.27-original-upgrade.apk / 6638537 bytes。
SHA256：de3b332908b482a33d4bf940544bb3801972bf5fa9b1c2da6072a01e94ba1674
Git blob：ee5c06daa66c23344272f8be4df4fa30e83991e5
证书SHA256：d576e97cae45aca6dedb80064e56a2aae97553fdaa8705904b63420b1d4503db

完整现有test.ps1退出0，包括媒体、角色系列偏好、未读、关系/记忆合并、时间、备份、日程/03:00、生命周期及通话等既有离线套件。31项新增RepairTests通过：br变体、连续空行、代码/引用、惰性HTML文本、合法/非法候选指令、裸ID与TTS、结构化返回、限流/超时/格式/取消/过期脱敏分类、排队/执行/迟到手动取消身份、总4/后台2请求槽、公平等待与取消释放。使用实际ModelClient+JVM自定义HTTPS假连接同时运行日程用途和聊天用途请求，取消前者后后者继续并正确返回，未发生额外HTTP重试；不是网络或Android生命周期验收。

本地SQLite生产SQL夹具通过：迁移/回滚、同任务并发CAS、配额、进程恢复不重放、删评论重发后的UUID/token、恢复账本保留及幂等；更新准入查询，验证只有同角色正在生成文字才延后社交，等待送达/失败不形成全局模型锁，日程与聊天不共用WritingEngine.running。原事务、效果合并、未读和备份回归通过，未创建或迁移数据库表。

实际HTML/JS+桌面Chrome假桥：repair-ui通过，6种异步事件到来时实际textarea引用不变，草稿/焦点/选区/模拟composition状态和历史滚动保持；原生IME事件驱动默认/关闭设置，5栏导航、3种布局与深色，安全历史文字、语音准备/失败气泡反馈，社交分类与明确重试参数。视图位置与聊天时间回归发现并修复局部刷新遗漏的更早消息按钮和剧情参考区。

共20组不同页面测试通过（含新repair及现有calls/settings/inbox/unread-entry/chat-time/social/schedule-error/schedule-rollover/reply-background/sticker-packs/view-position/life/media/release-notes/desktop-badge/diary/notebook/writings/writing-plans）；手动取消作用域调整后重跑repair/notebook/writings/writing-plans/calls通过。竖横屏/窄屏/深色截图视觉检查，测试图含刻意HTML字面量与假数据，未执行脚本。

没有连接手机，没有调用真实付费模型、ASR、Fish或天气服务。键盘真实弹出/收起、中文输入法组合、浮动/硬件键盘、横竖屏insets、Vivo/iQOO后台、Android音频焦点与真实多轮仍待实机验证。不能凭静态截图还原手机旧失败原因；旧泛化提示不会被伪造为新错误码。未知付费结果不自动重放，手动重试仍受日配额限制。取消不能保证服务未收费。

发布资料排除私有构建/签名配置、密钥、私聊/录音、测试截图和本地patch脚本；以基线树追加，发版前核对全部17个远端旧APK的hash/size，main使用expected-head更新并读回确认。
