# 云端 TTS 第一批：0.1.0.dev2

2026-11-05。这是默认关闭的模拟开发版，未部署到用户服务器，未接入 Android。后端通过 HTTPX 的进程内 MockTransport 读取固定静音，完全没有真实 Fish 传输、密钥加载或公网合成能力。

## 对计划的实际推进

| 步骤 | 本批完成 | 仍待完成 |
|---|---|---|
| 1 出口预检 | 无密钥 DNS/TCP/TLS 脚本；显式 HTTP CONNECT 模式；模拟错误和证书检查 | 在实际服务器运行，确认可用出口；不据本机测试宣称杭州服务器可达 |
| 2 请求协议 | 设备认证、UUID、持久登记、去重、状态、取消、完成摘要、schema2与备份兼容 | 真实付费前的状态细分、恢复旧备份后的请求代际隔离及预算 |
| 3 模拟中转 | HTTPX 异步假上游、回压、总并发1、16MiB、30秒总截止、断线清理和脱敏错误 | 真实网络上游适配的本地假 HTTP 服务/代理验收、真实配置加载 |
| 4–7 手机与上线 | 未开始 | 原播放器适配、私网/HTTPS、授权真实验证、原签名 APK 与发布 |

不向当前 dev1 服务执行升级、重启、私网或代理配置。TTS 传输与完整角色云端托管分开，`domainConnected` 仍为 false。

## 本机验证

在新建的独立虚拟环境安装本开发版及依赖。不要复用服务器的 dev1 虚拟环境或数据。以下命令在本源码根目录运行，数据目录必须新建且专供测试，服务使用回环端口8788：

```powershell
$env:EIDOLON_CLOUD_DATA=Join-Path $PWD ('.local/mock-' + [Guid]::NewGuid().ToString('N'))
$env:EIDOLON_CLOUD_PORT='8788'
$env:EIDOLON_CLOUD_TTS_MOCK='1'
$env:PYTHONPATH=Join-Path $PWD 'src'
# 登记结果留在本机；不要发到聊天/公开日志。
$mockDevice = python -m eidolon_cloud device-add mock-phone | ConvertFrom-Json
$env:EIDOLON_CLOUD_DEVICE_TOKEN=$mockDevice.token
# 在另一个具有同样 DATA/PORT/TTS_MOCK 配置的终端运行服务：
# python -m eidolon_cloud serve
python tools/tts_smoke.py --port 8788
```

Smoke 只允许127.0.0.1，禁用环境代理和重定向；先确认服务明确声明 mock，再发送一条人工输入，校验固定静音及完成摘要。输出不含令牌/正文；失败不会自动重发。服务Ctrl+C正常退出，测试数据保留；关闭功能设置 `EIDOLON_CLOUD_TTS_MOCK=0`，不删除请求历史。

出口脚本只依赖 Python 标准库，可单独放在服务器运行：

```bash
python3 tools/tts_probe.py
# 仅已有、无认证的 HTTP CONNECT 出口适用；不安装或配置任何代理：
python3 tools/tts_probe.py --proxy http://127.0.0.1:7890
```

目标固定 `api.fish.audio:443`，不发送 Fish HTTP 请求、文本或密钥。输出 DNS、TCP/CONNECT、证书验证阶段；退出0表示 TLS 成功，1表示失败。只影响本次探测，不读取环境代理、不改变系统网络。TCP/隧道/TLS使用8秒套接字超时，DNS仍受系统解析器控制，并非严格8秒总截止。拒绝携带凭据、路径、查询或 SOCKS 的代理地址。不要把速云订阅或私密节点放入命令。TLS通过只证明该出口握手可达，不能证明合成、余额、音色权限或实际音频通过。

## 已锁定的模拟接口

除健康检查外均使用原设备 Bearer 认证。健康检查不发起合成。`GET /api/tts/capabilities` 返回 `enabled`、`mode:off|mock`、`paidRequestsSupported:false`。

`POST /api/tts/stream` 只接受 JSON，禁止查询参数、重复字段、未知字段；请求最多65536字节。字段：

| 字段 | 限制 |
|---|---|
| requestId | 小写标准格式 UUID，客户端在一次操作开始前生成并保存 |
| text | 非空字符串，最多5000字符；本版不真的朗读 |
| voice |32位十六进制；沿用原客户端格式 |
| model | s2.1-pro-free、s2.1-pro、drama-3、s2-pro、s1 |
| pronunciation | 可选，沿用 `[{"items":[{"key":"…","value":"…","case_sensitive":true}]}]`；最多256组且合计256条，key/value各1–200字符 |

成功响应 `audio/pcm`，`X-Eidolon-TTS-Mode:mock`、`X-Eidolon-Sample-Rate:44100`，PCM signed16 little-endian、mono、44100Hz。固定17648字节静音，约0.2秒，与输入内容和声音无关。没有音频落盘缓存。每设备至多保留1000条模拟登记，达到上限拒绝；本版没有自动清理或付费预算功能。

`GET /api/tts/requests/{requestId}` 返回 requestId/state/bytes/sha256/mock/sampleRate/channels/sampleBits，仅允许原设备读取。`POST .../{requestId}/cancel` 可重复，保留已完成或其他终态，不假装撤销已完成工作。撤销设备之后不能再使用原令牌，活动流在后续块检查与完成前也验证设备。

状态流：`reserved → sending → completed`；取消为 canceled；超时、上游错误、中途断线、进程重启残留均为 unknown。`failed_before_send` 为未来真实适配预留，本版不产生该状态。相同ID同输入返回409 exists，不重新生成；不同输入409 conflict。unknown/canceled也不自动重做，需要用户明确新操作、新ID，未来付费版必须说明可能已有费用。

| 故障 | 行为 |
|---|---|
| 默认关闭/认证失败/格式超限/占用中 | 503/401/400或413/429，未读取假上游 |
| 首块前空响应、错误状态/类型、读取异常 | 受控502 JSON；不回显提供方正文 |
| 首块之后错误、超限或奇数总长度 | 终止流，登记unknown；不在PCM尾部拼JSON |
| 手机断开或主动取消 | 关闭上游并清理；先完成状态写入再释放并发名额 |
| 服务重启 | reserved/sending改unknown，绝不自动重发 |

只有整体音频字节数为偶数才有完整采样；单个分块可以为奇数。本版最多一条活动流，没有等待队列；HTTPX客户端关闭重定向、环境代理和自动重发，使用进程内假传输而非任何外部地址。整体30秒截止涵盖首块及后续读取；套接字细分超时与代理配置须在真实网络适配时另行验证。

## 完成和数据格式

HTTP流正常结束还不足以声明完整音频。未来手机必须再读同ID状态，确认 `completed`、bytes和SHA256与本机音频一致，才调用原PcmSpool.complete。查询失败可以重新查询，不得重新发起TTS。服务端 completed 只代表源流完整结束及登记，不代表手机收齐或用户听见。实际播放完成、关系/记忆提交、CallEpoch/turnId由原手机逻辑决定。

云端默认仍schema1；显式开启mock时，在事务中增加 `cloud_tts_requests` 并变更为schema2，不改原4张表内容。表只保存设备、请求ID、输入SHA256、时间、状态与完整音频校验，不保存正文、语音或密钥。输入摘要不是加密正文或匿名化承诺，数据库仍应私密保存。

dev2关闭mock后也可读取schema2的原框架数据，但不会删除新表。**dev1程序不能读取schema2**，不要覆盖生产数据；需要回退时使用升级前原快照。运维脚本2026.11.05.2的备份/校验函数支持schema1/2；其安装与服务身份校验仍固定dev1，不能把它当作dev2部署器。在线备份/复制在人工schema2库通过，未在真实服务器恢复。恢复历史快照可能丢失较新的请求登记，真实付费适配前必须加入恢复代际/拒绝旧请求机制，本版不承诺恢复旧备份后计费恰好一次。

## 本批验证记录

- Windows/Python3.12.14：完整89项测试通过、0跳过，其中新增37项TTS/出口脚本测试；原Java规则桥接仍执行。Ruff、格式与Python编译检查通过。
- dev2 wheel构建、安装到新目录后，运行实际回环HTTP服务：smoke通过，17648字节静音及持久摘要一致。另用真实本机TCP连接接收首块后断开，确认上游关闭、状态unknown、并发名额释放。仅本机套接字，不是手机/Ubuntu验证。
- schema2在线备份、摘要校验与复制保留请求登记通过；systemd操作仍沿用模拟检查。本批未升级服务器、改Android、产出APK、运行实际出口探测或调用付费服务。
- 旧Android历史摘要不是本批保护基线；Android现有1.28.1/code85与手机数据库v18未改。旧ZIP、旧APK和已验证的dev1服务器程序保留。

主要文件：`src/eidolon_cloud/tts.py`、`app.py`、`assembly.py`、`config.py`、`store.py`、`__init__.py`；`tools/tts_probe.py`、`tts_smoke.py`、`package_source.py`；`tests/test_tts.py`、`test_tts_probe.py`；`deploy/eidolon-cloud-setup.py`；版本/环境示例与本说明。

## 后续顺序

1. 运行出口脚本，依据实际结果确定服务器直连或受控出口；不要求用户提供速云订阅。
2. 做真实 HTTP 传输的本地假服务与代理测试，再增加服务器私密凭据/音色允许列表、预算、恢复保护和已知旧unit迁移。真实服务默认仍关闭。
3. 增量改 FishClient 传输边界、VoicePlayer 完成校验、VoiceCache命名空间和CallService配置准入；保留原播放及业务效果提交规则。
4. 私网模拟音频真机通过后，再请求一条明确短文本的真实合成验证授权。

依据：[Fish TTS](https://docs.fish.audio/api-reference/endpoint/openapi-v1/text-to-speech)、[HTTPX异步流](https://www.python-httpx.org/async/)、[HTTPX测试传输](https://www.python-httpx.org/advanced/transports/)。
