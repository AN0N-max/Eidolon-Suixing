# 1.28 验证记录

2026-11-04。基线1.27/code83的交付哈希与主源码一致，远端main仍e9b97d22a8bdb239632a8f20e63d696e07294d79；未克隆覆盖主工程。1.28/code84，SQLite/备份18保持。

## 最终产物

Release构建与完整lintRelease通过。Lint检查中补齐采音初始化时权限撤销的明确SecurityException处理，未屏蔽检查。原v2/v3升级签名、16KB对齐、包名/min26/target35/版本核对通过；全部打包assets逐字节匹配实际主源码，Java class新鲜度、所有Manifest组件及新增解析/时间/通话类的实际DEX定义通过。

APK：Eidolon-1.28-original-upgrade.apk / 6650897 bytes
SHA256：c9c7bb743b3203bd3173fcd57c8bad2ef44eed6ded4a21460740f7e8828a847c
Git blob：61c6a0e860ccf215a8b079af4d0bd6c6582f0318
原证书SHA256：d576e97cae45aca6dedb80064e56a2aae97553fdaa8705904b63420b1d4503db

## 离线与假服务

完整test.ps1最终退出0，保留既有聊天、时间、日程、03:00、生活状态、关系/记忆合并、备份、表情权限/系列偏好、请求有界并发、未读、取消与通话回归。ContinuationTests新增66项检查：语言嵌套/残缺/转义/引用/代码/隐藏与朗读，午夜/03:59/04:00共用睡眠边界及旧派生事件校正、不重放效果，时区、远期活动、保守时间冲突与正常午睡/夜班/回忆/引用，延迟送达，停顿继续/手动结束/采音上限/轻声阈值、拨号等待与旧身份隔离。

11组Java/浏览器解析结果逐字段一致，包括未闭合代码围栏和中文夹日文引用。扩展既有BailianTests使用生产适配器、合成PCM、可控时钟和假WebSocket：正常finish-task/task-finished后正常关闭；第一句结束不结束任务；多个sentence_id合并，临时覆盖/重复忽略，思考停顿后续说且只返回一次，手动结束，结束确认超时或最终文字后断线保留文字待确认，鉴权/服务错误安全码、无声/任务启动超时、上传背压、取消和迟到回调。没有正常结束确认时不能报告完整成功，不自动新增上传或模型请求。

原SQLite生产SQL夹具及条件提交、配额、恢复、不重放未知请求、重复播放/关系记忆幂等回归继续通过。不是Android生命周期或真实数据库设备验收。

## 页面

23组桌面Chrome实际HTML/JS＋假桥全部通过：calls/settings/inbox/unread-entry/chat-time/social/schedule-error/schedule-rollover/reply-background/sticker-packs/view-position/life/media/release-notes/desktop-badge/diary/notebook/writings/writing-plans/repair/continuation/role-prompts/reach-settings。覆盖1.27同一textarea、草稿/光标/模拟组合输入/焦点、历史位置、原生IME事件驱动导航和五栏保持；新增隐藏嵌套日文、安全HTML文本、拨号等待/页面重建不重复拨号、说完了按钮、异常文字确认、四种通话设置及窄屏/横屏/深色布局。截图检查通过。日记发现中文夹日文词不能整句隐藏，已修复并重跑。

## 未验证与推断边界

ADB授权设备数量0；未调用真实付费模型、ASR、Fish或天气服务。麦克风、中文输入法、扬声器回声、蓝牙/耳机、锁屏、Vivo/iQOO后台、真实接口协议表现/计费/延迟仍待验证。停顿参数是初始值，系统分段能力及最多8次正常续听的间隙需真机验证，不能写成无缝识别已验收。

时间检查是保守规则，不能保证消除所有语义错时；纯汉字日语/中文模糊混写不能可靠自动区分。原18次ASR统计失败不能仅凭截图或假服务归因到客户端，后续需真实task_id/request_id与错误码核对。原始输入音频不保存，诊断不含密钥、私聊或服务错误原文。

## 发布安全

原签名APK、源码、公告、功能、验证及交接共同发布；基于原远端树增量创建，保留18个远端旧APK。私有签名配置、实际凭据、私聊/录音、测试截图、一次性本机脚本不公开。main更新使用expected-head约束，更新前后核对树与APK blob。旧Downloads安装包不删除。
