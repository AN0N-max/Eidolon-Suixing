# Eidolon 随行 1.27 交接

当前1.27/code83；数据库/备份18未升级；Java Android原生+WebView；min26/target35。主源码仍为现有eidolon-android，不能重新克隆覆盖；publication不是开发主目录。接手基线1.26/main=0edeb2df916eaa5ddd1e05186d69396c48585740，src/assets/res与上次交付无差异，先备份再增量修改。发布时复查main并用expected-head更新，保留17个远端旧APK。

本轮完成严格消息控制语法/历史只读兼容、键盘导航设置和稳定编辑器、五栏一级朋友圈、社交错误分类及新身份手动重试、日程/聊天独立有界并发、普通语音气泡状态。详细原因与边界见RELEASE-1.27；文件索引REPAIR-1.27；测试见QA-1.27。并未获取手机原始响应，旧泛化错误不可事后恢复。

关键：ReplyMarkup只识别保留语法，文本节点安全显示；不执行模型HTML。MessagePresentation只读历史，原消息不批量改写。表情继续同一候选、角色/系列权限与资源验证，TTS过滤内部标记。chat-updates.patchChat保留实际textarea，不恢复/重建组合输入，不因新消息强行跳底；更早消息按钮、参考区和未读回归继续保持。键盘只读原生IME insets，设置默认开启，真机键盘未验收。

RequestSlots总体HTTP4、后台2；前台执行器2，独立ModelClient和id/revision/token。Jev判断同样持有槽。ScheduleEngine独立running，不因聊天yield；manualTicket/activeManualTicket/cancelledManualTicket区分排队、执行与迟到取消。关功能/恢复/OS stop仍取消对应任务。Social提交不按其他任务busy丢结果；保留自己的token/配置/过期/资格保护，错误只落脱敏分类，不落prompt/响应/URL。取消和未知结果不自动重试，手动重试创建新UUID并计额度。

保留：同会话合并和顺序、同角色文字/实时电话冲突、完整播放确认后才提交关系/记忆、事务及幂等、进程死亡不重放付费、原媒体/缓存、桌面角标和总设置归属。普通语音只有状态反馈迁移，实时通话全屏与系统通知未移除。无生图、无手动排工作扩展。

继续原升级签名，私有签名脚本不可公开。源码、APK、更新公告、功能与交接同步GitHub，旧包保留，本地APK优先。没有真实模型/ASR/Fish/天气请求，没有连接手机；键盘/输入法、系统栏、后台和音频路由必须保留实机待验证。不要将假桥或生产SQL夹具当作Android生命周期验收。

补充：手动写信/日记和记忆整理的停止操作限定各自UI所属线程/客户端，默认聊天停止不再顺带取消其他写作。某角色的社交任务等待时只延后自身，继续检查其他角色。

最终APK SHA256：de3b332908b482a33d4bf940544bb3801972bf5fa9b1c2da6072a01e94ba1674。Release/原签名/16KB对齐、完整离线、31新增检查、20组页面回归通过；真机/真实服务未验收。
