# 1.2 发布与验收记录

2026-10-26，Asia/Shanghai。基线为当前1.1，版本1.2/code55。

## 交付

- APK：outputs/Eidolon-1.2-original-upgrade.apk。
- APK SHA256：4DC8D7903F7C91E4712CD11684B6841E7781751C8D8BC404B44969B71C53C471。
- 包名：com.eidolon.sc.mobile；minSdk26/targetSdk35。
- 原升级证书SHA256：d576e97cae45aca6dedb80064e56a2aae97553fdaa8705904b63420b1d4503db。
- 构建目录：work/android-build-1.2；原签名库/别名保持。没有安装手机，没有调用真实API。
- 保留1.1个人头像、跨午夜作息及先前聊天、语音、日历、约定、关系等功能。

## 完成功能

1. 每日主动计划：每天约05:00检查；开启、升级启动或用户重新安排时也检查当前日。只生成本地时间与元数据，不提前生成正文。优先使用已有角色日程，排除睡眠、洗澡、临睡及上课/练习/工作等忙碌活动。没有日程时使用已有活跃、关系、免打扰规则，不为计划额外调用模型。
2. 持久任务：UUID、角色/会话、所属日/时区、计划/过期时间、类型、五种执行状态、创建/结束时间、实际消息ID、请求次数、配置版本、领取令牌及原因。日期/时区/版本账本防止重复抽当天计划；同配置重复检查不重抽。日程变化核对未执行项，不合适时跳过；可手动重新安排。保留近14天任务诊断（今日界面最多300项，优先未完成和失败）。
3. 后台调度：单个非精确AlarmManager → 非导出Receiver → WorkManager。计划Worker不需要网络，生成Worker要求网络；唯一工作名、数据库条件领取和提交事务防重；普通核对合并，设置修改追加一次新核对，避免旧Worker结束时吞掉刚保存的设置。WorkManager任务ID段20000–29999与既有回复/写作Job分开。取消旧283周期任务；WebView和前台Handler只请求核对计划，不再独立调用主动模型。
4. 到时生成：复用ModelClient、人物/关系、最近历史、记忆、当前作息与约定提示。仅一条简短主动消息，1200 token输出上限，不增加独立判断请求。去重近期主动消息的相同/高度相似文本。触发和投放前都检查最新关系、作息、会话、免打扰、额度、最近交流和设置版本。
5. 成本控制：系统延期超出有效期则跳过，不补历史消息。计划间隔与实际主动消息间隔都检查，避免系统恢复后集中补发。网络未恢复时由WorkManager等待；不因离线反复每30秒唤醒。请求发出后失败或中断标FAILED，不盲目重发；进程重新启动时由Application在任何新Worker启动前核对旧RUNNING。用户可确认手动重试，每任务至多三次，仍受每日请求预算约束。
6. 设置：保留五个折叠板块，增加允许角色范围、逐角色上限覆盖、最近交流暂停、计划间隔、延期有效期、主动消息系统通知开关和通知正文隐私。旧前后台周期及自动重试参数保存在配置中，但不再驱动1.2后台请求。关闭总开关取消主动闹钟/工作/可取消请求，旧结果不得落库；保留聊天、角色行程、信件与日记等独立功能。
7. 未读：聊天assistant消息逐条持久化unread，主动消息及正式延迟回复正式保存时置未读；列表按角色数量，底部对话显示总数。实际可见、焦点在本应用且无阅读弹窗遮挡时才由桥接确认具体消息ID，重复确认不重复扣数。原生层再核对当前角色、会话、页面及窗口可见状态。历史屏外消息不因打开聊天被批量清零。超过首屏最近200条时可按200条加载更早消息，避免旧未读无法查看。
8. 信件与日记：复用既有正文/隐藏页已读及关系授权，在生活日常、对应分支、日历快捷入口和设置导航显示未读提示。锁定日记不泄露隐藏内容或其未读。实际阅读清除对应记录，已读不反复提醒。
9. 通知：沿用前台对应聊天直接更新、其他页面横幅、应用外悬浮窗/通知分流。后台通知使用角色头像、安全的不可变PendingIntent、角色会话路由、渠道、隐私内容和未读number；读完该角色全部消息时取消其系统通知。应用内未读不依赖POST_NOTIFICATIONS或悬浮权限。桌面角标由启动器决定。
10. 后续方案：表情包资源ID、消息类型与备份设计，实时电话状态机、ASR、VAD、流式模型、TTS队列、打断、音频焦点及成本模块，见FUTURE-MODULES.md。本轮不开始实时电话或申请麦克风权限。

## 架构与构建

Java负责本地规则/数据库/HTTP/调度与通知，assets/ui负责界面，通过WebActivity桥接。沿用现有目录，未整体重构聊天。新增Gradle8.9/AGP8.7.3构建真实AndroidX WorkManager2.9.1，合并官方依赖资源和Manifest；仍由build.ps1入口使用原签名签名、16KB对齐。首次需下载官方Gradle及Maven依赖，本机已缓存。Gradle SDK视图链接到work/android-tools原SDK。此Windows主机Java AF_UNIX不可连接，构建脚本使JDK使用其TCP本机回环回退，仅作用于构建进程，不改变手机或系统全局配置。

许可证及上游NOTICE随包保存；新增assets/ANDROIDX-LICENSE.txt和THIRD-PARTY-NOTICES.txt，保留既有AGPL/Hibi出处。build-legacy.ps1仅供旧构建参考，无法独立解析新版依赖；不要用它构建1.2。

## 数据迁移与兼容

- SQLite11→12新增reach_days、reach_tasks、messages.unread及索引；不删除原消息、记忆、设置或语音。历史消息默认已读，不猜测以前是否查看过。旧reach_schedule保留兼容，不再由旧周期生成。
- 加密备份12保存消息未读布尔标记；校验仅assistant可未读。导入1–11时新导入消息默认已读，原有未读记录不被批量重置；导入12保留对应消息状态。已有信件/日记已读继续随原备份保存。
- 不从备份重放主动任务、闹钟、网络请求或已用请求账本；恢复使旧主动配置版本/生成失效，未来按本机现有设置重新检查。WorkManager有独立本机数据库。
- 1.2备份不能由旧APK导入；升级数据库后不支持降级APK。个人头像与语音文件仍按之前规则管理，不额外纳入聊天备份。

## 离线检查结果

| 检查 | 结果及边界 |
| --- | --- |
| Java与APK | 编译、AGP release构建、原签名v2/v3和16KB对齐通过；包名/版本元数据核对 |
| 新规则 | 27项计划/时区/随机/间隔/忙碌/角色范围/上限/备份未读/重复文本检查通过，纯JVM |
| SQLite | 执行生产新表及领取/已读/过期SQL，模拟事务回滚与任务完成；旧消息默认已读、重复领取失败、读取隔离及幂等、中断失败不重发通过；不是Android SQLiteOpenHelper生命周期实测 |
| 既有回归 | test.ps1全部通过，包括核心、关系、记忆、行程、睡眠、写作、来信、日记隐藏页、关键剧情、申诉、延迟回复和搭话预算 |
| 模拟界面 | inbox-ui、reach-settings-ui、settings-ui、diary-ui、appeal-ui通过；未读可见ID确认、红点独立清除、重试确认、较早消息分页、设置保存/校验、返回滚动、窄屏及隐藏内容授权回归 |
| 语法与资源 | 当前JavaScript语法、合并Manifest、真实WorkManager类/服务、非精确闹钟权限范围及截图检查通过 |
| 真机 | 前台、退到桌面、锁屏、最近任务清理、系统杀进程、重启、断网、系统通知与桌面角标均**未验证**；没有在手机执行测试或真实模型请求 |

界面截图：outputs/ui-1.2-plans.png、outputs/ui-1.2-reach-settings.png。记录：work/tests-1.2-final.log；最终构建记录在work/build-1.2-*日志。

## 真机验收操作指南（由用户执行）

1. 先用应用备份功能保存当前记录，手动覆盖安装1.2。确认聊天、密钥、头像、语音、角色作息仍在。开启系统通知，按需要允许悬浮窗；在OriginOS为本应用允许后台耗电。
2. 在主动搭话中只选一名聊过的、目前空闲且不在冷却的角色；每日总上限/单角色/请求上限均设1，最近交流暂停设5分钟，指定时间设在当前约15分钟之后，免打扰按需要配置。保存后进入今日计划，确认出现未来PENDING和有效期；若为空，检查关系间隔、最近交流、会话与作息条件。
3. 此步会使用用户自己的API。分别在前台其他页面、对应聊天、桌面、其他应用、锁屏进行独立日期或明确重新安排的验收；每轮最多1次请求。检查计划完成关联消息、正确分流以及未读数量，不以任务入队日志证明实际弹窗已展示。
4. 先断网再到计划时间，确认没有模型请求；在有效期内恢复网络，检查一次送达。超过有效期恢复，应SKIPPED且不调用API。已发出请求后被打断，应FAILED而非自动重发；手动重试前留意可能重复计费提示。
5. PENDING时关闭主动搭话，确认不再调用模型，聊天/日记/信件/行程独立设置保持；重新开启只安排未来时间，不补过去任务。生成期间关闭，允许网络取消有延迟，但旧结果不得保存成新主动消息。
6. 分别验证从最近任务移除、系统杀后台进程和手机重启，检查PENDING恢复；已发出的RUNNING恢复为FAILED，避免未知请求重复。最近任务清理在不同厂商可能被当作强杀，需单独记录。
7. 开消息实际查看再退出，检查列表/导航未读准确；滚动到旧未读才清除它。关闭系统通知时未读仍应保存。打开对应未读信/日记清除其红点，其他记录保持。导出12备份再恢复到测试数据，核对未读；旧备份导入为已读。
8. 若在系统设置中“强行停止”，不能期待自动执行。需要用户主动再次打开应用后恢复安排。不要用force-stop冒充系统正常结束进程的测试。

## 已知限制与下一步

非精确闹钟、WorkManager和网络约束均可被Doze、厂商冻结或配额延后。OriginOS曾出现fast_freezer整进程冻结，不能承诺本版消除此限制。05:00只是期望时间，不要求精确闹钟权限；只在每天未来时段安排，不批量补发。

外部服务计费与本机事务没有共同提交协议，不能保证网络边界的绝对一次计费；本版保证条件领取和消息一次性提交，并在结果未知时停止自动重发。模型是否完全遵守人设/日程、真实服务输出预算、实际通知展示及功耗都需要后续真机验收。

优先完成上述真机验收和失败诊断，再做表情包手动发送原型；实时电话按FUTURE-MODULES.md独立推进，不把单条TTS当成电话。

## 新增与修改文件

新增Java：EidolonApp、ReachAlarmReceiver、ReachScheduler、ReachPlanRules、ReachPlanStore、ReachPlans、ReachPlanWorker、ReachWorker、ReachExecution、UnreadState。

修改Java：ChatStore、BackupValidator、ReachOut、ReachConfig、ReachAlert、WebActivity、MainActivity、ScheduleEngine、ReplyRescheduleReceiver；原ReachJob保留但不再注册为主动周期服务。

界面：新增assets/ui/inbox.js；修改app.js、settings.js、style.css、index.html。

构建/资源：AndroidManifest.xml、build.gradle、settings.gradle、gradle.properties、build.ps1、tools/build-workmanager.ps1、参考build-legacy.ps1、test.ps1、assets/ANDROIDX-LICENSE.txt、assets/THIRD-PARTY-NOTICES.txt及Hibi出处副本。

测试：新增ReachPlanTests.java、test_reach_plans.py、inbox-ui.cjs；修改reach-settings-ui.cjs以及旧test_letters/test_diary/test_appeals中的当前数据库版本断言。

文档：本记录、DEVELOPMENT-1.2.md、FUTURE-MODULES.md、README.md、REACH-OUTS.md、HIBI-AUDIT.md。
