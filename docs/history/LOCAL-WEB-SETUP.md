> 2026-10-19历史网页端部署记录，不是Android APP的安装方法。手机APP请看[入门指南](../GETTING-STARTED.md)。

# Eidolon-SC 本地部署

2026-10-19 安装状态：后端 79 个依赖已安装，前端 512 个依赖已安装，生产构建和 TypeScript 检查通过。正式前端启动后的 HTTP 检查返回 200。后端已通过依赖加载，因尚未填写 `CHAT_LLM_API_KEY` 而停止；实际聊天尚未验证。测试服务已停止。

## 填写模型

双击同目录的 `configure.cmd`，在打开的本地 `.env` 中填写：

```ini
CHAT_LLM_BASE_URL=你的服务接口地址
CHAT_LLM_API_KEY=你的密钥
CHAT_LLM_MODEL=你的模型名称
```

默认接口格式是 OpenAI 兼容 Chat Completions。如果服务使用 Responses 或 Anthropic Messages，请分别将 `CHAT_LLM_PROTOCOL` 设置为 `responses` 或 `anthropic`。模型需要支持流式输出和工具调用，启动时会发送一个小型检查请求。

不要将密钥发到聊天或提交到 GitHub。

## 启动与停止

1. 双击 `start.cmd`。
2. 启动成功后，在浏览器打开 http://localhost:3000 。
3. 点击登录，选择开发登录，按页面说明完成登录。
4. 停止时双击 `stop.cmd`。

服务仅监听本机 127.0.0.1；没有设置开机自动启动。

## 文件位置

项目源码：此文件夹上一级的 `work\eidolon-sc-main`。

配置文件：`work\eidolon-sc-main\.env`。

聊天数据库默认保存在 `work\eidolon-sc-main\backend\data\eidolon.db`，备份时请先停止服务。

日志：`work\logs\backend.err.log` 和 `frontend.err.log`，以及同目录的 `.out.log`。

启动脚本使用此电脑已有的 Codex Python 3.12 和 Node.js 24 运行时。若未来清理这些运行时，需要修改 `start.ps1` 中的运行时路径并重建后端虚拟环境。

## 可选功能

文字聊天只需要模型配置。语音通话还需 Fish Audio 和语音识别服务；剧情库、向量检索、立绘和照片需要另外配置服务或素材。原仓库不包含完整剧情库和立绘素材。

项目文档：https://github.com/Sallyn0225/eidolon-sc
