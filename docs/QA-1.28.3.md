# 1.28.3 验证记录

2026-11-05。所有下列“通过”均为本轮实际执行；不借用旧版本结果代替。

| 验证 | 结果与范围 |
| --- | --- |
| 主源码基线 | src/res/assets与1.28.2交付哈希一致；修改前快照保留 |
| 完整原生/SQL离线入口 | test.ps1通过；含聊天、双语、语音规则、通话、时间、日程、备份、关系/记忆、事务及调度既有回归 |
| 新ChatRepairTests | 43项通过，调用生产解析器、结构投影及实际ModelClient；自定义假HTTPS捕获最终请求体，未连接真实服务 |
| 解析一致性 | 18组native/browser结果相同，覆盖污染、残缺/重复/嵌套标签、姓名、引号、代码与安全HTML |
| 页面回归 | 24组通过：calls/settings/inbox/unread-entry/chat-time/social/schedule-error/schedule-rollover/reply-background/sticker-packs/view-position/life/media/release-notes/desktop-badge/diary/notebook/writings/writing-plans/repair/continuation/role-prompts/reach-settings/chat-repair |
| 新chat-repair假桥 | 28项通过：隐藏与可读正文、真实textarea身份/焦点/选区/组合事件、视口展开收起、输入区增高、剧情参考、历史锚点/新消息、导航开关、长按取消/多点/长时抑制/等效入口/缺资源 |
| 界面检查 | 320×720、915×412和412×840暗色，截图为合成测试聊天；已人工式视觉核对布局，无私人截图发布 |
| Release / lintRelease | 均通过；lint原有警告不等于真机验收 |
| 安装包 | 包名com.eidolon.sc.mobile，version1.28.3/code87，min26/target35 |
| 签名与对齐 | 原v2/v3签名通过，16KB对齐通过 |
| 内容一致 | APK全部assets逐字节对应实际主源码；生产Java class新鲜度和Manifest组件/关键类DEX定义通过 |

关键时间用例包括20:08→22:54（9960秒）、用户与角色不同时区、跨日“明天”、23:59生成/00:01投放、未知旧时间、正文伪造时间、整轮长度裁剪及摘要中间来源。只送入已提交的可见历史；没有查询待投放答案作为已收到内容。生成、计划、投放、阅读确认不混为一谈。

长按读取不发mediaSend；随后click抑制、下一次短按只发一次；长按后继续按住超过既有超时也不误发。查看不调用mediaAnalyze/mediaUpdate或付费接口。既有事务/取消/重复提交/未知结果测试继续通过。

未验证：Android真机IME及厂商WebView、真实中文输入法组合行为、触摸长按/系统上下文菜单、相册/旋转设备差异、真实语音合成与播放质量、麦克风/回声/蓝牙/锁屏、真实模型的时间指代理解、整夜充电背景任务。桌面视口和合成事件只验证页面逻辑，不证明手机表现。

APK字节：6667355。

APK SHA256：`b33424703e16936a2dc73c3b58ed5e812f48e0cd1692db049e67cc019be2857e`。

证书SHA256：`d576e97cae45aca6dedb80064e56a2aae97553fdaa8705904b63420b1d4503db`。

未调用真实付费模型、ASR、Fish或天气，无手机安装/调试操作。旧APK保留。公开便携副本的最终检查与发布提交见交接记录。


最终公开便携副本已重新编译，并通过完整离线套件（新增原生43项、解析一致性18组）；同版公开脚本的新聊天假桥28项通过。最终原生时间边界调整后再次通过主源码lintRelease。所有UI更改之后的24组页面回归均通过。仍非真机/真实服务验收。
