# Eidolon 随行 1.20 开发交接

继续当前outputs/eidolon-android工程，不重新克隆覆盖。当前1.20/code75，min26/target35，SQLite/备份v16，原升级签名。此前电话架构见1.19交接及CALL-SYSTEM-1.19.md；本版增量详见BAILIAN-CALLS-1.20.md。

新增BailianAsrProtocol、CallBailianAsr、CallAsrSettings；复用CallService、CallEpoch、CallClock、ConversationEngine、VoicePlayer。OkHttp4.12.0直接连接选定百炼地域，独立Vault密钥，不需要电脑后端。原系统/HTTPS识别配置自动沿用。版本、更新公告、构建和测试路径同步1.20。

不自动重试或跨地域探测，不保存原始声音。识别临时文本只显示，最终句尾才调用原聊天。取消/静音/打断会停止录音和socket；未完整播放回答不计完成交流。真实付费API、真机音频及后台尚未验收，延迟和费用未测；不能用离线测试结论代替。主动来电默认关闭保持原逻辑。
