# Agent Note: 动态头像与按展示场景选择角标

Status: proposed

## Problem

当前 Avatar 只读取 avatar_template，并把整个容器裁成圆形。官网缩略图可能已经静态化，增加 GIF 解码器仍拿不到原动图；圆形外裁切也会截掉右下角资质或状态。本人账户入口和公开个人页即使显示同一用户，角标优先级仍不同。

## Proposal

用户已批准按数据、动图、统一展示三个单元实施，各单元独立提交。数据单元先修正帖子作者使用 user_id 的身份契约，再接入状态、资质、原动图和按需批量补齐；动图单元接入 APNG、GIF、动态 WebP；展示单元统一所有调用点与真机验收。

公共 Member 保存 animated_avatar、flair_* 和状态。账号、用户资料、用户卡片使用 status；帖子使用 user_status。列表没有状态，只对缺少资质且状态未知的可见作者合并请求 /user-cards.json，使用有上限的短期内存缓存。完整详情中的空状态表示确实没有状态，不能拿旧缓存补回。缓存随账号切换销毁，卡片失败只影响角标，不能使列表报错。自定义表情 URL 来自 /emojis.json，复用既有 HTTPS 图片请求边界。

Avatar 的展示场景显式区分账户入口和公开内容，不能用“是否本人 ID”推断：账户入口只展示有效状态，公开内容优先展示资质，无资质时展示有效状态；自己的公开个人页同样使用公开规则。状态 ends_at 到期后隐藏。图片和角标分开布局，只裁切头像本身。Neo 的用户名按官网规则匹配，圆角为边长 10%，其余用户均为圆形。

图片优先使用 animated_avatar，失败回退 avatar_template。按内容识别格式，不以 .png 后缀判断；APNG 需要额外解码能力。动画只在前台可见时播放，离开组合或退到后台后停止；资质动图复用同一能力。

## Evidence and related notes

2026-10-08 官网只读核查：common_theme_138 明确为 /neo/ 头像设置 border-radius:10%；Neo 的 flair_url 是 battery-quarter，颜色 #00aeff / #fff。当前账号入口显示 no_entry_sign 状态，公开资料显示 GIF 资质。热门列表返回 GIF、WebP 的独立 animated_avatar，常规 .png 头像实际可能为 JPEG。/user-cards.json?user_ids=1,218,18538 与 /emojis.json 返回 200，后者含自定义 bili_118 的上传地址。脱敏字段证据在 artifacts/avatar-web-*.json。

历史笔记检索分类：[原生架构](../../implemented/architecture/2026-10-07-native-community-client.md)、[数据契约](../../implemented/architecture/2026-10-07-discourse-data-contract.md)、[账号入口](../../implemented/feature/2026-10-07-account-and-composer.md)、[界面密度](../../implemented/feature/2026-10-07-coolapk-density-and-motion.md) 部分重叠，分别保留网络、会话、导航和触控尺寸的决定；[客户端验证](../../implemented/testing/2026-10-07-client-verification.md) 保留真机测试流程。不归档任何旧篇，未发现需否决的同主题提案。

## Alternatives considered

- 只注册 Coil GIF 解码器：集成少且沿用图片缓存，但不能解决 APNG，也无法从静态缩略图恢复原动画。
- LINE ApngDrawable：接口简单、支持 Android Drawable，但原生层加载全部帧，指定显示尺寸不等于降低原始帧内存；列表中多头像更适合按帧解码与采样的实现。
- 每个 Avatar 请求用户资料：组件独立、更新直接，但滚动会造成请求风暴；采用可见请求合并、批量用户卡片和有上限的短期缓存。
- 用当前用户 ID 决定角标：无需增加展示参数，但会错误地把本人帖子和本人公开个人页也换成状态；使用显式场景。
- 以 admin 字段决定头像形状：能适应新增管理员，但官网样式只指向 Neo，其他管理员仍应圆形；按站点现有规则匹配用户名。

## Acceptance criteria

解析测试覆盖帖子用户 ID、两种状态字段、空值与错误类型、资质图标和图片、独立动图地址。批量补齐覆盖合并、缓存上限与过期、失败退避、账号销毁和取消。确定性 APNG/GIF/WebP 多帧样本在真机确实换帧；后台、移出列表停止播放，失败回退静态头像。界面验证所有入口的形状、本人公开页、资质优先级、自定义表情、状态过期和明暗主题。

## Risks

原始动图可能远大于缩略图，解码应限制输入与画布，并按目标尺寸采样。自定义表情和资质依赖远程资源，错误时保留头像和可读语义。用户卡片可能因访客权限或验证不可用，此时仅显示已返回的元数据。通用状态更新以重新进入页面及缓存有效期为准，不增加独立的状态推送连接。
