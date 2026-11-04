# Eidolon 随行 1.26 交接

1.26/code82，Java Android原生+WebView；min26/target35；DB/加密备份18。主源码是现有eidolon-android，没有Git元数据，不能用重新克隆覆盖。公开publication只是暂存副本。上一公开版本1.25.1/code81，main基线8685d05ddc9d8c68be7d787c30e60781165fcaa3，发版使用lease复查，不覆盖远端后续提交。

用户确认本轮仅朋友圈、天气、城市、日程修复；生图和视频中的手动给角色排工作暂缓。UI参考原上游及作者两条视频；新配置归总设置。完整行为、参数、入口和限制见[RELEASE-1.26](../outputs/eidolon-android/RELEASE-1.26.md)，文件索引见[SOCIAL-1.26](SOCIAL-1.26.md)，验证见[QA-1.26](QA-1.26.md)。

保留核心：同角色待回复文字优先；聊天/通话原ASR、模型、Fish、播放和epoch链沿用；未播完不算听见，不重复关系/记忆效果；退出取消，Home/锁屏沿用前台服务；进程死亡不重放未知付费请求；不改桌面角标和表情资源/偏好。朋友圈不写关系/记忆效果，也不复制人物系统。

日程：1.25.1格式修复继续保留；新增292号持久JobScheduler逐位检查、实际忙碌判断和只读原因；ScheduleAttempts在请求前落库并以token隔离迟到响应；失败不自动重试，旧失败需用户手动重新安排。03:00按角色时区，额度按制作人日计，等待送达不等于模型生成。未来若改日程请保留这套identity、WritingStore.epoch、取消和事务保护。slots.kind只做可选work/school/daily元数据，没有手动排班页。

社交：新增7张social表和schedule_attempts共8表；任务CAS与usage同事务，关闭/改配置/删评论/恢复/进程中断均隔离旧回调；付费任务不导入，账本保留并合并，不重放未知请求。新功能默认关闭，恢复后仍关闭。单角色一天一条、每日请求限额含失败，不保证每人每天一条；后台时刻为Android近似调度。可见ID已读与聊天角标分离。天气缓存和凭据不进备份。

天气：东京Open-Meteo、用户城市和风，短上下文等待与一小时缓存；失败十分钟冷却，不自动重试。CityLocator仅用户主动一次大致定位，原始设备坐标不持久化。CONFIG_LOCK+revision+城市中心隔离迟到返回。JWT使用BC lightweight Ed25519，keyId/developer/project齐全；唯一credentialSet槽防新凭据配旧Host，清理只限weather前缀，清理失败会显示提示，原聊天/Fish密钥不动。

开发验证使用本地SQLite、假模型/假天气及Chrome假桥，未调用真实付费服务，没有手机操作。不得将离线通过写成真实模型/定位/Android后台通过。继续使用原升级签名，保留旧APK；私有构建脚本不可提交。安装包优先本地链接，不经QQ发送。下次发版源码、APK、公告、功能说明和交接同时同步。

最终包SHA256：e49451c257f2541f632a866899e56b60091690a5282248101d2b933fafc21134。Release/原签名/16KB对齐、完整离线、50队列规则、446社交天气规则与15组页面回归通过。APK本地优先交付，公开保留旧包。
