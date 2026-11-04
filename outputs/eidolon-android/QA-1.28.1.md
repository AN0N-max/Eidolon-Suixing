# 1.28.1 验证记录

2026-11-04。主源码基线1.28/code84与上次交付源码哈希一致；远端main核对为2e9b3479e6596e07110c83098814ebcc375f471b，树809828c518470cf505456bdd9fc2351ee12244f2。无Git元数据的主工程未克隆覆盖，publication仅用于发布暂存。

## 产物核验

1.28.1/code85，SQLite/加密备份18不变。Release及lintRelease通过，原v2/v3证书、16KB对齐、包名/min26/target35核对通过。全部打包assets逐字节匹配主源码；Java class新鲜度、Manifest组件与SocialReply等实际DEX定义通过。

APK：Eidolon-1.28.1-original-upgrade.apk，6659089 bytes。
SHA256：136369b3e851d4ff7c2561f562012832de1236d97f2f097b68a571291cbb897f
Git blob：5aeea9ce725026acb887733ac6ab8857ccaad982
原证书SHA256：d576e97cae45aca6dedb80064e56a2aae97553fdaa8705904b63420b1d4503db

## 离线结果

- 完整test.ps1退出0，既有聊天/表情权限和系列偏好/未读/视图位置/时间/日程/生活状态/备份/请求并发与取消/通话及ASR结束握手回归继续通过，1.28新增66项与11组Java/浏览器双语一致性保持通过。
- 新增SocialRepairTests28项：完整围栏/字符串/单对象数组/已知包装、嵌套双语、转义及花括号内容、截断/多对象/缺字段/长度、安全错误、准备与返回阶段分开、字段/代码位置不含正文、确定未请求与未知区分、旧记录不变及备份历史文本保留。
- test_social_preparation.py运行实际生产SocialEngine.preparePost/diagnose及原始assets，JVM只读数据库/系统适配，无网络处理器。29角色原始资料全部通过；坏可选天气/生活/近期参考可隔离；缺少地点lat明确失败在prepare_places并给出字段及PlacePool.all位置；共33项。任何URL连接尝试会失败；不执行模型、天气或任务请求登记。
- test_social_admission.py的3组真实SQLite夹具通过：旧确定未发送仅调统计不删记录、已发送/未知仍算；准备失败不登记请求；有界额度并发竞争、重复身份/旧取消token拒绝。该测试复用生产DDL/SQL，不是Android数据库设备生命周期验收。
- 23组桌面Chrome实际HTML/JS加假原生桥均通过：calls/settings/inbox/unread-entry/chat-time/social/schedule-error/schedule-rollover/reply-background/sticker-packs/view-position/life/media/release-notes/desktop-badge/diary/notebook/writings/writing-plans/repair/continuation/role-prompts/reach-settings。新增本地检查不触发socialGenerate/socialRetry/weatherRefresh/socialCheck，4页面、320/412/915宽、横竖/深色、诊断与重试不重叠。相关最终social复跑、截图检查通过。

便携发布副本另行编译及完整test.ps1通过，使用环境变量提供JDK、Android SDK及测试依赖；修正旧数据库测试的本机工具路径假设。公开副本朋友圈页面测试亦通过。

## 尚未确认

用户确认19:02失败是在安装1.28后生成。本机USB调试未授权，用户要求先离线修复；未读取手机私有数据库/异常，也未触发真实生成。不能确认原缺失字段、真实模型返回或所有失败是否同一根因。29角色标准资产通过不能作为手机数据正常的证据。新增本地检查用于缩小范围，不承诺所有损坏数据会自动恢复。

未调用真实付费模型、ASR、Fish或天气服务。Android麦克风、中文输入法、扬声器回声、蓝牙、锁屏/OEM后台、真实接口质量/费用仍待真机验证；本补丁未改这些模块，不把桌面假桥写成真机通过。免费额度或计费未知请求不自动重复。

## 发布

源码、原签名APK、公告、功能/修改/验证及交接共同同步GitHub。基于已核对远端树增量提交，保留19个旧远端APK及本地1.28。私有签名配置、密钥、聊天/录音、日志/测试截图及一次性本机脚本排除。提交值和远端核验见本地GITHUB-PUBLICATION-STATUS.md。
