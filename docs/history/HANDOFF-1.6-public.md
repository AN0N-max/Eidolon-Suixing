# 1.6 技术交接记录（公开节选）

此文保存1.6时的技术结构、实现边界与开发记录，不是当前操作指令或新授权。原交接中的本机位置、签名配置和个人设备操作边界不公开。当前功能以1.12发布说明为准：自绘弹窗已取消、检查边界已改03:00、主动消息的两项等待门槛已取消。

## 3. 产品方向与技术结构

应用定位为个人使用的独立APK，主要数据在手机本地。当前是 **Java原生能力 + APK内HTML/CSS/JavaScript的WebView界面 + SQLite本地存储**，不是Kotlin纯原生界面，也不是挂在服务器上的网页前端。

模型由用户配置：DeepSeek、Gemini、自定义OpenAI兼容接口；判断服务有兼容接口、Jev、百炼。此前“百炼角色模型”是误报，无需新增专用角色模型入口。当前没有自建服务端，也没有FCM。此前讨论过前后端，用户选择先继续本地版。

保留原网页的个人功能和风格，Hibi移植优先搬玩法与角色档案并做本地适配，不整体搬服务端、Docker或后台数据库。不需要生图。

已有功能包括：角色聊天、日中双语与补译／隐藏日文、字号与头像设置、礼物、任务／成就、自动记忆／剧情检索、好感与关系、个人档案、本地语音与暂停续播、夜间模式、日历／认识纪念日、信箱／事件日记、行程／睡眠／洗澡、约定／兑现、主动搭话、延迟回复、应用内横幅和跨应用悬浮窗。提示与声音需尊重静音及免打扰。

## 4. 最近完成的版本

### 1.6：未读红点、返回应用路由、小箭头

本轮用户要求：头像和桌面图标参考QQ显示未读；有新消息时进入软件不要强制跳进聊天；将过大的“回到最新消息”改成剧情参考左上方小箭头。

已完成：

- 未读数字移到角色头像右上角，底部“对话”显示总数，超过99显示99+；辅助说明保留完整数字。列表顶部显示消息总数与有未读的角色数量，其他页面未读增加时有短提示。
- 找到强制跳转根因：WebActivity.onNewIntent原来不区分普通启动与点通知，均发送refresh；网页refresh原来无条件page=chat。
- 新增ChatEntry：新消息通知使用OPEN_CONVERSATION动作，要求完整角色／会话目标并验证归属，读取后消费目标；MAIN启动即使残留目标也不跳聊天。旧通知／悬浮卡片完整目标继续兼容。
- 普通返回只做inboxRefresh，普通refresh不改变页面。当前WebView仍存活时保留当前页面和原位置；普通冷启动显示对话列表。只有点击具体消息通知／卡片／横幅或明确会话操作才打开聊天。
- 持久未读本来在1.2已有，本轮增强展示和通知同步。仍只将实际可见assistant消息ID标为已读，不因打开软件、列表或点通知清空整段历史。旧的未读如果仍在上方未看，角色角标可能继续保留。
- 新增UnreadNotifications，标准Android Notification.setNumber与showBadge；每角色复用一条通知，避免额外汇总通知重复累加。阅读、删除会话、前后台切换和其他页面收到回复时核对未读；缺少通知时可静默保留未读数量。
- 真实消息使用原通知通道及声音／隐私设置；同步onlyAlertOnce。通知不在点击时自动删除，而在消息实际已读后更新／清除。读确认与通知发布共用锁，阻止已读消息被稍晚回调重新挂上角标；test=true本地测试保留可用。
- 大文字按钮改为36×36下箭头，位于剧情参考上方左侧；无剧情参考时在输入区上方同一角落，不新增一整行。离底部至少100px显示，当前角色有未读时附红点，点击跳到底部。

**未完成真机验收**：桌面角标的数字／圆点由OriginOS启动器、通知权限、通知类别和角标设置决定。标准通知计数接入不等于已在该手机证明与QQ相同。用户划掉系统通知后，桌面角标可能暂时消失，应用后续核对再同步。应用内未读独立保留。未加入厂商私有广播、FCM或服务器。

主要文件：

```text
src/com/eidolon/sc/mobile/ChatEntry.java           新增
src/com/eidolon/sc/mobile/UnreadNotifications.java 新增
src/com/eidolon/sc/mobile/UnreadState.java
src/com/eidolon/sc/mobile/ReachOut.java
src/com/eidolon/sc/mobile/ReplyManager.java
src/com/eidolon/sc/mobile/WebActivity.java
src/com/eidolon/sc/mobile/ChatStore.java
assets/ui/app.js
assets/ui/inbox.js
assets/ui/view-position.js
assets/ui/style.css
```

### 1.5：内部时间标记、消息时间分段、位置恢复、05:00安排

- 模型曾直接输出“【消息发送时间：……】”。根因是历史assistant正文里也被加了时间前缀，模型把它学成台词格式。MessageTime／ModelClient现将时间放在单独system参考表，真实历史正文不附时间前缀。
- ReplyText精确清理已知内部标签，接入展示、上下文、投放、语音及补译。保留用户自己的引用和角色正常提到的日期；仅内部标签的答案拒绝投放。旧数据库正文没有全量重写，不能假设所有未知格式都能识别。
- 气泡外居中时间：首条、间隔至少5分钟、跨天等重新分段。依真实sent_at和当前档案时区显示；旧时间未知不补造。分页重算边界。
- view-position.js统一管理选项／列表位置，角色页按角色、聊天按会话保留锚点与偏移；离开A到B再回A不回顶部，读旧消息时新回复不强拉到底部。聊天缓存最近12会话／约400万文字。
- 位置在当前WebView内存中，**不保证进程回收／冷启动／缓存淘汰后的深层位置和草稿恢复**。
- 新入口“设置 → 生活日常 → 信件与日记安排”，只读查看计划、状态、尝试和错误，不调用模型、不泄露暂存信件或隐藏页正文。
- WritingMorningJob：id288每日档案时区05:00持久离线检查；id289独立联网日记任务；保留284写作检查及286／287来信生成／投放。日记按记录次日05:00后整理，最多补近3天，每角色每日最多2次，失败至少隔1小时，无事件不硬写。
- 05:00是期望起点，不是精确闹钟。没有新增FCM、服务器或常驻保活。

### 1.4：角色提示词编辑

入口“设置 → 聊天与关系 → 角色提示词”。逐角色查看／修改sheet与facts，查看内置原稿、恢复默认，离开或换角色提醒未保存修改。保存在personal.rolePrompts并随加密备份保留；普通／延迟聊天、主动搭话、来信和日记共用，行程使用精简设定。查看／保存不调用模型，已生成的待发送回复不重写。

### 1.3：时间记忆／聊天总结、主动计划修复、延迟回复切换会话

- “设置 → 聊天与关系 → 时间记忆与聊天总结”：区分事件日期、消息发送时间、整理时间，保留来源，可编辑、锁定、查看历史。总结先生成预览，再确认保存；未知日期不默认今天。自动事件提取复用已有记忆请求，不新增独立请求。
- 设计参考酒馆总结／记忆表格的思路，**不是能直接安装酒馆插件**，也不是把全部历史无限装进上下文。
- 主动计划按今日发送较少／等待更久优先分角色，每角色同时一条待执行安排，历史记录折叠。仅分配规则实际变化才替换任务；重复保存或改变弹窗外观不再使旧计划失效。
- 临时忙碌、近期聊天、作息等在原有效期内延后，超期才跳过；已发出请求而结果未知不盲目自动重发。过去截图中的失效记录不会被伪装成已完成。
- 开启延迟回复时按会话／批次执行，A生成期间可进入B；发送／取消B不取消A，A完成不抢回页面、不清空B草稿。不承诺所有会话并发调用模型，保护服务可串行处理。

### 更早的保留改动

- 1.0：主动搭话参数分组可配置，版本命名改为1.x；有每日额度、角色上限、关系间隔、时区免打扰、上下文／记忆量、重试及弹窗设置。
- 1.1：修复午夜前后夜猫子角色被统一判睡，优先昨夜实际安排，缺失明确估计，聊天状态／上下文／睡眠延迟共用本夜规则。个人档案可换头像，私有存储；头像不发送模型，当前加密聊天备份不含头像。
- 1.2：本地每日主动计划、非精确AlarmManager → Receiver → WorkManager网络执行，SQLite持久聊天未读及信件日记入口红点。
- 早期0.9.x已完成：分组设置、关系查看面板、剧情检索范围、通知设置跳转、本地短延迟测试、行程与洗澡、Hibi关系档案、关键剧情、误判申诉、信件提前生成／离线投放、隐藏日记页等。不要重复实现或退回0.9.8源码。

## 5. 延迟回复与后台的实际边界

用户消息先保存，约4秒连续输入合并，按发送时行程和本地随机规则决定延迟，不为延迟额外请求判断模型；回答提前生成并持久暂存，正式投放时才提交聊天及相关副作用。相同会话新消息通过批次版本使旧生成／待发送失效，提供立即接收、取消、失败重试及状态。

短本地测试ReplyTestService与正式回复的ReplyWorkService不同：前者不调API、不写正式聊天；后者在用户发消息等可见操作时启动dataSync临时保护，最多约10分钟，完成后结束，较长等待仍交回持久任务。JobScheduler／WorkManager／Receiver负责其他恢复，不保证秒级准时。

历史USB证据见 `outputs/eidolon-android/USB-DEBUG.md`：0.9.26现场发现OriginOS fast_freezer把整个进程冻结，连临时前台服务也会暂停；返回应用解冻后才继续。用户将Eidolon后台耗电从“智能控制”改为“允许后台耗电”后反馈成功。这是当时设备测试的结果，不能推断所有场景都已解决。

窗口切换时序另已修复：onUserLeaveHint、焦点、生命周期共同判断，投放在主线程等待250ms再分流。当前对应聊天不弹窗；其他页面横幅；外部悬浮窗并尝试系统通知。addView成功、通知已提交日志都不证明厂商系统实际展示。

## 6. 需要持续关注，不能照搬旧交接结论

- 当前最多40条历史及约24000字历史裁剪仍在，时间记忆是补充，不代表已有全部聊天原文都参与模型生成。
- 延迟总开关关闭后仍保留约4秒合并与非流式暂存路径。若用户要求严格恢复原即时／流式体验，需要单独适配与验证。
- 前台Activity与后台ReplyFallback仍有两份相似生成流程，修改上下文、关系、记忆时必须核对两条路径一致。
- **旧0.9.7交接里“PersonalMerge相同好感／计数改动直接跳过”的说法已不能原样沿用**：当前源码对affinity.*和部分progress.counts.*已做数值增量合并，并有并发送礼／计数检查。但整体仍为三方合并，复杂数组／关系状态、语义副作用及后续同时修改需据当前源码审查，不能据此宣称所有并发问题消失。
- **旧“onStopJob完全不取消线程”的描述也过时**：当前ReplyFallback登记id／revision线程所有权与可取消client，PendingReplyJob停止时只取消自己拥有的生成并重新入队。事务一次性投放不能证明网络层恰好一次；中断请求可能已计费。
- 已有调度失败可见错误，不再只是写日志；正式后台时序、重启、强停、长等待仍缺最新Android全链路验收。
- SQLite未读和通知计数已有，不能再笼统说“只有系统通知没有持久未读”。但桌面角标与实际通知展示仍待真机确认。
- 本轮1.6锁／通知管理器及OEM启动器只做编译、规则／SQL与模拟UI验证，未实际执行Android通知生命周期。
- 覆盖升级使用原包名和原证书；不要清数据／卸载来绕过兼容问题。

## 7. Hibi参考与下一阶段候选

原项目：https://github.com/Sallyn0225/eidolon-sc

Hibi：https://github.com/x499806631-commits/Eidolon-Hibi

已有实读审计：`outputs/eidolon-android/HIBI-AUDIT.md`。参考分支my-features，固定提交：

```text
069668ea26abd0e64df142f1ccbc31a02c74eb28
```

角色档案来源与许可证：`HIBI-ATTRIBUTION.md`、`assets/hibi-hearts-source`、`tools/hibi-hearts-manifest.json`。Hibi标注AGPL-3.0，直接复用代码／素材继续保留相应版权、许可证和出处，不把角色／声音素材视为无条件可分发。

已补：日历／认识日期、本地来信／日记与隐藏页、日常行程／睡眠／洗澡、28份角色关系规则、65／85关键剧情门槛与六轮推进、约定、误判申诉等。最新版05:00安排是在这些本地记录上继续适配，不是重做信件系统。

仍未完成的候选：ASMR与环境音、多人共同行程／同组合出差、专门早间事件搭话、回礼纪念品／券、独立角色经历系统、番茄钟／天气／联网、表情包和实时通话。应由用户下一条需求决定顺序，不把本交接当成要求一次性全部实现。

`FUTURE-MODULES.md`已有表情包与实时语音设计：先资源白名单／私有文件／备份与缺图处理，再可选AI选图；通话需要独立会话、麦克风、ASR／VAD、句级TTS队列、取消与音频焦点。当前单条语音不等于实时通话或完整ASMR，也未因规划新增麦克风权限或模型调用。

## 8. 构建与检查

从项目根目录运行：

```powershell
powershell -ExecutionPolicy Bypass -File outputs/eidolon-android/build.ps1
powershell -ExecutionPolicy Bypass -File outputs/eidolon-android/test.ps1
```

build.ps1底层调用tools/build-workmanager.ps1，Gradle8.9／AGP8.7.3，依赖WorkManager2.9.1。本机依赖已缓存；首次环境可能需要下载官方依赖。保留构建脚本对本机Java AF_UNIX问题的TCP回退处理。test.ps1使用当前构建Java类，因此先构建再测试。

1.6已完成：

- Java编译、APK构建、原签名验证、16KB对齐及aapt包名／1.6／code59核对。
- 全部UI JavaScript语法检查；已逐文件核对APK内assets/ui与当前源码一致。
- test.ps1 Java／SQLite离线回归通过，含7项新增ChatEntry规则；新增test_unread_badges.py执行生产未读SQL检查。
- 模拟原生桥接UI通过：unread-entry-ui、inbox-ui、view-position-ui、chat-time-ui、notebook-ui、settings-ui、appeal-ui、role-prompts-ui、diary-ui、writing-plans-ui。

UI检查示例：

```powershell
node outputs/eidolon-android/tests/unread-entry-ui.cjs
node outputs/eidolon-android/tests/view-position-ui.cjs
node outputs/eidolon-android/tests/inbox-ui.cjs
```

这些使用本地mock Native桥接，不会访问模型或手机。Playwright依赖位于 `work/eidolon-sc-main/frontend/node_modules/playwright`，Chrome为 `C:/Program Files/Google/Chrome/Application/chrome.exe`。不要把该frontend依赖目录误认为当前APK源码。

日志：

```text
work/build-1.6.log
work/test-1.6.log
work/ui-1.6.log
```

后两个日志分别记录离线回归及新未读UI；其他UI检查结果亦已在本轮工具输出中确认。截图 `outputs/ui-1.6-unread.png`、`outputs/ui-1.6-chat-arrow.png` 是本地模拟画面，不是真机。

工作目录中有历史一次性patch脚本，例如work/patch-1.6.cjs，**已经应用，不要盲目重复运行**；应直接读取当前文件再编辑。不可根据旧截图／旧脚本的锚点推定当前实现仍相同。

