# 1.28.6 修改与原因

## 基线与确认的问题

实际1.28.5/code89，主源码与上版交付哈希一致；远端main为31f71242bfbcbead18b83fc822c9cf8349e1dd0e，无后续。未重克隆或覆盖工程。

旧ReachPlans.ensure主要按接触/关系/日程/时段排队；ReachExecution到期领取后直接请求一条简短消息。ReplyBasis在无事件时还可回退到日程分享，缺少生成前无合适内容则等待的判断。文本近似去重在生成后，不能省下该次请求。

旧自动来信从LetterPlanData独立的日期/场合/普通抽样进入LettersEngine→WritingEngine→LetterPlanStore.ready→deliver。已有连续性版本保护，但缺少跨入口同一事项的领取/交流收据和最新用户消息失效保护。日记为内部写作，本版不把它当向用户表达。

普通聊天通过ConversationEngine→ReplyBasis/ContinuityContext→ModelClient→原PendingReply事务。已有目标和条件理解，但一般具体问题的结构化资料/缺口选择偏向台本；原话存在不等于明确选择了问题所需资料。

这些是代码可确认的机制缺口，不证明所有真实模型的急切、绕开问题或模板感只有同一原因。

## 实际改动

ExchangeIntent在现有character_life JSON中管理有界事项，单次领取后允许say/wait/drop，无内容不请求，无变化不重试。ExchangeStore把准备、跨入口并发领取、投放复核和实际收据接入原SQLite事务。用户接起话题记为合并，和已经告诉用户分开；来源、版本、消息或时间变化就停止旧候选。

ReachPlans/ReachExecution在排队和请求前先选择有依据的新内容；保留原token/revision、预算与时段约束。普通聊天提交可吸收相关事项，去重不禁止用户追问。WritingEngine/LettersEngine/LetterPlanStore/WritingStore共享依据，信件入箱与收据同事务，准备和失败不算送达。主动来电邀请仅记邀请，仍须完整播放才提交通话内容。

InformationTurn/ReplyBasis把一般问题的实际资料、条件、来源和缺口放入最终请求；允许感受和自然结束。ContinuityContext补充有限的实际交流/待表达状态。LifeStore原提交写收据；EventContext保留目标关联和已有提及，避免目标与阶段各排一遍。

备份导入写入原消息映射的来源边界，旧问候不因消息ID变化成为新事项；时间回退拒绝把未来生成内容当作当前状态。

设置与机会页面明确数量是上限、窗口仅评估，不制造必须发言的排班。没有新模型/工具执行服务，没有新增关系、记忆、目标或奖励结算。历史聊天原文不批量改写。

## 修改文件

- `src/com/eidolon/sc/mobile/CallIncoming.java`
- `src/com/eidolon/sc/mobile/ChatStore.java`
- `src/com/eidolon/sc/mobile/ContinuityContext.java`
- `src/com/eidolon/sc/mobile/ContinuityStore.java`
- `src/com/eidolon/sc/mobile/EventContext.java`
- `src/com/eidolon/sc/mobile/ExchangeIntent.java`
- `src/com/eidolon/sc/mobile/ExchangeStore.java`
- `src/com/eidolon/sc/mobile/InformationTurn.java`
- `src/com/eidolon/sc/mobile/LetterPlanStore.java`
- `src/com/eidolon/sc/mobile/LettersEngine.java`
- `src/com/eidolon/sc/mobile/LifeData.java`
- `src/com/eidolon/sc/mobile/LifeStore.java`
- `src/com/eidolon/sc/mobile/ReachExecution.java`
- `src/com/eidolon/sc/mobile/ReachPlans.java`
- `src/com/eidolon/sc/mobile/ReplyBasis.java`
- `src/com/eidolon/sc/mobile/WritingEngine.java`
- `src/com/eidolon/sc/mobile/WritingStore.java`
- `assets/update-notices.json`
- `assets/ui/app.js`
- `assets/ui/inbox.js`
- `tests/inbox-ui.cjs`
- `tests/plans-ui.cjs`
- `tests/reach-settings-ui.cjs`
- `tests/test_continuity_delivery.py`
- `tests/test_event_context.py`
- `tests/test_exchange_delivery.py`
- `tests/test_letters.py`
- `tests/test_shared_store.py`
- `tests/test_social_preparation.py`
- `tests/test_writing_admission.py`
- `tests/com/eidolon/sc/mobile/ExchangeTests.java`
- `tools/build-workmanager.ps1`
- `build.gradle`
- `test.ps1`

原签名脚本仅本机递增输出版本；公开副本继续使用可移植无私有签名脚本。源码主目录不变，publication只暂存。
