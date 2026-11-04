# Eidolon 随行云端运维交接：2026-11-04

## 真实版本与范围

实际 Android 主源码已是 1.28/code84，SQLite/加密备份 v18；已读 1.28 交接与发布说明。本轮没有修改 Android、APK、签名、语音或业务框架源码，也没有回退原规则。

GitHub main 已核对为 2e9b3479e6596e07110c83098814ebcc375f471b。云端开发分支在本轮开始时为 d2a2ccda6820ed6a0c85d75f934d4bb6a3780305；该分支早于后续 Android 发布，不得用其整树覆盖 main 或本地 1.28。仅对开发分支提交本批云端工具增量，不合并 main。

原云端框架源码包保持 0.1.0.dev1 与固定摘要，未重打包。新增运维工具版本 2026.11.04.1。原工具与旧安装包保留，新工具以版本文件名交付。

## 已实现

- 原单文件部署工具增量增加 service-install、service-disable、check、backup、backup-check、restore；原 start/stop/status/restart/logs 在接管后统一管理 systemd。
- service 预检、同名服务/修改/drop-in/未重载保护、接管前快照、旧进程身份检查与优雅停止、归档 PID、单服务常驻、异常重启、开机启动及 JSON 回执。
- 继续回环监听、非 root 运行、数据目录写入限制、日志限速和 journald 管理，不开放公网业务接口。
- SQLite 在线备份包含已提交 WAL 数据；独立快照、SHA256、schema/完整性/外键检查；恢复要求停服与明确摘要，冻结输入并保留恢复前快照，不自动启动或删除旧数据。

主要文件：outputs/eidolon-cloud/deploy/eidolon-cloud-setup.py、deploy/README.md、deploy/OPS-2026-11-04.md、tests/test_ops_script.py。本说明随源码增量公开，公开内容不包含真实回执、备份、数据库或设备密钥。

## 实际验证

本机 Windows/Python 3.12.14：全部 48 项测试通过，0 skipped，包含 18 项新增运维测试、9 项旧部署安全测试及 21 项原框架/实际 Java 日切桥接。Ruff E4/E7/E9/F/I、格式与 Python 编译检查通过。systemd 路径为模拟测试，不是 Ubuntu 验收。

原 1.22 业务摘要检查不通过：23 个文件中有 12 个已被后续 Android 发布修改，与当前公开暂存副本一致（忽略换行）。保留旧摘要，不将其更新成“当前通过”。原 DailyScheduleClock 仍可通过实际 Java 桥接；完整云端业务适配未来必须按 1.28 重审，不能沿旧 1.22 规则提取。

## 尚未完成

Workbench 入口新标签可在浏览器清单中看到，但页面读取持续超时并重置；SSH 因缺少已核验 ED25519 指纹被严格检查拒绝。没有获得可靠 Shell，没有在服务器运行新工具；没有安装 systemd 服务、重启服务器、恢复真实数据或创建真实备份。用户之前截图只证明旧部署工具当时能启动基础框架。

交付运行文件后，下一步在可用 Workbench Shell 运行新版 check → service-install → status → backup，核验进程、日志、回环监听和备份摘要，再单列服务器重启/故障恢复验收。不要为测试覆盖当前数据库。

手机继续保留速云加速器。私网兼容方式、TTS 出口环境、Android 诊断连接包、完整业务托管均未实现；未调用真实付费模型/ASR/Fish 服务。

限制和恢复命令详见 outputs/eidolon-cloud/deploy/OPS-2026-11-04.md。当前仅支持已有且有效数据库的数据回退，不支持空目录灾难恢复；同机手动快照不等于异机自动容灾。恢复可能回退设备撤销与游标，需检查相关状态。
