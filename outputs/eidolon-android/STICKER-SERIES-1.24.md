# 1.24 表情系列偏好、导入格式与文件清单

## 用户操作

先安装1.24原签名升级包，再到设置→聊天与关系→表情整包导入选择本地ZIP，预览图片与参数后确认合并。表情库中仍保留整包与单图入口。

包中的角色偏好默认补充缺失项：新角色获得包内频率/用途/风格和系列配置；已有角色只补缺失的preferredSeries、blockedSeries，保留原频率和风格。已有显式空列表视为用户的“不限定”，不覆盖。若要更换已设系列，在表情包资源库→当前角色偏好中编辑，选择角色、最喜欢/次选系列并保存。禁用系列可逗号分隔填写。

本次本地八系列184张：吉伊卡哇24、Esther Bunny20、兔丸24、猫与熊20、兔子100%24、大耳狗24、卡娜赫拉24、正能量企鹅24。mofusand、内裤兔及整个んぽちゃむ系列不加入。最终包声明停用内裤兔/んぽちゃむ；预览会显示旧库当前启用数量和可取消的默认勾选项，只有确认导入才停用。不会删除文件、旧消息或使用记录，日后可在资源库重新启用。

## 选择规则

系列身份单独存储，不能把“开心”“晚安”等用途当作IP偏好。原StickerEngine先检查角色频率、间隔、权限、禁用分类/系列、最近使用，再对当前消息和逐图语义做原Recall词项匹配。没有当前话题匹配的图片不进候选。主用系列权重1.6、次选1.3，其余1；最多8个候选仍交给原人物模型按当时关系/情绪/语境选择，最多发送一张，也可以不用。权重是偏好而非强制系列白名单，不保证每条都用主用系列。

不引入第二个人物系统，不改ConversationEngine/通话/数据库表。亲密表情仍依靠语义和已有关系上下文约束，未新增数值好感阈值。历史消息snapshot增加series，备份沿用现有v17 JSON字段与媒体资源。旧精选库缺少series时，只对已知“系列·名称”前缀推断；不把未知自定义名字猜成品牌，显式空series保持未指定。

## version 2格式

原version 1包仍支持。新v2包每张必须有非空series（最多80字符），profiles可加preferredSeries（按主用到次选排序）和blockedSeries。根级disableSeries是本次确认后可选停用的旧系列列表，最多20项，不得同时包含包内图片所属系列。profile列表字段最多20项、每项80字符且不能重复。v1不接受v2的系列偏好和停用指令，避免旧应用忽略这些指令。包内series不能表达版权许可。

```json
{
  "format": "eidolon-sticker-pack", "version": 2,
  "id": "my-series-v2", "name": "我的系列包",
  "disableSeries": ["不再使用的旧系列"],
  "stickers": [{"file":"images/ok.png", "sha256":"制作脚本计算",
    "name":"收到", "series":"我的原创角色", "category":"赞同回应",
    "description":"点头表示收到", "intent":"确认理解", "characters":[]}],
  "profiles": [{"character":"mano", "profile":{
    "frequency":"low", "preferredSeries":["我的原创角色"], "blockedSeries":[]}}]
}
```

通用制作脚本：python tools/build-sticker-pack.py 素材目录 输出.zip。沿用1.23哈希、文件/展开大小、路径、图片解码及角色编号校验；不执行远程下载、不直接解压路径、不调用付费API。详细基础格式见[1.23格式](STICKER-PACK-1.23.md)。最终精选包仅本地交付；来源和作者记录在包的manifest中，公开代码与APK不预装这些图片。

## 增量文件清单

- 新增StickerRank.java，有限系列加权与当前话题准入。
- StickerEngine.java：复用原候选链路，调度排序 helper 与模型提示。
- MediaData.java：系列读取/旧前缀兼容、共享禁用检查、历史snapshot。
- MediaStore.java：原JSON中添加/更新系列和角色新字段，列表返回规范化系列，没有改表。
- StickerPack.java、StickerPackImport.java：v1/v2校验、重复项补series、角色缺失字段补充、原事务中可选停用旧系列。
- assets/ui/media.js：单图所属系列、角色主用/次选/禁用系列；assets/ui/sticker-packs.js：系列预览、停用数量及确认选项。
- tests/StickerPackTests.java、StickerSeriesTests.java（均在com/eidolon/sc/mobile下），tests/media-ui.cjs与sticker-packs-ui.cjs；通用制作工具、构建/测试版本路径、应用内更新公告和发布文档。

## 验证边界

见[发布记录](RELEASE-1.24.md)。生产解析器和候选算法在JVM执行；WebView页面以桌面浏览器和假原生桥执行。它们不能代替Android文件选择、解码/导入/备份往返或真实模型选择质量。未操作手机、未发起付费API请求。原生通话硬件/服务验收边界继续保留。
