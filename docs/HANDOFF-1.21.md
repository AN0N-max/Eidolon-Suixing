# Eidolon 随行 1.21 开发交接

继续当前outputs/eidolon-android工程，不重新克隆覆盖。当前1.21/code76，Java原生+WebView，min26/target35，SQLite及加密备份v17，原升级签名。基线1.20电话/百炼系统保留，新增多模态文档见MULTIMODAL-1.21.md。

新增MediaData、MediaStore、MediaActions、StickerEngine、MediaReply、MediaContext及media.js/media.css。扩展ChatStore.Message.blocks、messages.content，全部历史/翻页/批次/备份保留有序块；旧消息默认[]。共享私有媒体目录chat-media，哈希寻址，Exif方向修正、静态首帧与缩略图。角色偏好、语义档案、资源权限和分析缓存独立持久化。

原ConversationEngine/ReachExecution复用人设、关系、记忆检索、生活与时间；少量候选由模型选择，正式投放再校验。附件轮不自动提取长期事实或约定，未知事件日期不认定上传日。图片视觉默认关闭，设置可选原生image_url或独立兼容模型，密钥快照/隔离。电话不发表情，纯表情不提供TTS朗读。含附件的生成中断要求明确重试，不自动重复未知付费请求。

原离线回归、28项多模态Java、SQLite及8组假原生页面检查通过；没有真实付费调用和手机安装，实际识图/角色表达、原生选择器/图片内存、延迟/费用未验收。APK与源码文档同步时必须保留这个验证范围，不能宣称真实模型理解已通过。

本机资源64MB/2000张，规范化单图1.5MB，每次4张；媒体备份12MB/总加密包32MB。删除表情目录不删除已发送图片，清理保护历史/目录/草稿引用。旧APK不能直接降级读取v17。下一阶段优先真机备份/选择器与获授权真实视觉服务验证，再扩展动画或大媒体包。
