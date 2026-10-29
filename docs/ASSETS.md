# 公开资源与来源

本仓库不上传个人聊天、API密钥、签名私钥、备份、剧情数据库、语音模型/音频或原作立绘/Spine素材。Android版本本身不包含Spine runtime；保留的上游网页端如果使用该runtime，须遵循上游LICENSING.md及其独立许可证。

Android assets/characters.json 是从公开上游的角色配置、人设和资料转换的静态数据；assets/avatars/ 为上游公开头像的原文件，逐个按配置核对一致，不是原作立绘包。人设、资料和头像的公开范围依据上游 ADR-0003 后续修订。音色ID只是服务配置标识，不包含模型或音频；是否可用受提供方限制。

礼物目录与图片取自公开上游。calendar、routines、hearts以及保留的TOML来源和作者说明见 HIBI-ATTRIBUTION.md；不把这些资料的出处改为本分支原创。应用矢量图标与UI源码一并提供。

AndroidX及其他依赖保留assets内的第三方许可证；Vivo桌面角标兼容参考的ShortcutBadger说明保存在assets/licenses/shortcutbadger-notice.txt。

项目代码为AGPL-3.0-or-later。角色与设定属于各自版权方，依赖和素材不因本项目代码许可证而自动获得额外授权。原项目与本分支均为非官方粉丝作品。
