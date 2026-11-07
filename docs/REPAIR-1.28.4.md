# 1.28.4 原因与修改清单

基线为1.28.3/code87。主工程src/res/assets与上次交付清单逐项一致，远端main仍为e5e8b7819e2cabd8d06aea2c574a7b427c69fb0f，无待合并后续提交；在原目录增量修改，publication仅发布暂存。

确认的代码缺口：LifeStore.projectEvent丢失start/end/zone，模型生活投影不完整；RecentTimeFacts原指令对普通日常过于保守；普通聊天和主动搭话没有共同的选题/事件状态协议；TimeConsistency原校验只覆盖少数固定句；默认字号原生与前端18/13，部分兼容正文落入旧CSS回退；台本资料缺口没有关联同一活动的持久概要。截图未作为生成/实际送达时间或单一根因的证明。

核心改动：EventContext从真实可见用户消息生成有界候选，已有判断确认事实/主体/时间/纠正/状态；程序换算日期并投影交谈状态，稳定事件ID含来源编号及时间。ReplyBasis选择本轮任务、话题和材料来源，附在最终system内容中，保留既有消息序列；不会向API根对象塞不支持字段。LifeData/Store复用已有文档和事务，保持模拟完成与对话推定分离；提交依据检查待回复状态/版本及源消息，导入重映射源ID，来源缺失不推定。TemporalData/Runtime带上一段安排和规范化计划版本。ReachExecution/ConversationEngine同源生成并在ChatStore实际投放事务中复核；取消、迟到、重复和未知付费结果沿用原机制，不自动付费重生成。

TimeConsistency增加受时态/主体/引用限定的自述分类与结构标注，按当时日程/覆盖/有据事件校验，不采用全局禁词或禁止晚安。模型缺标注时仅有限语言兼容，不能保证穷尽所有同义句。BilingualAlignment检查可选逐段引用覆盖和显式HH:mm冲突；不做长度阈值，不声称完全验证跨语言语义。ChatEnvelope仍是统一JSON字段协议。

ScheduleGeneration/Schema在原日程请求可选准备虚拟原创topic；ReplyBasis严格区分导入参考、用户正文、虚拟原创和资料缺失，投放后保存授权topic_update。WritingEngine/NotebookEngine复用同一事件视图，过去日记按记录日期截止，不用未来记录填补旧日经历。来信不在生成未投放时执行聊天个人效果。

UI原生16/13默认、WebView textZoom100、CSS一致继承和段间距调整；已存偏好保留；用户提及记录在生活界面只读，不能通过虚拟目标控制误结算。

以下为实际变化文件（含测试类路径更新；私有签名脚本不发布）：

- `src/com/eidolon/sc/mobile/BackupValidator.java`
- `src/com/eidolon/sc/mobile/BilingualAlignment.java`
- `src/com/eidolon/sc/mobile/ChatEnvelope.java`
- `src/com/eidolon/sc/mobile/ChatStore.java`
- `src/com/eidolon/sc/mobile/ConversationEngine.java`
- `src/com/eidolon/sc/mobile/EventContext.java`
- `src/com/eidolon/sc/mobile/LifeData.java`
- `src/com/eidolon/sc/mobile/LifeStore.java`
- `src/com/eidolon/sc/mobile/NotebookEngine.java`
- `src/com/eidolon/sc/mobile/ReachExecution.java`
- `src/com/eidolon/sc/mobile/RecentTimeFacts.java`
- `src/com/eidolon/sc/mobile/ReplyBasis.java`
- `src/com/eidolon/sc/mobile/ScheduleData.java`
- `src/com/eidolon/sc/mobile/ScheduleGeneration.java`
- `src/com/eidolon/sc/mobile/TemporalData.java`
- `src/com/eidolon/sc/mobile/TemporalRuntime.java`
- `src/com/eidolon/sc/mobile/TimeConsistency.java`
- `src/com/eidolon/sc/mobile/UiSnapshots.java`
- `src/com/eidolon/sc/mobile/WebActivity.java`
- `src/com/eidolon/sc/mobile/WritingEngine.java`
- `assets/update-notices.json`
- `assets/ui/app.js`
- `assets/ui/life.js`
- `assets/ui/style.css`
- `tests/life-context-ui.cjs`
- `tests/test_calls.py`
- `tests/test_event_context.py`
- `tests/test_life_store.py`
- `tests/test_media.py`
- `tests/test_reach_plans.py`
- `tests/test_reach_wake.py`
- `tests/test_shared_store.py`
- `tests/test_social_admission.py`
- `tests/test_social_preparation.py`
- `tests/com/eidolon/sc/mobile/ContinuationTests.java`
- `tests/com/eidolon/sc/mobile/ConversationBasisTests.java`

另更新build.gradle、test.ps1、发布公告、README及本轮发布/功能/验证/交接文档。本机tools/build-workmanager.ps1仅版本路径改动，签名配置不进入GitHub。

未确认：截图中具体模型原始返回、设备显示设置、实际生成/计划/实际送达时刻及角色设置时区均未从私人设备采集。没有证明原截图每次都由同一问题造成。真实服务是否遵守结构、角色口吻是否自然、其他同义自述是否遗漏仍需实测；原后台整夜失效诊断没有因本轮桌面测试而解决。
