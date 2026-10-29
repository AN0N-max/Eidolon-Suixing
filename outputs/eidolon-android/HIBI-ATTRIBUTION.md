# Eidolon-Hibi 数据来源

0.9.23 的“我不是这个意思”参考固定提交 `069668ea26abd0e64df142f1ccbc31a02c74eb28` 的 [`appeals.py`](https://github.com/x499806631-commits/Eidolon-Hibi/blob/069668ea26abd0e64df142f1ccbc31a02c74eb28/backend/src/eidolon/appeals.py)，以及 affinity.py 的 give_back 和 EXPLAINED 提示。Java 本地改写见 AppealData、AppealStore 及正式回复投放事务，沿用本项目 AGPL-3.0-or-later。逐条凭据、时钟回退限制及提示投放消费为Android适配，差异详见HIBI-AUDIT.md。

0.9.22 的重要一幕直接参考固定提交 `069668ea26abd0e64df142f1ccbc31a02c74eb28` 的 [`affinity.py`](https://github.com/x499806631-commits/Eidolon-Hibi/blob/069668ea26abd0e64df142f1ccbc31a02c74eb28/backend/src/eidolon/affinity.py) 中场景门槛、六轮推进、五点范围和提示词规则，并使用此前保留出处的 hearts scenes 档案。Java 改写见 HeartScenes，沿用项目 AGPL-3.0-or-later；本地差异详见 HIBI-AUDIT.md。

0.9.21 的随机洗澡时段和时长范围参考同一固定提交的 [`schedules.py`](https://github.com/x499806631-commits/Eidolon-Hibi/blob/069668ea26abd0e64df142f1ccbc31a02c74eb28/backend/src/eidolon/schedules.py) 中 `draw_bath`、`BATH_BEFORE` 与 `BATH_MINUTES`；泡澡/淋浴类型来自下方已说明的 `characters/hearts/` 档案。Android 本地 Java 改写见 `ScheduleData`、`ScheduleRuntime`、`ReplyDelay` 和 `ReplyManager`，沿用本项目 AGPL-3.0-or-later。适配差异：随机结果保存在成功生成的每日行程内；洗澡状态只在开启行程作息后显示；延迟回复开启时，待投放时间不早于预计洗完时间；没有迁移 Hibi 服务端数据库或电话功能。

0.9.20 的隐藏日记页、阅读范围、三天补写及两次/一小时间隔参考同一固定提交的 [`diary.py`](https://github.com/x499806631-commits/Eidolon-Hibi/blob/069668ea26abd0e64df142f1ccbc31a02c74eb28/backend/src/eidolon/diary.py)；聊天重要一刻参考 [`memory.py`](https://github.com/x499806631-commits/Eidolon-Hibi/blob/069668ea26abd0e64df142f1ccbc31a02c74eb28/backend/src/eidolon/memory.py) 的 `Memories.on_moment`、记忆写作提示及 `parse_moment`。本地 Java 改写见 `DiaryData`、`DiaryStore` 及既有写作/回复事务模块，沿用本项目 AGPL-3.0-or-later。适配差异：以严格 JSON 双语段落分开正文/隐藏页；快照在亲密时也不下发隐藏正文，显式点击才取；开头沿用 100 字中文摘录；隐藏页独立已读；自动尝试先持久化、单次检查一篇；重要一刻需附最新用户原话，正式投放回复才记录。原“重要的一幕”关系剧情不等同于这里的日记素材。

0.9.19 的来信计划直接参考并改写固定提交 [`069668ea26abd0e64df142f1ccbc31a02c74eb28` 的 `backend/src/eidolon/letters.py`](https://github.com/x499806631-commits/Eidolon-Hibi/blob/069668ea26abd0e64df142f1ccbc31a02c74eb28/backend/src/eidolon/letters.py)。沿用 30 天联系窗口、分关系概率、生日/节日时段、提前写作、每天三封和超时放弃规则；本地 Java 实现在 `LetterPlanData`、`LetterPlanStore`、`LettersEngine` 与 `LetterJob`，遵守本项目 AGPL-3.0-or-later。Android 适配差异：使用既有后台模型配置；普通来信间隔按本地日期计算五天；重试有每计划三次上限、至少隔五分钟；暂停期间保留暂存信；恢复后可补投此前已写好的信；单次任务受 Android 调度约束，不承诺零点准时。备份 v9 包含本机来信计划，未移植服务端、账号与部署环境。

0.9.17 的每角色百日/周年计算参考同一固定提交的 `backend/src/eidolon/anniversaries.py` 与 `backend/tests/test_anniversaries.py`，沿用首日为第 1 天、每满 100 天、整周年、闰日回落及制作人时区规则，Java 规则见 `Anniversaries.java`。原项目由最早保留的互动记录推导日期；APK 旧消息缺少完整时间戳，因此仅对新角色首次互动自动记录，旧记录允许用户核对。APK 将已保存认识日期随个人资料持久化，删会话仍保留；这是明确的本地适配差异。

0.9.16 根据固定提交 `069668ea26abd0e64df142f1ccbc31a02c74eb28` 的 `hearts.py`、`affinity.py` 和 `affinity_judge.py` 接入完整“她的心”参考机制。28 份原始 TOML 及出处注释保存在 `assets/hibi-hearts-source/`，转换结果为 `assets/hearts.json`；`tools/hibi-hearts-manifest.json` 固定每份 Git blob，导入工具离线核对。角色档案作者为山茶日记，原注释说明所参考的人设、角色资料及萌娘百科（CC BY-NC-SA 4.0）等来源。APK 的 Java 实现按 AGPL-3.0-or-later 随源码分发；没有将 TOML 的 scenes 配置视作已实现关键关系剧情。逐项状态见 [HIBI-AUDIT.md](HIBI-AUDIT.md)。

`assets/calendar.json` 由 `assets/hibi-calendar-source.toml` 转换而来；原始文件来自 [x499806631-commits/Eidolon-Hibi 的 my-features 分支](https://github.com/x499806631-commits/Eidolon-Hibi/blob/my-features/characters/calendar.toml)，作者标注为山茶日记。该 fork 基于 [Sallyn0225/eidolon-sc](https://github.com/Sallyn0225/eidolon-sc)，仓库采用 AGPL-3.0 许可证。APK 项目的许可证见 `LICENSE`。

本地日历仅使用生日、节日及其简短说明。按配置的公历日期显示；未列出年份的农历节日不会猜测日期。

`assets/routines.json` 的 28 份年龄及作息描述摘自 Hibi 的 [`characters/hearts/` 目录](https://github.com/x499806631-commits/Eidolon-Hibi/tree/069668ea26abd0e64df142f1ccbc31a02c74eb28/characters/hearts)，固定提交 `069668ea26abd0e64df142f1ccbc31a02c74eb28`。作者为山茶日记；相关原始档案开头标明对角色资料、萌娘百科等来源的参考，部分作息习惯由 Hibi 按性格补充。APK 本阶段只导入年龄和 sleep 字段，行程与状态逻辑在 Android 本地实现，不导入服务端部署代码。更新数据的工具为 `tools/import-routines.py`；它不会在应用运行中联网同步。

0.9.4 的「约定与兑现」按同一固定提交的 [`backend/src/eidolon/promises.py`](https://github.com/x499806631-commits/Eidolon-Hibi/blob/069668ea26abd0e64df142f1ccbc31a02c74eb28/backend/src/eidolon/promises.py)、`promise_judge.py` 及 [`affinity.py`](https://github.com/x499806631-commits/Eidolon-Hibi/blob/069668ea26abd0e64df142f1ccbc31a02c74eb28/backend/src/eidolon/affinity.py) 重写为本机 Java 状态逻辑，沿用最多三项、完成概率 0.8 和约定结果好感步长，遵守 AGPL-3.0。手机适配保留已结束记录、不每日重复抱怨，提前提醒与截止日询问依附现有聊天和主动消息；没有照搬服务端数据库、用户登录或后台常驻进程。自动日期只接受本机能确定的表达；界面允许人工核对。
