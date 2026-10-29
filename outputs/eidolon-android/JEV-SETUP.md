# Jev 判断模型（0.7.2）

参考原仓库 `backend/src/eidolon/judge.py`、`retrieval.py`、`affinity_judge.py` 和 `.env.example`。

1. APK 中打开 设置 → 后台与判断模型 → 判断模型。
2. 判断协议选择「Jev System One」。默认填入 `https://openrouter.ai/api/v1` 与 `typesafe/jev-1.13`；把你自己的 OpenRouter API Key 填入独立密钥后保存。也可以把接口改成 TypeSafe 直连的 `https://api.typesafe.ai/v1`，模型名用 `jev-1.13.0`。
3. 聊天回复模型仍在 设置 → 模型服务 单独配置 DeepSeek、Gemini 或兼容服务。Jev 专门判断，不替代聊天 LLM。

启用好感度或剧情检索时，Jev 走 `{接口地址}/systemone`，发送角色、最近四条对话、用户当前消息和对应结构化问题。好感度按原仓库 Jev 阈值处理：不该说的话 0.7、操纵 0.4、检索/回忆/制作人记忆 0.5。自动记忆正文仍交后台模型提取；Jev 只选择是否带入已有相关记忆。剧情命中后由本机已导入剧情库 FTS 检索，不会把剧情库整体发给 Jev。

密钥保存在 Android Keystore 加密的本地保险箱。换成另一家的接口时需填写相应的独立密钥。清除应用数据会清除密钥。

此 APK 尚未实现原网页中 Jev 的专用照片判断（手机端没有生图功能）、通话情绪判断、向量检索和重排。Jev 判断失败时普通聊天继续；本轮不改好感，不写入自动记忆，剧情检索退回本机关键词检索。API 费用依你的 Jev/OpenRouter 账户而定。

验证：APK 编译、v2/v3 签名通过。未使用真实密钥，没有对 Jev/OpenRouter 发真实请求。
