# USB 调试记录 · 2026-10-23

设备 V2463A（用户 iQOO Neo10 Pro+），OriginOS 6，原包名 com.eidolon.sc.mobile。现场安装 0.9.24/code50。

通知 POST_NOTIFICATIONS 为 granted=true，POST_NOTIFICATION AppOps 默认 allow，应用内 NotificationManager 返回已允许，悬浮窗 AppOps allow。不能用 vivo 的 moreNotificationsEnabled=false 字段断言通知总开关关闭；此前该判断已更正。

3 秒本地测试：通过 ADB 点击后切到桌面，截图 usb-local-test-0924.png 清楚显示弹窗。系统窗口为 TYPE_APPLICATION_OVERLAY/2038，消息通知已发布到 reach-history-v2/importance2。此测试使用 ReplyTestService，不证明正式回复生成过程通过。

用户单独授权一条真实聊天调试消息。系统事件显示：

- 23:33:06.360：Eidolon onPause，切后台；正式 ReplyWorkService 已启动，isForeground=true，id9285。
- 等待期间未捕获正式回复弹窗；后台保护仍运行。
- 23:35:37.317：系统记录 Eidolon onResume。
- 23:35:39：应用投放记录为“回复已投放到应用内”。
- 23:36:20：调试器重新打开应用检查，正式回复已经存在；系统仅留下此前的本地测试通知。

因此此次正式投放发生时应用在前台。由于 0.9.24 未保存回答生成完成时间和预计投放时间，无法据此确认先前是模型耗时、行程等待还是后台停顿。不能宣称正式后台故障已修复。

0.9.25/code51 增加本机最近40条时序诊断（不记录正文、接口、密钥），以及使用正式 dataSync 服务的5秒本地测试。测试记录仍保留 test=true，投放由原事务消费，不写正式聊天及关系；不调用模型、不覆盖重启恢复/长等待验收。此版本需覆盖安装后继续 USB 验证。

构建、v2/v3签名、16KB对齐、JS语法、现有离线Java/SQLite回归、15项后台时序检查及当前分组设置/诊断界面模拟检查通过。包名原样，versionName=0.9.25，versionCode=51，签名SHA256为 d576e97cae45aca6dedb80064e56a2aae97553fdaa8705904b63420b1d4503db。

APK SHA256：2ED95DD3F1240E7C56DD6289162C659DEC18A4B6CDDEB03DB130A7FC09DC7B90。

## 2026-10-24 继续 USB 验证

用户授权覆盖安装并继续调试，并允许本轮少于10次 API 调用。0.9.25及随后0.9.26/code52均通过ADB原签名覆盖安装，firstInstallTime仍为2026-10-22 13:56:37，旧聊天存在。新增两条正式聊天，每条记录后台分析、关系判断、聊天回答三个请求阶段，共6次明确的调试请求；未再重试。

0.9.25的5秒正式后台服务本地测试在23:58:35真实显示桌面卡片并发布通知，截图 usb-guard-test-0925.png。正式测试另捕获窗口切换时序：桌面00:00:19.993恢复，但Eidolon onPause到00:00:20.962才执行，回复在20.155走应用内路线。0.9.26加入窗口焦点、onUserLeaveHint提前标记及250ms分流稳定等待；此修复仅针对切页面边界。

0.9.26正式测试仍出现回应用才继续。系统事件现已确认整进程冻结：

- 00:03:54.934：正式dataSync前台保护服务启动。
- 00:03:55.007：应用退到后台，窗口随后失去焦点。
- 00:04:00.926：开始请求聊天回答。
- 00:04:01.502：am_app_frozen [0,10371,com.eidolon.sc.mobile,from fast_freezer]。
- 00:05:36.805：am_app_unfrozen，reason=resume top activity；同一时段应用恢复并完成回答暂存、投放。

冻结/解冻事件保存于 outputs/usb-eidolon-freezer-evidence.txt。普通后台AppOps为allow、待机桶ACTIVE，通知权限允许；这些不能阻止当前fast_freezer。已有前台服务仍被冻，不能将问题仅归为模型耗时或弹窗权限。尚未确定触发冻结的具体厂商设置或其他管理工具，不自动修改全局冻结/专注/时间管控设置。0.9.26不能宣称已解决整进程冻结。

0.9.26构建、签名、16KB对齐、现有离线回归通过；包名及证书不变。APK SHA256：931FB7525BAB25F8AED963D0DCABB05CA5DC5A69D8BEC7A0ED9F2B6285639746。

## 用户反馈及1.0开发

在Eidolon后台耗电管理发现原选项为“智能控制后台耗电”，调试转向只为Eidolon选择“允许后台耗电”。随后用户明确反馈“成功了”，并要求主动搭话参数可配置及版本改为1.0。这条反馈作为本次设备问题的用户验收；不宣称所有OriginOS条件或所有设备均已解决。1.0的主动搭话设置开发未新增真实API调用，也未自动覆盖设备上的0.9.26。
