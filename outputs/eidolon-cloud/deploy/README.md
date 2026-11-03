# Ubuntu 开发框架安装脚本

适用于当前 Ubuntu 24.04 服务器，以普通登录用户运行，不要给整个脚本加 sudo。

将 `eidolon-cloud-setup.py` 上传到服务器用户的主目录，然后运行：

```bash
python3 ~/eidolon-cloud-setup.py install
```

脚本下载并校验固定提交的 0.1.0.dev1 源码包；如果此前已下载，会复用校验通过的 ZIP。默认安装目录为 `~/eidolon-cloud-dev1`，独立数据目录为 `~/eidolon-cloud-data`。解压前检查全部已有源码，发现本地修改就停止，保留原文件。

脚本先等待之前的依赖安装进程退出，复用通过版本和导入检查的虚拟环境；缺少依赖时才安装，只有系统 venv 组件安装使用 sudo。Python 依赖默认通过清华 PyPI 镜像下载，可用 `--pip-index https://pypi.org/simple` 指定官方源。

安装完成后自动后台启动并检查健康接口和未认证请求的 401 响应。重复运行不会重复启动。后续命令：

```bash
python3 ~/eidolon-cloud-setup.py status
python3 ~/eidolon-cloud-setup.py logs
python3 ~/eidolon-cloud-setup.py stop
python3 ~/eidolon-cloud-setup.py start
```

`restart` 为优雅停止后启动。停止会验证进程身份，仅向本用户、当前安装目录的框架进程发送 SIGTERM，保留源码、数据库和日志。安装中断后可重跑；如有外部安装进程长期未结束，脚本会停止等待并保留进程，详情见原安装日志。

服务仅监听 `127.0.0.1:8787`，不会配置开机启动或开放公网端口。这一步部署的是基础框架；完整业务适配、手机私网连接尚未接入，健康接口仍应返回 `domainConnected:false`。脚本不需要模型密钥，不调用付费接口。

验证：Windows 上 9 项部署安全测试通过，覆盖校验失败、已有修改的完整预检查、重复解压、路径逃逸、进程身份和旧安装进程识别。Ruff 和 Python 编译检查通过。尚未实际在 Ubuntu 上运行本脚本，不能将本地测试写成服务器部署成功。
