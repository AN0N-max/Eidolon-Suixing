# Eidolon 随行 1.19 开发交接

在既有Android工程outputs/eidolon-android上继续，不重新克隆覆盖。当前1.19/code74，包名com.eidolon.sc.mobile，min26/target35，SQLite与备份v16。使用已有私有构建流程和原升级签名，公开仓库不含升级私钥。历史1.6交接仅作历史资料。

电话主要模块：CallService、CallStore、CallEpoch、CallClock、CallHttpAsr、CallAudioText、CallIncoming及其接收器/失效Job；前端calls.js。复用ConversationEngine、VoicePlayer和既有角色/时间模块。完整架构与验证矩阵见CALL-SYSTEM-1.19.md。

原有离线回归、23项电话Java检查、电话SQLite和7个界面模拟通过。没有调用真实付费服务或安装本版到手机；真机麦克风、焦点/蓝牙/锁屏和真实服务延迟、成本尚未验收。先用独立假配置验证，不读取或公开用户录音、密钥与私人文本。

主动来电默认关闭；当用户显式开启时，最多每日一次且24小时冷却，复用实际到期但未执行的主动联系机会。必须接听后才启动麦克风与模型。未完整播放的回答不提交，关闭/打断取消本轮。备份恢复先结束当前电话，恢复记录不恢复连接。
