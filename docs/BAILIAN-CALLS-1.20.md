# 1.20 电话接入阿里云百炼

## 审查及增量方案

基线1.19/code74，当前1.20/code75，SQLite与加密备份保持v16。没有重新克隆或覆盖工程。旧通话的系统识别、HTTPS转录、聊天/记忆/时间上下文、Fish合成、播放确认提交及主动来电逻辑保留。

核对上游源码而非只看README：[bailian.py](https://github.com/Sallyn0225/eidolon-sc/blob/main/backend/src/eidolon/bailian.py) 的REGION_URLS（61行）、PARAMETERS（75行）、_run_task及_receive_messages，及[config.py:198](https://github.com/Sallyn0225/eidolon-sc/blob/main/backend/src/eidolon/config.py#L198)的默认模型。上游通过DashScope WebSocket发送run-task，等待task-started后上传16kHz/16bit单声道PCM，result-generated内sentence提供临时文字和sentence_end句尾；heartbeat不作为用户发言。Android直接使用同一协议，不引入Pipecat或电脑后端。

## 新增/修改文件

- BailianAsrProtocol.java：固定地域端点、任务ID、run/finish消息、临时/最终文字解析、忽略心跳/标点/过期任务、去重终态及安全错误码。
- CallBailianAsr.java：OkHttp WebSocket与AudioRecord适配器；可注入音频源、传输层与单调时钟。任务启动确认后采音，约100ms声音包实时上传，服务句尾决定一轮结束；最多采集30秒，12秒无声结束，连接/启动/最终结果等待有界。每轮结束或取消释放录音与连接。上传队列超过64KB停止，避免弱网无限积压。不自动重试或跨地域探测。
- CallAsrSettings.java：系统/百炼/HTTPS选择、旧配置沿用、拨打前校验；百炼密钥单独使用Vault的callBailianKey，不复用聊天、Fish或HTTPS密钥。固定可信端点，禁止把百炼密钥发到自定义地址。HTTPS更换地址须重新提供对应密钥。
- CallService.java：接入百炼适配器，临时文字只用于字幕，最终文字才调用原generate；所有回调继续检查callId/turn版本，挂断/静音/打断和设备变化取消连接。识别时间通过现有CallClock回推发言日期，后续角色上下文仍由原系统生成。
- WebActivity.java与assets/ui/calls.js：原生设置桥接及提供商/地域/模型/独立密钥表单；保留静音、扬声器、打断、挂断和来电设置。
- build.gradle：OkHttp4.12.0标准WebSocket实现，版本升级；更新公告、构建/测试版本路径和专项测试同步。

## 使用与数据流

角色聊天页 → 电话 → 识别服务与主动来电设置 → 阿里云百炼（流式识别）。

1. 选择密钥所属地域：北京或新加坡。国内密钥通常选择北京；不同地域不能互换。
2. 填写百炼实时语音识别API Key，并开通所填模型。默认沿用当前上游qwen-audio-3.1-asr-flash-streaming；可以填账号已开通且支持DashScope audio/asr/recognition协议的模型。普通聊天模型、OpenAI转录端点及其他Realtime协议不可直接替代。
3. 原聊天模型、角色Fish音色/密钥保持原设置。保存后拨打，授予麦克风及通知权限。

声音直接经WSS发送至所选地域百炼；识别文字与角色上下文发送到已有模型配置；角色台词发送到Fish合成。原始输入声音不写入文件，识别文字仍保存在本机聊天。识别文本不自动成为长期稳定用户事实/约定；只有角色回答完整播放后提交原有聊天效果。

百炼默认端点分别为wss://dashscope.aliyuncs.com/api-ws/v1/inference与wss://dashscope-intl.aliyuncs.com/api-ws/v1/inference。密钥不在公开日志、文档或备份中。API认证/模型拒绝仅显示安全状态码，不回显服务原始错误消息或密钥。通话识别时持续上传，静音检测前的声音也可能参与计费；费用以百炼实际规则为准，不承诺无讲话零费用。

## 验证与限制

新增BailianTests执行生产协议和采音/发送循环，音频为合成PCM，socket与时钟为可注入假实现，覆盖：任务身份/旧结果、临时字幕不提交、心跳/空句忽略、重复最终结果、正常关闭、任务启动超时、401不采音不切换地域、模型失败安全提示、采音取消、排队取消不联网、64KB上传积压、断网无自动重试、12秒静音、30秒采集上限及finish结果、缺少最终结果不提交、资源释放。calls-ui.cjs检查百炼/地域/模型/密钥表单的实际页面及原电话操作。既有回归继续运行。

验证必须分别记录：离线假接口验证通过不代表Android硬件或百炼真实服务通过。未调用付费服务，真实服务兼容性、Android麦克风/耳机/蓝牙/后台、实际延迟与金额仍未测量。界面沿用现有真实设备计时，音量阈值估计停讲时刻可能受噪声影响；没有首段LLM直接合成或自然语音打断，整通电话仍自动轮流说话。用户可手动打断。

尚未加入上游热词权重表与多句后续发言合并。第一版以首个有效sentence_end作为本轮结束；说话中长停顿可能被识别成两轮，需要继续接话或手动打断。没有服务失败后静默降级或未知付费请求自动重试。
