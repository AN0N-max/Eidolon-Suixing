# 1.8 设置与页面响应优化

基于现有1.7/code60继续开发，未重新拉取仓库。版本1.8/code61，包名com.eidolon.sc.mobile、原升级签名、SQLite与加密备份v13保持。

## 修改

- 设置首页和六个分组直接使用当前数据切换，不等待全量快照；具体页面显示读取状态后异步加载，可随时返回，旧导航结果不会抢回页面。保留未保存角色提示词的离开确认。
- SnapshotRequest在界面线程捕获角色/会话/操作版本，UiSnapshots独立本地执行器读取和序列化；使用自己拥有的ChatStore，不与模型生成队列共用。保存设置后的快照及日记私密页嵌套快照同样异步组装。状态改变时重新读取，避免旧查询覆盖刚保存的数据。
- 快照按页面加载：聊天记录只在聊天页读取，信件日记正文只在相应阅读页读取，统计只在统计页读取，主动设置的详细额度只在相应页读取。其他页面保留已有数据并合并更新，不传输无用的会话状态/长期记忆数组；角色提示词与日记隐藏正文继续不进入普通快照。
- 未读使用只含元数据的查询，保持关系权限及隐藏页已读规则；角色预览和最近活动合并为两次查询，最新消息仍使用已有character_messages索引，不遍历全部聊天正文。
- 页面读取不保存整份个人资料或执行信件投放。生命周期维护在后台事务内执行，正常数据改动仍沿用原保存入口。
- 界面只检查是否保存密钥，不为显示配置状态反复解密；真实请求仍用原Vault解密。兼容旧版加密空密钥，未改变密钥值或存储身份。
- 合并返回应用时的收件刷新，更新角标时不重建设置表单。保留草稿、滚动位置、未读与日记降级校验。新增仅含阶段/耗时/大小的内存与日志诊断，不记录聊天、正文或密钥。

设置分组和功能入口保持。没有重写成纯原生界面，也没有调整模型请求数量、后台玩法或数据库结构。

## 验证

最终构建与验证结果将在检查完成后追加。模拟350ms读取延迟证明设置目录不被该读取阻塞，并不能证明手机上的毫秒数。离线规则与SQL不能替代Android界面线程、WebView和OriginOS实际性能分析。本轮未安装手机、未调用真实API。

## 最终验证结果

- build.ps1成功，原升级证书及16KB对齐通过；aapt核对com.eidolon.sc.mobile、1.8/code61、minSdk26/targetSdk35正确。
- test.ps1全部Java/SQLite回归通过；新增14项读取范围规则，包含128种信件/日记权限与未读组合；28000条模拟消息的生产SQL与旧逐角色结果一致，并确认使用已有索引。
- 全部UI脚本语法通过。新增ui-performance-ui模拟350ms桥接读取，验证目录零快照请求、可取消的慢页面、合并刷新、未保存输入保留、部分响应合并、读取失败后重试及窄屏/深色布局。
- settings-ui、view-position-ui、unread-entry-ui、inbox-ui、diary-ui、role-prompts-ui、notebook-ui、chat-time-ui、writing-plans-ui、plans-ui、plan-recovery-ui、reach-settings-ui、reply-background-ui、writings-ui、anniversaries-ui、appeal-ui通过。最后改动涉及的读取与表单刷新检查已重跑。
- 包内所有assets/ui文件逐一与源码一致；DEX包含UiSnapshots、UiReadScope及捕获状态的SnapshotRequest。
- 构建/离线日志：work/build-1.8.log、work/test-1.8.log。界面截图outputs/ui-1.8-settings.png为本地模拟画面。
- APK SHA-256：848744E513830C6CA30DF2170751CDCF406607DFE795A1351CC65FE53E6A9843。

没有安装手机或调用真实模型。上述耗时来自主机和模拟桥接，不代表OriginOS/WebView实际帧率；手机内大数据量、系统负载与后台全链路仍需实机体验。
