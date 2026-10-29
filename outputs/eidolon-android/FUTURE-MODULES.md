# 表情包与实时语音的后续设计

本轮只规划这两项，不启用新模型调用或麦克风权限。

## 表情包

现有messages为role/body文本，gift角色是结构化礼物事件，不能直接将任意路径作为图片消息。建议下一版新增message_type与payload字段，旧消息迁移为text，gift保持兼容；新增sticker_assets(id,character,mime,sha256,width,height,private_path,source,license)。资源在应用私有目录保存，WebView通过固定同源资源路由读取，不接受模型返回的文件路径、HTML或外部URL。

模块：StickerCatalog资源校验/角色范围；StickerImporter通过系统选择器导入并限制尺寸、类型、总量；MessagePayload校验已有资源ID；聊天时间线分别渲染text/sticker/gift；备份先制作资源清单和哈希，再加密打包。删除资源需检查历史引用，保留占位提示，不破坏聊天。模型只能从当前可用ID白名单中选择，可将可选ID附加到既有回复结构，不新增独立选图调用。

先做用户手动发送、时间线、备份和缺资源占位，再做可关闭的AI选择。测试畸形图片、跨角色ID、路径穿越、解压限额、恢复缺图以及旧版本备份；未完成这些前不开放图片消息入口。

## 实时电话

APK实际已有单条Fish TTS、音色设置、缓存、AudioTrack流式播放、暂停续播和本地语音管理；当前没有麦克风输入、ASR、VAD、双向实时通话或打断。Hibi/原网页后端提供的能力不代表APK已具备。

建议模块：

1. CallSession：IDLE → CONNECTING → LISTENING → THINKING → SPEAKING → ENDING/ERROR，独立于普通聊天的待回复批次。通话结束才提交完整转录，失败不能伪造成功轮次或时长。
2. MicrophoneInput：用户点击开始后申请RECORD_AUDIO；AudioRecord录音及本地VAD。真实输入设备、蓝牙切换和回声消除需要真机测量。
3. SpeechRecognition：可插拔系统/自配云ASR；先探测设备离线识别能力，不假定每台手机有离线中文/日文模型。云识别需独立端点、鉴权、音频上传说明与费用上限。
4. StreamingConversation：复用ModelClient聊天接口，新增有句子边界的增量输出、轮次ID与取消令牌，避免把未完成句子存成正式聊天。
5. SpeechQueue：复用FishClient、已配置角色音色、语音缓存；按句合成并限制待播队列。打断时同时取消ASR上一段、模型、TTS和播放队列，使用轮次ID丢弃晚返回数据。
6. CallAudio：音频焦点、MODE_IN_COMMUNICATION、耳机/扬声器、音量、静音、电话中断；需要独立音频会话，不能直接将普通TTS播放等同通话。
7. CallBudget/Latency：本地统计ASR时长、模型token（服务有usage才记录，未知显示未知）、TTS字符/时长及首音延迟；用户设置每次时长和预算，达到上限停止。不得编造金额。

先做应用前台、用户按住说话的半双工原型，使用模拟ASR/模型/TTS验收取消和状态机；再由用户明确允许后进行少量真实服务验证。最后考虑全双工、后台麦克风前台服务及环境音混音。Android后台麦克风权限、前台服务启动限制、厂商冻结和服务商流式协议均为依赖，不能靠隐藏WebView解决。
