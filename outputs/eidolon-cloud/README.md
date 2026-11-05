# Eidolon 随行：自编云端框架

状态：`0.1.0.dev2`，新增默认关闭的云端 TTS 模拟协议。只生成固定静音，不调用 Fish；手机尚未接入。用户截图已确认服务器上的 **dev1** 由 systemd 运行、健康/认证与在线备份通过；本开发版尚未部署。

本批实现、运行方式和下一步见 [TTS-DEV2.md](docs/TTS-DEV2.md)。服务器出口探测脚本只做 DNS/TCP/TLS；真实服务器可达性仍待运行确认。

**框架组织参考上游，后端代码自行编写，现有业务规则保持原样。** 本目录不依赖或导入上游 `eidolon` 包，也没有把上游角色、日程、记忆、关系系统复制为第二套。

上游参考基线是 `Sallyn0225/eidolon-sc@596368d7f7253d58840eb4f0e6f02f57c4418552`。参考的是服务装配、生命周期、数据访问、任务运行和 API 适配的职责边界；上游默认日程与搭话策略不作为随行的新规则。

## 分层与现有模块的关系

| 本目录自编模块 | 职责 | 不承担的现有业务 |
| --- | --- | --- |
| `assembly.py` | 组装配置、数据库、接口与任务运行器，统一启动/退出 | 不重新初始化人物、关系或记忆 |
| `app.py` | 设备认证、基础消息补拉、保存确认、请求限制 | 不计算日程、不调用模型 |
| `data_access.py` | SQLite 串行事务写入与独立快照读取 | 不替代手机 ChatStore，不读取原 eidolon.db |
| `store.py` | 设备令牌摘要、诊断任务、测试收件箱和保存游标 | 不建立日程、关系、记忆或个人档案表 |
| `reach_outs.py` | 后台循环的启动、停止、任务执行入口 | 不决定睡眠/洗澡/忙碌、好感门槛或搭话间隔 |
| `domain.py` | 接现有业务核心的适配接口与版本字段 | 没接上时明确不可用，不用简化版算法顶替 |
| `runtime_lock.py` | 同一数据目录只允许一个任务运行进程 | 本地管理命令仍通过 SQLite 事务工作 |
| `tts.py` | 设备隔离的模拟 TTS 流、持久请求状态与取消 | 不调用真实 Fish、不声明用户已听见、不提交关系/记忆 |

日程与判断的当前来源保持为 Android 的 ReachConfig、ReachPolicy、ReachPlanRules、ReachPlans、ReachExecution、ScheduleData/Runtime、DailyScheduleClock、LifeData/Store、TemporalRuntime 等。现有配置字段、默认值与规则没有改写成另一份 Python 版本。

**规则不改，不等于永远只能在手机执行。** 后续先梳理、提取无 Android 依赖的共享 Java 规则，通过受控的本机业务适配器供后端调用；Android 的偏好读取、数据库访问和通知放在各自适配层。提取只改变调用/存储位置，不改变日程结果和判断内容，需要用现有测试和相同输入输出逐项验收。

已实现第一条共享调用通路：`shared_clock.py` 经本机 Java 适配器直接运行原始 `DailyScheduleClock.java`。构建脚本从现有 Android 源文件编译，不复制/改写该规则，同时运行原有 DailyScheduleClockTests。它覆盖每日 03:00 显示日切换、时区和夏令时边界，**不是完整的日程安排或聊天核心**。

完整业务适配器尚未接通。`UnboundDomain` 明确拒绝真实业务执行；不能宣称手机关闭后真实角色已能在云端自主聊天。也不能靠手机偶尔上传几个计划来冒充完整的离线常驻业务核心。

## 已实现的基础能力

- 独立 SQLite 基础设施库 `cloud-control.sqlite3`；不接管无关数据库，不改当前手机 SQLite/备份 v18。默认 schema1；显式启用模拟 TTS 才增量迁移为 schema2。
- 单个私有用户的设备登记、256 位随机令牌、摘要保存和撤销；管理操作只有本机 CLI。
- 明确标识的固定诊断任务：持久化、幂等入队、过期、取消、原子投放。
- 消息稳定 UUID、数据库消息流 UUID、分页游标、重复补拉和单设备保存确认。
- 保存确认不表示用户已读、已听见，不改关系、记忆、日程或桌面角标。
- 有界写入队列、SQLite 事务回滚、读写分离、单运行进程保护；已提交的写任务不因 HTTP 等待者断开而被误当成未执行。
- 默认诊断循环关闭；HTTP 服务只能监听回环地址，关闭访问日志，不信任外部转发头。

## 本机开发与验证

Python 3.11+。不需要 PostgreSQL、Redis、上游网页前端、Pipecat 或 WebRTC/coturn。

```bash
python -m venv .venv
# 激活此目录自己的虚拟环境后：
python -m pip install -e '.[test]'
python -m pytest -q
```

Windows 如默认 pytest 临时目录不可访问，可使用：

```powershell
./tools/test.ps1 -Python '你的 Python 可执行文件'
```

脚本为每次测试创建 `.local/` 下新的独立路径，不删除已有测试目录或用户数据。所有测试只使用人工数据，无模型或语音 API 请求。

核对受保护的现有源码：

```bash
python tools/verify_domain.py --android-source ../eidolon-android
```

`domain-baseline.json` 保留历史 Android 1.22/code77、v17 与 23 个文件的审查摘要。当前 Android 已为 1.28.1/code85、v18，后续 Android 发版使其中12个摘要不同；不能把旧摘要全部通过作为本批验收，也不能为消除差异重置基线。本批未改 Android 文件。

原始 Java 规则桥接验收（需要 JDK 17，构建目标为 Java 8）：

```powershell
./tools/build-shared-clock.ps1 -AndroidSource ../eidolon-android -Jdk $env:JAVA_HOME -Python '你的Python'
# 使用上一步实际输出的 JAR 路径：
$env:EIDOLON_TEST_RULE_JAR='实际的 shared-clock.jar 路径'
$env:EIDOLON_TEST_JAVA=Join-Path $env:JAVA_HOME 'bin/java.exe'
./tools/test.ps1 -Python '你的Python'
```

未设置这两个测试变量时，6 个真实 Java 适配检查会明确跳过，不能把普通 Python 测试通过写成共享规则验收通过。适配器只有本机进程调用，没有新增公网规则计算接口。

## 运行与诊断

环境变量由进程读取，不自动加载旁边的 `.env`、上游 `.env` 或既有密钥：

```bash
export EIDOLON_CLOUD_DATA=/var/lib/eidolon-cloud
export EIDOLON_CLOUD_DIAGNOSTICS=1

eidolon-cloud device-add my-phone
eidolon-cloud diagnostic-add connection-test '带时区的ISO时间'
eidolon-cloud serve
```

开发中未安装命令入口时可使用 `PYTHONPATH=src python -m eidolon_cloud ...`。登记设备输出只在本机终端显示一次令牌，不要粘贴到聊天、提交到 GitHub 或保存到公共日志。撤销用 `eidolon-cloud device-revoke DEVICE_ID`。

诊断任务仅允许过去五分钟至未来一天的时间，十五分钟后过期。重复入队使用同一任务标识和完全相同时间；改变参数会被拒绝。诊断循环不调用 DomainPort，也不会伪造角色消息。

HTTP API：

| 接口 | 用途 |
| --- | --- |
| `GET /api/health` | 连接和存储状态；`domainConnected:false` 明示业务未接通 |
| `GET /api/device` | 设备信息、消息流 UUID、已保存游标 |
| `GET /api/inbox?after=0&limit=50` | 稳定顺序补拉，limit 为 1–100 |
| `POST /api/inbox/ack` | `{"through":1,"streamId":"消息流UUID"}` |
| `GET /api/tts/capabilities` | 认证后查询 off/mock；永远 paidRequestsSupported=false |
| `POST /api/tts/stream` | 显式启用后返回固定测试 PCM；默认503 |
| `GET /api/tts/requests/{requestId}` | 同设备读取状态/字节数/摘要，不发起合成 |
| `POST /api/tts/requests/{requestId}/cancel` | 同设备幂等取消，不改已结束状态 |

除健康检查外要求 `Authorization: Bearer DEVICE_TOKEN`。没有 HTTP 注册、管理、创建任意任务、模型调用、配置覆盖或日程计算接口。

后续手机必须在一个本地持久事务里保存消息、消息流和游标，再确认。续拉 `after>0` 必须携带 `streamId`；消息流不一致时停止同步，不能沿用旧游标跳读。补拉和确认可以重复，确认不会删除消息。

## 服务器部署边界

dev1 的 Ubuntu/systemd 启动、启用自启动、健康/认证和在线备份已有用户截图证据；服务器重启恢复、真实恢复、HTTPS、手机流量和后台通知尚未验收。dev2 只在本机验证，不能沿用 dev1 的运行结果宣称部署成功。

没有公网域名也可以继续开发。回环监听可经 SSH 隧道联调；正式手机访问再配置 VPN 或认证 HTTPS，保留现有 App 的证书和明文限制。不会为诊断直接把明文业务端口暴露到公网。

## 下一步接入

当前优先完成独立 TTS 中转：模拟协议 → 真实 HTTP 上游的离线假服务验证与私密配置 → 手机原播放器适配 → 私网及经授权的真实验证。角色云端托管仍需受控 Java 业务适配、跨端执行权与上下文同步，不能由 TTS 中转替代。

Android 当前为 1.28.1/code85，SQLite/备份 v18，本轮没有新 APK。旧 `eidolon-backend` 是暂停的 Java/PostgreSQL 实验，本目录 `eidolon-cloud` 是当前自编框架目录。

许可沿用项目 AGPL-3.0-or-later；全文见 LICENSE。FastAPI、Uvicorn 及其依赖保留各自许可。自编代码组织参考上游，但不是上游源码的改名复制或完整服务部署。
