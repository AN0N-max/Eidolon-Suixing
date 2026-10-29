# 1.2 架构审计与实施记录

基线为当前1.1/code54源码，不使用任务书过期的0.9版本号重新开发。

## 审计

- UI为assets/ui中的HTML、JavaScript与CSS，WebActivity提供受同源限制的WebView和Native.post/nativeReply桥接。Java负责数据库、密钥、模型HTTP、后台调度、通知、语音及文件处理。
- ChatStore为SQLiteOpenHelper，当前数据库v11；messages以文本为主，另有gift_event角色。已有普通回复持久化pending_replies及一次性投放事务。ModelClient独立于WebView，可直接后台调用；无需隐藏页面。
- ReachOut、ReachPolicy、ReachConfig、ReachBudget已有关系/定时策略、总量和请求预算、免打扰、记忆/角色资料/日程上下文、弹窗和通知。ReachJob是15分钟起的周期JobScheduler任务，前台另有Handler检查；reach_schedule仅保存会话/contact/due，没有每条任务状态或实际消息关联。新实现必须替换其调度，避免两套并行请求。
- ScheduleEngine/ScheduleStore/ScheduleRuntime/ScheduleData已有按角色每天生成、Hibi习惯、睡眠与洗澡判定、跨午夜修复、状态上下文。WritingJob负责独立写作及日程，不因关闭主动搭话而取消。
- 信件、日记已有数据库已读及备份，隐藏日记有独立权限和未读；缺少统一入口红点。聊天没有持久化未读列。
- 现有build.ps1直接调用javac/aapt2/d8，没有依赖解析或AndroidX。为真实接入WorkManager，新增Gradle Android构建配置，保留Java、资源、assets原目录和原签名；不写WorkManager同名替代类。旧构建保留为build-legacy.ps1供旧版本参照。

## 兼容要求

包名com.eidolon.sc.mobile及原版证书保持；数据库v11升v12，历史消息默认已读，不猜测旧未读。加密备份v12携带逐消息未读，导入v1–v11默认已读；计划不从备份重放。新增WorkManager自身数据库独立于聊天库。旧reach_schedule保留供迁移兼容但不再触发旧周期生成。

## 调度设计

非精确AlarmManager(RTC_WAKEUP)只保存一个最近唤醒点（任务或下一次05:00），Receiver只入队WorkManager。无网络的计划Worker生成/核对本地时间；有网络约束的生成Worker到时读取最新上下文，再调用现有ModelClient。唯一Work名称、数据库条件领取、配置版本和投放事务共同防重。不请求精确闹钟权限、不启动Activity、不常驻服务。

外部API与SQLite不能组成同一事务：如果请求发出后进程中断，无法证明服务是否已计费。因此RUNNING中断标记FAILED，不自动重发；手动重试明确提示可能再次计费。一次性消息落库可保证，跨网络的绝对恰好一次计费不能承诺。
