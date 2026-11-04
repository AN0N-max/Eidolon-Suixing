# 1.28.1 修改与原因

## 已确认的源码缺陷

1. SocialFailure原先仅按异常类型将任何JSONException写成“模型返回不是完整JSON”。但SocialEngine在调用模型前阶段为prepare，调用前改为request，返回后才为parse。截图的format/prepare至少说明错误说明不可信，不能视为响应正文错误；旧版本未采集字段，不能反推具体手机数据。
2. SocialStore.claim原先在准备资料前插入social_usage，准备异常也占正文额度。现在先条件领取任务，完整准备后reserveRequest在事务中检查身份、配置revision、日期、到期、额度、账本唯一性，再登记请求。即使进程在登记后中断也不自动重放。原账本不删除；仅FAILED且明确未发送的诊断（含旧format/prepare/JSONException）从统计中扣除，未知仍保守计数。
3. 每任务阶段此前未重置，可能继承上一任务阶段；现在逐任务重置并细分。任务仍独立身份/取消、保留同角色文字/通话保护和提交守卫。
4. SocialData.object原先从第一个左花括号截到最后一个右花括号，不能可靠区分包装、多个对象及截断。现在有界扫描字符串/转义/深度，限定唯一对象并受控解包；缺失字段/语法/输出长度等原因分开。不会第二次付费修复或编造缺失内容。
5. 角色东京朋友圈准备原先WeatherRuntime.known同时关联用户城市和天气请求。现在只取允许使用的东京缓存；可选结构异常跳过并保留警告，核心角色/地点错误仍报告。所有29名角色原始内置资料离线可准备，不代表手机私有数据正常。

## 修改文件

| 文件 | 作用 |
| --- | --- |
| src/com/eidolon/sc/mobile/SocialReply.java | 新增有界响应解包、双语规范化、安全错误码 |
| SocialData.java | 复用响应解析，保留地点/角色/赞评候选过滤 |
| SocialBackup.java | 备份沿用存储文本校验，保留历史双语原文 |
| SocialEngine.java | 任务阶段重置、准备共用方法、可选参考隔离、只读诊断、发送前登记额度 |
| SocialFailure.java | 阶段分类、字段/代码位置脱敏、旧记录显示兼容及确定未发送判断 |
| SocialStore.java | 原子发送登记、原账本保留的确定证据额度统计、状态读取兼容 |
| SocialActions.java | 只读socialDiagnose桥接入口 |
| assets/ui/social.js、social.css | 本地资料检查、阶段说明、重试独立布局 |
| assets/update-notices.json | 应用内1.28.1公告 |
| build.gradle、test.ps1 | 1.28.1/code85与测试入口；本机构建脚本仅本地输出路径更新 |
| tests/com/eidolon/sc/mobile/SocialRepairTests.java | 28项解析/诊断/备份/旧数据回归 |
| tests/test_social_preparation.py | 实际生产准备方法，29角色及损坏可选/核心字段隔离33项 |
| tests/test_social_admission.py | 实际SQL的额度、并发、身份、保守计费SQLite夹具 |
| tests/social-ui.cjs、test_shared_store.py | 本地诊断无服务调用、布局和版本构建路径 |

Java表内省略目录的文件均在src/com/eidolon/sc/mobile。发布另含RELEASE/REPAIR/QA、功能说明、交接和便携构建/测试文档；私有签名脚本不公开。

## 保持与边界

没有复制聊天、角色或朋友圈系统，没有升级数据库，没有重写历史聊天/动态，没有删除旧请求账本，没有修改1.28通话/ASR/时间算法。新输出复用DialogueText、ReplyMarkup，UI继续textContent安全呈现；没有放开任意HTML。备份不因新输出规范拒绝原有合法存储文本。核心资料损坏不通过编造地点或人物掩盖，用户可读取脱敏诊断后定位。

本机不能读取未授权手机的实际异常，因此尚不能证明截图每条失败的具体字段或服务响应。旧泛化错误没有细节时只说明证据不足；不伪造诊断。服务格式错误只做本地兼容，真正截断/缺字仍明确失败，由用户新身份重试，不无限重复收费。
