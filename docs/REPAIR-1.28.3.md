# 1.28.3 修改与原因

## 核对结果

起点为1.28.2/code86，远端main为9fed6242a439a301bc1efaf66cb89e9f25f168e0。主源码src/res/assets与既有交付哈希一致；修改前完整快照已保存在本地。主源码没有重新克隆或覆盖；publication仅为发布暂存。云端未修改。

## 已确认的代码问题与修复

1. DialogueText及浏览器解析此前会因为片段含假名而将中文改判为日文；BilingualReply又拒绝任何含假名的补译。中文姓名、术语或引用因此可能被隐藏。现在尊重字段语言，仅按受限、可解释的语言证据恢复已知污染，不按假名一刀切。代码/引用保留，未知HTML作为文本呈现。ReplyText取消重复规范化；ChatEnvelope清除重复协议说明，保持JSON ja/zh纯文本协议。
2. 保存链仍走MediaReply/ChatEnvelope、原PendingReply和事务；历史读取在MessagePresentation附加原生解析投影，不更新旧行。前端按语言分组后呈现投影，避免姓名和引号被拆成独立段。朗读直接从同一解析器取确认的完整日文，不读取隐藏后的DOM，不混入中文、内部标记或代码。缺失/不确定轨道明确反馈，不自动补译或生成。中文模式沿用其中文语音含义；VoicePlayer分离显示/控制身份和语音派生文本，保持暂停/继续与缓存管理。
3. 键盘事件此前只改变导航可见性，缺少尺寸变化后的滚动恢复。现在使用原生WebView实际布局通知和ResizeObserver，复用原insets，不再次扣减IME高度。记录底部意图或首条可见消息偏移，输入区/剧情参考/图片/面板或旋转改变尺寸后恢复；连续布局动画不把中间位置当成用户改了阅读意图。输入框不重建。用户消息可作阅读锚点，原未读确认标记不变。
4. 表情原有点击只发送或预览，聊天没有释义入口。新增MediaActions→MediaStore稳定ID与asset一致性只读查询，MediaData只投影现有字段；不调用视觉/模型，不更新使用次数、偏好或权限。聊天面板/气泡提供长按和等效按钮，移动、滚动、取消及多点触控取消识别。抑制成功长按后的click持续至消费/下一次明确按下，不因长时间按住失效。删除资源明确降级。
5. 历史原有时间表统一换算成当前角色时区，近期时间事实只覆盖用户消息；摘要仅保留首末来源。现在MessageTime保留每条原始时区、生成/投放、来源和间隔；当前双方时钟使用同一Clock快照及各自配置。模型真实HTTP体包含与实际入选消息一一对应的参考表。RecentTimeFacts按来源消息时区处理用户相对日期；NotebookEngine和MemoryNotebook保留及携带摘要来源时间序列。整轮裁剪计算元数据成本，并说明丢弃的早期范围。正文中的伪时间标签不进入可信发送时间表。

这些是源码与离线链路中确认的问题；未读取本轮私人截图或手机原始返回，不能断言每条截图都只由其中某一项导致。旧角色的历史本地时区未记录时，显示时区不冒充角色所在地，跨时区相对日期保持不确定；用户消息跨日差按来源时区计算。模型实际是否准确理解所有指代，仍取决于真实服务表现，未进行付费验证。

## 具体文件

- `assets/ui/app.js`
- `assets/ui/chat-time.js`
- `assets/ui/chat-updates.js`
- `assets/ui/dialogue-text.js`
- `assets/ui/index.html`
- `assets/ui/media-picker.js`
- `assets/ui/media.css`
- `assets/ui/media.js`
- `assets/ui/sticker-meaning.js`
- `assets/ui/style.css`
- `assets/ui/view-position.js`
- `src/com/eidolon/sc/mobile/BilingualReply.java`
- `src/com/eidolon/sc/mobile/ChatEnvelope.java`
- `src/com/eidolon/sc/mobile/ConversationEngine.java`
- `src/com/eidolon/sc/mobile/DialogueText.java`
- `src/com/eidolon/sc/mobile/MainActivity.java`
- `src/com/eidolon/sc/mobile/MediaActions.java`
- `src/com/eidolon/sc/mobile/MediaData.java`
- `src/com/eidolon/sc/mobile/MediaStore.java`
- `src/com/eidolon/sc/mobile/MemoryNotebook.java`
- `src/com/eidolon/sc/mobile/MessagePresentation.java`
- `src/com/eidolon/sc/mobile/MessageTime.java`
- `src/com/eidolon/sc/mobile/ModelClient.java`
- `src/com/eidolon/sc/mobile/NotebookEngine.java`
- `src/com/eidolon/sc/mobile/ReachExecution.java`
- `src/com/eidolon/sc/mobile/RecentTimeFacts.java`
- `src/com/eidolon/sc/mobile/ReplyText.java`
- `src/com/eidolon/sc/mobile/VoicePlayer.java`
- `src/com/eidolon/sc/mobile/WebActivity.java`

另更新build.gradle、test.ps1、原生/假桥/解析一致性测试、应用内公告及同版发布/操作/验证/交接。私有构建签名包装保留在本机，公开版使用便携脚本。

## 保留边界

无数据库或备份迁移，不批量重写历史；原生成请求、revision/token、迟到回调隔离、关系/记忆/生活提交幂等及未知计费隔离保持。未完整播放的通话仍不能提交成已听见。没有自动重放付费请求。原签名与旧APK保留。整夜充电的后台失效原因仍未确认，不把标准未充电Doze当作该次已知原因。
