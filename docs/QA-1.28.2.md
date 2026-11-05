# 1.28.2 验证记录

2026-11-05，基线1.28.1/code85，交付1.28.2/code86。SQLite/备份18、包名com.eidolon.sc.mobile、min26/target35、原签名保持。

## 产物

Release与lintRelease通过；v2/v3原升级证书、16KB对齐、包名/版本、全部assets逐字节匹配、Java class新鲜度、Manifest及ReachWakeWorker等新增类实际DEX定义通过。
APK：Eidolon-1.28.2-original-upgrade.apk，6659089 bytes。
SHA256：7c524a28f96fec007ef9f5f58b8cf9b24b5382301dd71fe25d7f7695f2a61083
Git blob：8bc6485d0fb73cb5554d5348a0dde5e5eaf3a489
原证书SHA256：d576e97cae45aca6dedb80064e56a2aae97553fdaa8705904b63420b1d4503db

## 离线和假系统

- 完整test.ps1通过，保留既有聊天/未读/表情权限偏好/取消/计划与03:00/生活状态/备份/并发/通话及ASR握手回归，1.28新增66规则和11组双语一致性、1.28.1的28解析诊断/33实际准备/3组SQL继续通过。
- ReachWakeTests13项（另含240个相邻分钟窗口）：较早唤醒不后推，过期时间不永久保留，07:00至08:30睡眠后可在08:00同窗调整，09:50至11:20课程没有空档不延长，打开手机时已过期不复活，已请求不自动重排，过去due只选新空档，免打扰与间距保持，有界检查不越期限。
- test_reach_wake.py运行实际生产ReachScheduler，假Android/WorkManager仅捕捉注册动作：API35加急、API26普通、唯一生成KEEP、15分钟配置兜底、持久定时同一到期、不重启同一定时、系统拒绝闹钟仍保留独立定时、关闭全部工作等9项。没有真实JobScheduler/AlarmManager/Doze执行。
- 同脚本复用生产DDL/defer SQL的真实本机SQLite：同一未发送机会调整due，id/expires/opportunity/attempts不变；RUNNING/token/attempts不能被延期覆盖。不是Android数据库生命周期验收。
- 23组Chrome实际HTML/JS+假桥通过，新增inbox后台诊断、系统限制和“开始不代表已调用”说明、历史结束时间、320窄屏/915横屏及深色截图检查；保留原草稿/IME/五栏、未读、分页、日程、朋友圈、媒体、通话与明确重试交互。

## 未验证

未读取手机系统/应用私有日志，未更改系统权限，未执行锁屏、整夜休眠、断网/恢复、重启、OEM杀进程或真实模型服务验收。麦克风/输入法/回声/蓝牙/锁屏与真实付费接口表现仍待真机。没有调用真实模型、ASR、Fish或天气服务。实际手机为何错过07:00至08:30仍待新执行时间线与系统状态核对；请求0次只说明未调用模型，不证明全部是后台延迟。

持久兜底、加急与闹钟均受Android/OEM调度和配额影响，无法保证锁屏准点。不复活旧过期计划，不删除旧记录，不重复未知付费请求。公开内容排除签名私有脚本/凭据、私聊/录音、日志/截图和一次性本机脚本；旧APK保留，远端树与提交值见本地publication状态。
