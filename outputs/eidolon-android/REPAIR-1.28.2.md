# 1.28.2 原因与修改清单

## 已确认与尚未确认

基线为1.28.1/code85，与上次交付源码哈希一致，远端main为4b860e988cdf4016cfceb757fff263cc6a124729，无后续提交；主工程无Git元数据，未重新克隆覆盖。截图显示过期和日程变化两类未发送计划，不是同一个错误，也没有证据证明模型请求失败。

源码原先只有非精确闹钟加普通WorkManager转交；闹钟接收器自行扫描所有角色再入队，没有独立持久定时/周期兜底。回到前台会主动检查，因而前台执行与锁屏后台路径不同。Android省电会延后普通任务，符合用户描述，但未读取该手机系统日志，不能确认是Doze、厂商策略、网络、进程终止还是其他条件导致每一条失效。

另已确认ReachPlans.reconcile一旦原定时段变忙，就直接SKIPPED，即使仍有有效时间也不再寻找空档。该问题可以本地重现，与单纯请求失败不同。本补丁保持既有不补发历史消息规则，只调整尚未发送的有效计划。

## 文件

| 文件 | 修改 |
| --- | --- |
| src/com/eidolon/sc/mobile/ReachScheduler.java | 非精确闹钟+独立持久定时+周期兜底；更早时间保持；API31以上有额度的加急检查/生成；关闭统一取消 |
| ReachWakeWorker.java | 新增短持久唤醒转交，不调用模型，不扫描角色 |
| ReachWake.java | 更早唤醒规则、仅元数据诊断和系统状态读取 |
| ReachAlarmReceiver.java | 记录实际闹钟到达，短转交，不在广播中长时间扫描 |
| ReachPlanWorker.java、ReachWorker.java、ReachPlanCheck.java | 分别记录实际计划/生成开始、系统停止、前台恢复，并在快照提供诊断 |
| ReachPlanRules.java、ReachPlans.java | 有界扫描原有效期空档，同ID延期；只处理尚未请求计划，保留运行/期限/额度守卫 |
| assets/ui/inbox.js | 后台线索、系统限制提示、记录结束时间，继续安全文本显示 |
| assets/update-notices.json | 1.28.2应用内公告 |
| build.gradle、test.ps1、本机tools/build-workmanager.ps1 | code86、版本/输出和测试入口；私有构建脚本不公开 |
| tests/com/eidolon/sc/mobile/ReachWakeTests.java | 更早唤醒、睡眠/课程/免打扰/间距、过期/已请求/有界搜索回归 |
| tests/test_reach_wake.py | 生产SQL与实际ReachScheduler在假Android/WorkManager接口下的执行验证 |
| tests/inbox-ui.cjs | 新诊断、独立请求次数说明、320/915宽与深色、原未读/分页/手动重试保持 |
| tests/test_shared_store.py、test_social_preparation.py | 构建输出路径更新，环境变量依赖保持 |

Java表内省略目录的文件均在src/com/eidolon/sc/mobile。另更新公开便携构建/测试、发布、功能、验证与交接资料。

## 保持的付费和数据边界

所有唤醒仍只汇聚到既有生成Worker，使用唯一工作与原任务条件领取/token/配置revision/日期/关系/会话/同角色通话保护，关系/记忆效果提交路径不改。周期检查不是周期重试模型；只有既有待执行任务可请求。API31以下不启用加急兼容前台服务，以上配额不足降级；不申请精确闹钟权限、不增加常驻保活、不请求麦克风、不记录私聊正文。过期与计费未知任务不自动重放，不升级数据库，不迁移或删除用户记录。

官方边界：[Doze和待机](https://developer.android.com/training/monitoring-device-state/doze-standby)、[WorkManager加急与配额](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)、[允许空闲闹钟的短豁免和调度限制](https://developer.android.com/reference/android/app/AlarmManager#setAndAllowWhileIdle(int,%20long,%20android.app.PendingIntent))。不能将登记成功或假系统通过写成锁屏后台已验收。
