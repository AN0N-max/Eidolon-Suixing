# 1.26 验证记录

2026-11-04；1.26/code82；DB/备份18。原工程增量改造，未克隆覆盖。Release构建通过，APK v2/v3签名与原升级签名相同，zipalign16KB检查通过，包名/min26/target35及82/1.26核对通过。所有APK assets与主工程逐字节相等，全部Java class时间不早于源码，Manifest应用组件及新增社交/天气/城市/队列类在实际DEX中存在。公开文本统一LF；与本地主工程的换行差异不代表业务改动。

APK：Eidolon-1.26-original-upgrade.apk；6342848 bytes。
SHA256：e49451c257f2541f632a866899e56b60091690a5282248101d2b933fafc21134
Git blob：4656100a0706d2907bb997678610f1f5a67c26b1
证书SHA256：d576e97cae45aca6dedb80064e56a2aae97553fdaa8705904b63420b1d4503db

完整现有test.ps1离线套件退出0；新增50项队列/类别/取消规则检查、446项朋友圈/天气/地点/备份/JWT检查通过。28名角色案例是共享准入规则模拟，不是28次真实模型调用或Android任务验收。BC签名用独立Ed25519验证器校验，未读取用户密钥。ChatStore连接池检查的classpath从历史1.22更新到当前1.26，并单独重跑通过，1922 leases/16线程；仍为假Android helper。

生产DDL与SQL在本地SQLite夹具执行：17→18追加8表保留受保护行，迁移中断回滚；同任务8并发只记一次；不同角色并发不超额度；失败/进程恢复不重新开放未知付费请求；删评论后迟到UUID/token不能写新评论；waiting/failed与collecting/generating分开判忙；日程新旧token隔离；恢复式账本合并及清除任务后既有行保留。不是AndroidSQLiteOpenHelper实机迁移。

实际HTML/JS+Chrome假原生桥：朋友圈/城市/天气/设置通过；可见ID与回复尾部确认已读、弹层遮挡、分页、点赞评论、过期读响应、自动互动更新、草稿/焦点/caret/视图位置、明确取消付费确认、不回显凭据、不自动抓天气、取消定位/过期城市搜索、城市中心保存、档案未保存草稿往返、空参与选择、4路由×3布局与深色。截图已视觉检查，测试文字含刻意的XSS字面量，未作为用户内容执行。

既有15组页面回归通过：settings、desktop-badge、release-notes、diary、life、inbox、media、chat-time、calls、sticker-packs、view-position、unread-entry、reply-background、schedule-error、schedule-rollover；最终公告修改后单独重跑通过。

未调用任何真实模型、ASR、Fish、天气/城市服务，未连接手机。Android后台/开机/网络切换/进程生命周期、Vivo/iQOO、权限与定位、真实服务套餐可用性、延迟/费用、多轮质量仍需验收。不把纯规则或假桥测试写成实机通过。生图与手动排工作暂缓。旧失败记录需明确手动重新安排，不补造丢失的模型返回。

公开发布排除私有构建/签名脚本、用户资料/凭据/录音、视频参考文件和第三方表情图片；基于原main树添加文件，保留16个远端历史APK的hash/size，发版时再次核对。
