# 1.28.5 实际原因与修改文件

检查基线：主工程为1.28.4/code88，src/res/assets与上次交付SHA清单完全一致，没有额外本地修改；远端main仍为578dfc92b49f1f8b627dd60b5a6418e6016d7a22，没有待合入后续。直接在原工程增量实施，publication仅发布暂存。

确认的缺口：已有生活目标只投影标题、阶段数量和状态，缺少方法、困难、最近具体进展、来源链和已分享版本；用户建议没有可验证的“意向→绑定活动→尝试→结果”关联。Recall.write原先在最终回答请求之后才准备新记忆，当前轮会继续读取旧理解；原删除逻辑还会复活被新纠正替代的旧事实。自动记忆检索未默认隔离私人角色归属，旧总结没有用户理解版本边界。原生同语言规范化及messageText隐藏后的容器复用会丢失可见中文分段；CSS依赖语言元素相邻关系。另发现写作错误后的旧自动重试可能触及未知付费尝试，需要持久尝试边界。

ContinuityLife复用goals/events，附加有来源的阶段经过和exchange关联；程序推进仍由LifeData原规则决定，模型仅能提出可校验的对话效果。ContinuityContext提供同一有界投影、节奏/状态依据和已用话题控制；ContinuityStore接到原提交事务、读取与备份重映射。UserUnderstanding仍操作原memories数组，以用户原话保存条件/版本，先预览进入实际请求再随投放提交。个人效果继续用原PersonalMerge，memoryEpoch的既有冲突保护用于新修正，不复制关系或记忆提交系统。

WritingEngine/NotebookEngine只附共同参考，不附聊天候选协议，保留各自信件/日记/摘要格式。LetterPlanStore及WritingStore在原事务中复查，未知写作请求有持久防重发标记；DiaryStore保持真实尝试数。ChatStore继续在正式投放和通话完整播放资格之后提交；旧任务和未知请求不会被复活。

DialogueText新增仅用于显示的paragraph和逐气泡投影，ReplyText提供规范化之前的安全显示来源，MessagePresentation读取兼容而不改记录。前端同样解析段落边界，messageText按边界选择容器、CSS直接给中文段间距；朗读算法/缓存键未更换。

实际修改/新增源码及测试：

- `src/com/eidolon/sc/mobile/BackupValidator.java`
- `src/com/eidolon/sc/mobile/ChatStore.java`
- `src/com/eidolon/sc/mobile/ContinuityChanged.java`
- `src/com/eidolon/sc/mobile/ContinuityContext.java`
- `src/com/eidolon/sc/mobile/ContinuityLife.java`
- `src/com/eidolon/sc/mobile/ContinuityStore.java`
- `src/com/eidolon/sc/mobile/ConversationEngine.java`
- `src/com/eidolon/sc/mobile/DialogueText.java`
- `src/com/eidolon/sc/mobile/DiaryData.java`
- `src/com/eidolon/sc/mobile/DiaryStore.java`
- `src/com/eidolon/sc/mobile/LetterPlanData.java`
- `src/com/eidolon/sc/mobile/LetterPlanStore.java`
- `src/com/eidolon/sc/mobile/LettersEngine.java`
- `src/com/eidolon/sc/mobile/LifeData.java`
- `src/com/eidolon/sc/mobile/LifeHistory.java`
- `src/com/eidolon/sc/mobile/LifeStore.java`
- `src/com/eidolon/sc/mobile/MemoryNotebook.java`
- `src/com/eidolon/sc/mobile/MessagePresentation.java`
- `src/com/eidolon/sc/mobile/NotebookEngine.java`
- `src/com/eidolon/sc/mobile/PersonalData.java`
- `src/com/eidolon/sc/mobile/ReachExecution.java`
- `src/com/eidolon/sc/mobile/Recall.java`
- `src/com/eidolon/sc/mobile/ReplyBasis.java`
- `src/com/eidolon/sc/mobile/ReplyText.java`
- `src/com/eidolon/sc/mobile/UiSnapshots.java`
- `src/com/eidolon/sc/mobile/UserUnderstanding.java`
- `src/com/eidolon/sc/mobile/WebActivity.java`
- `src/com/eidolon/sc/mobile/WritingEngine.java`
- `src/com/eidolon/sc/mobile/WritingStore.java`
- `assets/update-notices.json`
- `assets/ui/app.js`
- `assets/ui/dialogue-text.js`
- `assets/ui/life.js`
- `assets/ui/style.css`
- `tests/continuity-ui.cjs`
- `tests/paragraph-ui.cjs`
- `tests/test_calls.py`
- `tests/test_continuity_delivery.py`
- `tests/test_event_context.py`
- `tests/test_letters.py`
- `tests/test_life_store.py`
- `tests/test_media.py`
- `tests/test_reach_plans.py`
- `tests/test_shared_store.py`
- `tests/test_social_preparation.py`
- `tests/test_writing_admission.py`
- `tests/com/eidolon/sc/mobile/ContinuityTests.java`
- `tests/com/eidolon/sc/mobile/DialogueParagraphTests.java`
- `tests/com/eidolon/sc/mobile/DialogueParityTests.java`
- `tests/com/eidolon/sc/mobile/FeatureTests.java`

另更新build.gradle/test.ps1的1.28.5路径、应用公告、README及同版功能/发布/验证/交接。私有签名入口只改本机构建版本路径，不进入公开发布树。

边界：结构候选和字面证据校验不等于完整自然语言事实审查；模型仍可能漏标、表达生硬或不符合角色，需要真实服务单独验收。无可靠结构时保守不写新候选；有界容量达到上限时不扩展数据库或无限创建。没有证据说明所有历史截图都出于同一根因；未采集私人原始返回或用户手机数据。
