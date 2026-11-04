# 1.28 修改与功能入口

主工程原位置增量修改，基线1.27/code83/main e9b97d22a8bdb239632a8f20e63d696e07294d79；本地交付哈希相符，未克隆覆盖。1.28/code84，DB/备份18保持。

设置→模型与接口→语音通话设置：识别提供商、发言结束方式、自动下一轮、主动来电和通话记录。外呼本地回铃后连接，聆听/等待继续/确认中如实展示；说完了可立即结束。异常收尾保留最终文字供显式确认。诊断在通话记录中展开。

核心文件：DialogueText与浏览器dialogue-text.js负责相同语言解码；ChatEnvelope和ConversationEngine/ReachExecution统一协议；ReplyText/BilingualReply/FishClient/CallAudioText消费一致结果。ScheduleData.sleepInterval共用日期规则，LifeData只修正派生夜间事件；TemporalData/Runtime和TimeConsistency负责有来源的状态及保守生成/投放检查。CallTurnEnd负责有界整轮判定，CallDialWait保存单次等待；BailianAsrProtocol/CallBailianAsr分段与握手，CallHttpAsr整轮一次上传；CallService继续现有身份/音频/通知/播放提交。AsrDiagnostic只允许元数据字段。

不修改数据库表或备份版本；CallStore允许新增通话状态并保留最多8轮诊断。旧通话恢复为结束，不恢复定时器或付费任务。私有本机构建签名脚本不公开，公开构建入口使用可移植配置。

完整变更文件（交付时以公开提交为准）：

- `RELEASE-1.28.md`
- `REPAIR-1.28.md`
- `assets/ui/app.js`
- `assets/ui/call-screen.css`
- `assets/ui/call-screen.js`
- `assets/ui/calls.js`
- `assets/ui/chat-time.js`
- `assets/ui/dialogue-text.js`
- `assets/ui/index.html`
- `assets/update-notices.json`
- `build.gradle`
- `src/com/eidolon/sc/mobile/AsrDiagnostic.java`
- `src/com/eidolon/sc/mobile/BailianAsrProtocol.java`
- `src/com/eidolon/sc/mobile/BilingualReply.java`
- `src/com/eidolon/sc/mobile/CalendarEvents.java`
- `src/com/eidolon/sc/mobile/CallAsrSettings.java`
- `src/com/eidolon/sc/mobile/CallAudioText.java`
- `src/com/eidolon/sc/mobile/CallBailianAsr.java`
- `src/com/eidolon/sc/mobile/CallDialWait.java`
- `src/com/eidolon/sc/mobile/CallHttpAsr.java`
- `src/com/eidolon/sc/mobile/CallLimits.java`
- `src/com/eidolon/sc/mobile/CallService.java`
- `src/com/eidolon/sc/mobile/CallStore.java`
- `src/com/eidolon/sc/mobile/CallTurnEnd.java`
- `src/com/eidolon/sc/mobile/ChatEnvelope.java`
- `src/com/eidolon/sc/mobile/ChatStore.java`
- `src/com/eidolon/sc/mobile/ConversationEngine.java`
- `src/com/eidolon/sc/mobile/DialogueText.java`
- `src/com/eidolon/sc/mobile/FishClient.java`
- `src/com/eidolon/sc/mobile/LifeData.java`
- `src/com/eidolon/sc/mobile/LifeStore.java`
- `src/com/eidolon/sc/mobile/ProducerProfile.java`
- `src/com/eidolon/sc/mobile/ReachExecution.java`
- `src/com/eidolon/sc/mobile/ReplyText.java`
- `src/com/eidolon/sc/mobile/ScheduleData.java`
- `src/com/eidolon/sc/mobile/TemporalData.java`
- `src/com/eidolon/sc/mobile/TemporalRuntime.java`
- `src/com/eidolon/sc/mobile/TimeConsistency.java`
- `test.ps1`
- `tests/com/eidolon/sc/mobile/BailianTests.java`
- `tests/com/eidolon/sc/mobile/ContinuationTests.java`
- `tests/com/eidolon/sc/mobile/DialogueParityTests.java`
- `tests/continuation-ui.cjs`
- `tests/dialogue-parity.cjs`
- `tests/test_shared_store.py`
- `tools/build-workmanager.ps1`
