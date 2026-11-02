# 1.22 / code77 全屏通话：审查、实现与验收

## 源码审查与改造依据

在现有1.21/code76工程增量修改，没有克隆或覆盖项目。实际技术栈为Java原生Activity/Service + WebView HTML/CSS/JavaScript，并非Compose/Kotlin界面。minSdk26、targetSdk35；SQLite和加密备份保持v17。工作目录没有Git元数据，发布以已同步的1.21源码快照为基线，保留全部本地已有媒体、日程、角标和聊天能力。

原通话入口位于assets/ui/chat.js的电话按钮，调用calls.js中的callDial；原calls.js同时承载说明弹窗、配置、记录和通话控制。WebActivity.java的callStart/callControl/callStatus桥接CallService.java。音频由原生服务负责，WebView没有麦克风采集权限或浏览器音频通道。

上游实际参考[frontend/src/components/conversations/CallLayer.tsx](https://github.com/Sallyn0225/eidolon-sc/blob/main/frontend/src/components/conversations/CallLayer.tsx)：CallLayer约200行建立全屏层；Backdrop约343行采用放大头像模糊背景和渐变；StandStage约370行仅在角色具备stand资源时渲染立绘；Status约410行显示连接与计时；LineCard约468行展示双语字幕和识别文字；Dock约623行放置实际通话操作。行号以检查时main为准。B站演示请求返回412，未能观看，因此不声称逐帧复刻视频。

本地characters.json只有现有头像，没有可用Spine渲染器或动态立绘资源。复用头像和上游的视觉层次，不引入上游依赖电脑后端的WebRTC/Pipecat架构。独立全屏页面以现有WebView顶层section实现，Activity、前台服务、识别和合成链路沿用原工程。

## 文件及职责

| 文件（相对outputs/eidolon-android） | 改动 |
| --- | --- |
| assets/ui/call-screen.js（新增） | 全屏页面及控制器；一次拨号、实际状态、字幕、时长、静音/打断/路由/挂断、失败重试、恢复与旧回调隔离 |
| assets/ui/call-screen.css（新增） | 深色模糊背景、头像、底部字幕卡和操作栏，窄屏/横屏/安全区/减少动画适配 |
| assets/ui/calls.js | 移除原介绍/拨号弹窗，改为语音通话设置和记录入口；保留原字段及空密钥保留语义 |
| assets/ui/index.html、app.js、settings.js | 加载页面和样式，设置分组/导航及原生状态事件、返回处理 |
| assets/ui/inbox.js | 全屏通话遮挡聊天时不把底层消息自动标为已读 |
| src/com/eidolon/sc/mobile/CallLaunch.java（新增） | 进程内拨号身份、权限等待、启动超时及取消隔离；进程死亡不自动重拨 |
| src/com/eidolon/sc/mobile/CallLimits.java（新增） | 复用原30分钟/60轮上限，统一真实采集状态判定 |
| src/com/eidolon/sc/mobile/WebActivity.java | callUiStatus/beginCallUi/cancelCallUi、权限请求归属、系统返回、页面关闭和系统栏主题；限定角色/会话身份 |
| src/com/eidolon/sc/mobile/CallService.java | 验证启动请求身份；输出recording/autoListen；手动下一轮；语音合成与实际播放分开显示 |
| src/com/eidolon/sc/mobile/CallAsrSettings.java | 新增callAutoListen，默认true；原系统/百炼/HTTP/主动来电配置和密钥仍用原存储 |
| src/com/eidolon/sc/mobile/CallStore.java | 备份允许paused/synthesizing状态，恢复仍结束旧连接；不改数据库结构 |
| tests/calls-ui.cjs、tests/com/eidolon/sc/mobile/CallUiTests.java、test.ps1 | 页面模拟、生产拨号取消和状态规则验证及测试入口 |
| build.gradle、assets/update-notices.json | 1.22/code77和应用内更新公告 |

发布包装器只更新构建目录版本；公开仓库不包含本机签名凭据、私有路径、录音或通话内容。发布说明、交接和功能介绍同步更新。

## 用户操作及状态流

聊天页电话按钮 → 立即进入全屏拨号 → 原生配置/同角色待回复检查 → 必要时系统权限 → 启动原生前台通话服务 → 识别服务实际ready后接通并聆听 → 识别 → 原ConversationEngine生成 → Fish合成 → 实际首音频播放回调后显示播放中 → 完整播放并提交本轮 → 自动或手动下一轮。

拨号、权限和连接阶段不伪造接通时长；接通时间以原生识别ready为准。通话时长来自单调时钟，前端仅平滑显示原生快照，不把页面存在时间算成通话。状态区分dialing/permission/connecting/listening/recognizing/thinking/synthesizing/speaking/committing/muted/paused/failed/ended。字幕复用原双语解析；显示临时识别结果和完整生成台词，不宣称逐字音频对齐。

静音按钮修改原生muted，另显示recording，以区分“未静音”和“当前正在采集”。打断停止本轮识别、模型/合成及播放，复用原取消版本机制；无自动付费重试。扬声器切换调用原AudioManager路由，设备不支持或系统拒绝时沿用原失败/结束逻辑。没有虚构可用设备列表。

返回、挂断或退出通话页会取消拨号或结束当前通话，返回所属聊天；系统返回键还有原生兜底。迟到权限结果和排队Service启动必须匹配CallLaunch身份，不能在退出后重新占用麦克风。旧前端结果由epoch/requestId/callId隔离，不能覆盖新通话。重建只查询现有状态，不再次callStart。WebView不可用时结束通话。锁屏/Home仍由原前台服务和可操作通知管理；这不等于保证厂商强杀后继续通话。进程死亡后旧记录恢复为结束，不自动重连。

## 设置迁移及保留的数据

设置 → 模型与接口 → 语音通话设置：系统识别/百炼/HTTPS、百炼地域模型及密钥、主动来电、自动下一轮、半双工与打断说明、30分钟/60轮限制、数据流/费用/隐私及通话记录均在此处。

原callSttProvider、callSttUrl、callSttModel、callSttKey、callBailianRegion、callBailianModel、callBailianKey、callIncoming和Fish/聊天配置继续读取原位置。密钥为空保留原值，不把密钥回显到页面。新增callAutoListen默认开启，维持旧行为；关闭后完整播放完进入paused，用户点击开始聆听。单次时长和轮数本版维持原限制，不提供没有实现的可调控件。

输入音频交给选定识别服务；文本及必要上下文交给现有聊天模型；台词交给Fish Audio。原始输入录音不持久化，没有虚假的录音保存开关；合成语音仍走原缓存。系统识别是否联网由手机识别服务决定。识别文本不自动提取稳定用户事实；未完整播放回复不计已听见。

## 复用与并发边界

保留CallHttpAsr、CallBailianAsr、SpeechRecognizer、ConversationEngine、VoicePlayer、CallEpoch、CallIncoming以及模型/Fish设置；保留播放完成后事务提交消息和效果、取消旧请求、音频焦点、蓝牙/耳机路由、通知及设备移除处理。文字历史、记忆、关系、生活/日程和带日期上下文仍走同一ConversationEngine，不复制第二套。

同角色已有待回复文字时不能直接拨号，需先等待/取消，检查仍在Activity和Service两处；通话期间同角色并发仍受原CallStore/ReplyManager约束。其他角色通知、主动搭话、日记、图片和桌面角标未另建全局锁。关闭通话层后刷新聊天快照，完整播放过的本轮记录可见。

## 验证结果及范围

- Release构建、APK对齐和原升级签名校验通过，versionName1.22/versionCode77。
- 全套现有离线Java、SQLite和源码契约回归通过；新增CallUiTests覆盖拨号唯一性、权限取消、迟到回调、旧身份隔离、启动超时、真实采集状态和限制常量。它们不执行Android系统权限或真实录音。
- calls-ui.cjs使用真实页面与假原生桥：直接一次拨号、头像/状态/计时/双语字幕、静音/打断/扬声器/挂断、权限拒绝/等待取消、迟到请求、新通话隔离、重建恢复不重复拨号、手动下一轮、设置保存/原密钥保留、记录、来电拒接与屏幕边界均通过。
- 320×568、412×915和915×412页面边界检查通过；竖屏和横屏截图经人工视觉检查。设置、角标、更新公告、日记、生活状态、收件箱、图片及聊天时间分组共8组页面回归通过。
- Android真机：本轮未安装或运行，麦克风权限、焦点抢占、蓝牙、锁屏/旋转及Vivo/iQOO后台仍需真机验收。
- 真实语音服务：未调用付费ASR/聊天/Fish API，真实多轮质量、延迟及费用未测。页面和规则通过不能替代以上两层验收。

当前限制：静态头像，无动态立绘；半双工轮流交流与手动打断，无自然全双工；字幕非逐字同步。没有引入假接通、假进度、假按钮或额外必部署后端。
