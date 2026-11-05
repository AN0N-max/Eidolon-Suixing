# Eidolon 随行 1.28.2 交接

2026-11-05。最新1.28.2/code86；Java原生+WebView，min26/target35、SQLite/加密备份18不变，原签名升级。主开发源仍为原Codex工程outputs/eidolon-android，无Git元数据；publication只是发布暂存，不能重新克隆覆盖主工程。

基线1.28.1/code85主源码与交付哈希一致，远端main4b860e988cdf4016cfceb757fff263cc6a124729。用户反馈睡前安排在睡醒前跳过、打开手机后执行；截图两个原因是过期及日程变更，均请求0次。没有手机授权日志，不能将整夜失效直接归因Doze/网络/模型或把打开应用当检测到用户醒来。

已改进：ReachScheduler增加独立持久timer与watchdog，保留更早唤醒，SDK31以上申请有界加急，以下普通；AlarmReceiver短转交，新增ReachWakeWorker不调用模型；ReachWake与计划/生成/前台入口记录安全时间线。ReachPlans仅对PENDING/attempts0/原有效期内且revision匹配的计划寻找日程/免打扰/间距空档，同ID原expires不变。过期、已请求、运行/失败/未知计费保持原隔离，不自动补发历史。所有生成仍走原ReachWorker/ReachExecution及原提交，数据库无变更，其他1.28.1/1.28/1.27成果保持。

入口：设置→提醒与后台→主动搭话→今日计划→后台执行线索，可比较唤醒、计划、生成排队/开始、系统停止、前台恢复。生成工作开始不是模型请求次数；系统状态只是读取当时状态，不等同整夜日志。后台设置沿用原入口，不擅自改系统权限。旧跳过记录不会自动复活；用户明确重新安排才采用现有新机会规则。

完整离线、13规则含240窗口、9实际调度器假接口及生产SQL、23页面、Release/Lint/原签名/对齐通过；无真机锁屏或真实付费服务。需后续在用户手机看时间线，再判断系统后台/电池/厂商/网络限制；Android后台没有准点保证。本机构建脚本不能公开，旧APK保留，源码/公告/功能/RELEASE/REPAIR/QA/交接与APK共同发布。细节见同版文档，不重跑本机一次性修改/发布脚本。

APK SHA256：7c524a28f96fec007ef9f5f58b8cf9b24b5382301dd71fe25d7f7695f2a61083。最终GitHub提交见本地GITHUB-PUBLICATION-STATUS.md。
