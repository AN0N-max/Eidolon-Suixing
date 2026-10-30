# 发版时维护应用内更新公告

每次发版必须更新assets/update-notices.json，在releases开头加入本版公告：version与build.gradle的versionName一致，code与versionCode一致且严格递增，title为用户可读标题，sections列出本次实际新增、修复、调整及已知限制。历史版本向后保留，不把历史设置描述当作现行行为。

公告是安装包内的只读资源，WebView更新公告页与原生关于窗口共用，不需要登录、网络或模型。更新安装包后显示随该包发布的公告，不远程热更新。默认展开当前版本，不自动弹窗或改变聊天未读。

1. 修改版本号与构建/测试输出目录，补充对应RELEASE-版本.md。
2. 增加本版update-notices.json内容，校验code排序、唯一、标题及条目非空；不要提前写尚未交付的功能。
3. 运行tools/check-release-notes.ps1。两个PowerShell构建入口都执行此校验，缺失或版本不符会拒绝构建。直接Gradle构建不经过脚本时应先手动执行该检查。
4. 运行build.ps1、test.ps1及与改动相关的界面检查。公告页测试为tests/release-notes-ui.cjs，使用假本地桥接，不调用模型。
5. 校验安装包版本、签名、16KB对齐及公告资源与源码一致。原升级签名仅由维护者本机保存，公开源码不包含密钥库。
6. 同步README、CHANGELOG、downloads目录、源码与发布说明，核对远端提交及安装包哈希。AGPL源码与实际安装包应对应。

公告页入口位于设置 → 数据与应用，紧邻关于；使用统计页和关于窗口也可进入。settings.js中的入口摘要也应随发版校对。关于标题直接读取安装包版本，避免手工版本号滞留。
