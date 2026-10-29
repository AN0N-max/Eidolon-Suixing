# 1.10 主动搭话等待条件与Vivo桌面角标

基于现有1.9/code62继续，未重新拉取项目。版本1.10/code63，包名com.eidolon.sc.mobile、原升级证书、SQLite和加密备份v13保持。

## 主动搭话

按用户最后指定，仅取消两项门槛：有待处理聊天回复时也可主动搭话；上一条主动消息未回复也可继续安排。没有额外缩短默认关系间隔、提高每日额度或改动用户自定义参数。

- 筛选、执行前、最终投放三条路径均去掉pending/未回复检查，以及阻挡正常聊天生成期间主动任务的全局generating检查。
- 已完成的主动消息不再消费该角色当天所有后续机会；时间间隔满足后可以创建新任务。仍保持每角色一条活动计划、总额度/角色额度/请求预算，以及任务领取的幂等条件。
- 失败和未知结果仍阻止盲目自动重发。生成期间会话有新消息、配置变化、过期、免打扰或作息不合适等投放复核保留。
- 普通待回复记录不因主动任务被取消、消费或清空；来信与申诉的待回复保护没有改动。提示词说明主动消息不替代正式聊天回复、不催促未回复者。

## 桌面角标

原1.6已有Notification.setNumber/showBadge，这次补Vivo/iQOO兼容广播路径。真实聊天未读总数通过launcher.action.CHANGE_APPLICATION_NOTIFICATION_NUM提交给com.vivo.launcher，附本应用包名、WebActivity类名和notificationNum，不发送正文、角色或密钥。标准通知路径继续保留，每角色一条通知，不额外叠加汇总通知。

- 新消息投放、系统通知发布、阅读、删除、前后台切换、系统恢复后核对；独立角标队列不等待聊天模型执行器。全部读完发送0；本地test=true不会伪造正式角标。相同计数短时间合并，手动同步可重新提交。
- 新入口：设置 → 提醒与后台 → 桌面未读角标。显示实际聊天未读数、通知/通知类别开关、兼容提交状态；可按真实未读同步或打开系统通知设置，不生成消息、不标已读、不调用模型。
- 公共接口参考ShortcutBadger的VivoHomeBadger，Apache-2.0出处与声明保存在assets/licenses/shortcutbadger-notice.txt。
- 广播提交不等于启动器实际接收或展示。OriginOS6可能受通知、桌面角标设置、系统兼容/白名单限制；数字、红点、位置由启动器控制，不能承诺与QQ完全相同。

## 验证

- build.ps1及全部test.ps1离线回归通过。新增生产SQL验证普通pending四种状态不影响新机会、已完成但未回复允许新机会、普通回复保持、重复领取受阻、未知失败不自动重试；八项Vivo品牌/计数/通知关闭/归零规则通过。
- desktop-badge-ui模拟桥接检查通过：入口、真实计数展示、同步、归零、权限诊断、系统设置链接、深色/窄屏、不发送已读确认或模型请求。
- reach-settings-ui、plan-recovery-ui、ui-performance-ui、inbox-ui、unread-entry-ui、settings-ui、view-position-ui通过。全部UI脚本语法通过。
- 原签名、16KB对齐、aapt包名/1.10/code63通过。APK内所有UI文件及许可证声明与源码一致，DEX包含LauncherBadge。
- 日志work/build-1.10.log、work/test-1.10.log、work/ui-1.10.log；截图outputs/ui-1.10-desktop-badge.png为模拟画面。原文件备份work/source-backup-1.9-before-1.10。
- APK SHA256：3BEAA29169F362EF30D201FBA791D3F2DFC2FFB337045919F69624B0401A5803。

本轮没有安装手机、调用真实API或实际发送Android广播；Vivo/OriginOS桌面角标效果必须由手机验收，不能将mock/编译通过表述为QQ同款角标已实测成功。
