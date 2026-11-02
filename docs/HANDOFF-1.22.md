# Eidolon 随行 1.22 开发交接

继续当前outputs/eidolon-android工程，不重新克隆覆盖。本版1.22/code77，Java原生+WebView，min26/target35，SQLite及加密备份v17不变，原升级签名。1.21多模态系统及此前电话/百炼/生活系统保留。完整审查见CALL-UI-1.22.md。

新增call-screen.js/css，电话入口直接全屏拨打。calls.js只负责设置及记录；设置→模型与接口→语音通话设置。界面以真实原生快照驱动，接通时长来自单调时钟，字幕沿用双语解析，静态头像无Spine资源，预留portrait渲染节点。没有复制聊天/音频链路。

CallLaunch是进程内拨号/权限身份栅栏，WebActivity负责取消、权限归属、页面重建/返回及主题；Service启动前验证requestId，退出后的排队启动无效。callId/turnId/CallEpoch继续保护本轮，前端callEpoch防旧回调污染新通话。进程死亡不重拨，旧记录恢复结束。合成阶段synthesizing，VoicePlayer真实started之后才speaking。CallLimits统一30分钟/60轮和实际recording指示。新增callAutoListen默认true，关闭后完整播放进入paused；旧设置密钥位置不变。

原同角色待回复检查、未完整播放不提交、ASR低置信拒绝、取消、焦点/路由/通知继续沿用。全屏遮挡时不确认底层消息已读。Home/锁屏保留原前台通话，返回/退出页面挂断。真实厂商后台行为不保证。

Release/原签名/对齐、完整离线套件、新增CallUiTests和calls-ui.cjs通过，另8组现有页面回归通过；竖横屏已查看截图。未在本轮安装手机或调用真实付费API。实际麦克风/蓝牙/焦点、锁屏、旋转与真实多轮质量/延迟/费用待真机和获授权服务验收。当前仍半双工、静态头像、完整台词字幕，无逐字对齐。后续不能把模拟结果写成真机或真实服务通过。
