# 1.7 主动搭话计划检查与重排修复

基于现有1.6/code59源码继续，未重新拉取项目。版本1.7/code60，包名com.eidolon.sc.mobile、原升级签名、SQLite与备份v13保持。

## 修改

截图中当天0项安排却有角色显示可用、检查时间停在昨天。缺少手机日志，无法确定当次后台任务未运行还是异常；代码确认手动重排只入队WorkManager并立即返回旧页面，无任务时只约到次日05:00。

- 本地检查用独立执行器，页面打开、刷新和重排等待实际完成并返回结果；返回前台也执行检查，不依赖模型执行器或WorkManager排队。检查不调用模型、不增加请求计数。
- 重排保留已完成/失败历史及请求次数。正在主动生成时要求等待，不中断请求；未知计费结果仍禁止自动重发。
- 显示最近检查、结果和错误类别。未安排原因区分固定时间已过、关系/固定间隔跨天、作息、免打扰与全局间距。预留额度和创建计划使用同一计数。
- 按既有backgroundMinutes安排定期本地检查，并保留05:00检查；AlarmReceiver异步完成本地检查，联网生成继续交给WorkManager。
- next()直接返回最早due，修复过期due被替换为expires导致等到过期才检查的问题；到期未过有效期任务约30秒后再核对。
- 唤醒失败清除旧时间并显示错误；页面按配置时区显示，识别过期记录。

没有重做关系/随机时间算法，不降低免打扰、作息、关系、每日额度或未回复限制。条件确实不满足时仍可为0项。

## 验证边界

构建与回归完成后追加结果。离线Java、生产SQL与模拟Native桥接不能替代Android WorkManager/AlarmManager、进程恢复或OriginOS实机验收。本轮不安装手机、不调用真实API。

## 已完成验证

- build.ps1成功；原升级证书、16KB对齐通过；aapt确认com.eidolon.sc.mobile、1.7/code60、min26/target35。
- test.ps1全部Java/SQLite离线回归通过，新增14项本地检查/唤醒规则以及生产SQL重排与未知请求结果隔离。
- plan-recovery-ui、plans-ui、inbox-ui、unread-entry-ui、view-position-ui、settings-ui模拟桥接检查通过；全部UI脚本语法通过。
- APK内所有assets/ui文件逐一与当前源码一致。
- 日志work/build-1.7.log、work/test-1.7.log；截图outputs/ui-1.7-plan-recovery.png是模拟界面，不是真机。
- APK SHA-256：BB26887899B80E8598B610255E087314D0F7AADD0D4018CB4341EE547338BE2C。

没有手机安装、真实模型调用或OriginOS系统后台全链路验收。
