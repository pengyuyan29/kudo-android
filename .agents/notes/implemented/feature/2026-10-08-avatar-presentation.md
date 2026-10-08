# Agent Note: 动态头像与按展示场景选择角标

Status: implemented

## Problem

只读取 avatar_template 无法可靠展示动画：官网缩略图可能已经静态化，增加 GIF 解码器仍拿不到原动图；把整个容器裁成圆形也会截掉右下角资质或状态。本人账户入口和公开个人页即使显示同一用户，角标优先级仍不同。

## Decision

首页账户入口、“我的”、话题列表、楼层和公开个人页复用同一个 Avatar。组件显示官网已有的头像与状态、资质设置，不增加编辑入口；数据、补齐缓存、动图解码和统一展示各自保持独立边界。

公共 Member 保存 animated_avatar、flair_* 和状态。账号、用户资料、用户卡片使用 status；帖子使用 user_status。列表没有状态，只对缺少资质且状态未知的可见作者合并请求 /user-cards.json，使用有上限的短期内存缓存。完整详情中的空状态表示确实没有状态，不能拿旧缓存补回。缓存随账号切换销毁，卡片失败只影响角标，不能使列表报错。自定义表情 URL 来自 /emojis.json，复用既有 HTTPS 图片请求边界。

Avatar 的展示场景显式区分账户入口和公开内容，不能用“是否本人 ID”推断：账户入口只展示有效状态，公开内容优先展示资质，无资质时展示有效状态；自己的公开个人页同样使用公开规则。状态 ends_at 到期后隐藏。图片和角标分开布局，只裁切头像本身。Neo 的用户名按官网规则匹配，圆角为边长 10%，其余用户均为圆形。

图片优先使用 animated_avatar，失败回退 avatar_template。按内容识别格式，不以 .png 后缀判断；APNG 需要额外解码能力。动画只在前台可见时播放，离开组合或退到后台后停止；资质动图复用同一能力。

动画暂停直接绑定 LifecycleResumeEffect 的 onPauseOrDispose。仅收集生命周期状态后等 Compose 重组不能保证后台停止，因为宿主暂停时重组器本身也会暂停；图片在后台完成加载时还必须立即停止 Drawable。可见性控制保留，用于首页折叠后仍在组合中的头像。全局加载器由 Application 提供，初始化与受限网络配置见[原生架构](../architecture/2026-10-07-native-community-client.md)。

## Evidence and related notes

2026-10-08 官网只读核查：common_theme_138 明确为 /neo/ 头像设置 border-radius:10%；Neo 的 flair_url 是 battery-quarter，颜色 #00aeff / #fff。当前账号入口显示 no_entry_sign 状态，公开资料显示 GIF 资质。热门列表返回 GIF、WebP 的独立 animated_avatar，常规 .png 头像实际可能为 JPEG。/user-cards.json?user_ids=1,218,18538 与 /emojis.json 返回 200，后者含自定义 bili_118 的上传地址。脱敏字段证据在 artifacts/avatar-web-*.json。

历史笔记检索分类：[原生架构](../architecture/2026-10-07-native-community-client.md)、[数据契约](../architecture/2026-10-07-discourse-data-contract.md)、[账号入口](2026-10-07-account-and-composer.md)、[界面密度](2026-10-07-coolapk-density-and-motion.md) 部分重叠，分别保留网络、会话、导航和触控尺寸的决定；[客户端验证](../testing/2026-10-07-client-verification.md) 保留真机测试流程。不归档任何旧篇，未发现需否决的同主题提案。

## Alternatives considered

- 只注册 Coil GIF 解码器：集成少且沿用图片缓存，但不能解决 APNG，也无法从静态缩略图恢复原动画。
- LINE ApngDrawable：接口简单、支持 Android Drawable，但原生层加载全部帧，指定显示尺寸不等于降低原始帧内存；列表中多头像更适合按帧解码与采样的实现。
- 每个 Avatar 请求用户资料：组件独立、更新直接，但滚动会造成请求风暴；采用可见请求合并、批量用户卡片和有上限的短期缓存。
- 用当前用户 ID 决定角标：无需增加展示参数，但会错误地把本人帖子和本人公开个人页也换成状态；使用显式场景。
- 以 admin 字段决定头像形状：能适应新增管理员，但官网样式只指向 Neo，其他管理员仍应圆形；按站点现有规则匹配用户名。
- 只由 Compose 状态驱动动画停止：可见性与生命周期都能汇成一个布尔值，调用简单，但 Activity 暂停时重组器可能已停止工作；直接绑定生命周期清理，状态仍负责滚出屏幕后的暂停。

## Verification

新增20项JVM测试覆盖帖子用户 ID、两种状态字段、空值与错误类型、资质、独立动图地址、头部检测和角标策略；批量补齐覆盖合并、缓存上限与过期、失败退避、账号销毁和取消。当前全部57项JVM通过。

RMX2202 / Android 13 的9项头像真机测试通过（19.357秒，artifacts/avatar-lifecycle-instrumentation.txt）。自制64px APNG/GIF/WebP样本在真实加载器的32px画布上渲染三种帧色；另强制执行旧版WebP分支。真实 Activity 后台与恢复、组件移除、首页折叠、静态回退、状态过期、场景优先级、方圆形状及角标外延都有断言，Activity 重建后仍能解码。后台未停与加载器初始化顺序崩溃先由 avatar-ui-instrumentation.txt 复现。浅色与深色130%截图 avatar-light-gallery.png / avatar-dark-large-fallback.png 已人工检查。

最终57项JVM、46项真机回归全部通过（101.177秒），Debug与测试APK构建通过，Lint为0 errors / 28 warnings；日志为 avatar-final-build.txt / avatar-final-instrumentation.txt。真实首页、楼层和Neo公开个人页的圆角方形与蓝色资质已检查，普通作者的圆形与图片资质也已检查，材料为 avatar-real-home.png / avatar-real-topic.png / avatar-real-neo-profile.png / avatar-real-list-frame-a.png。实景截图不是多帧播放证明，动画播放结论来自上述确定性帧测试。

真实列表有一位作者头像仅显示底色。官网同一账号的 animated_avatar 为空，返回118字节的288×288静态PNG，所有像素Alpha都是0（avatar-blank-source.json与对应原图）。保留用户的透明图内容，不把它作为加载失败替换为首字母。

分发与手机安装包SHA256均为 c1b8c09ae6411f3aeb9408a5609fe2841afa5657753cabb4511d0a36eb04d5aa，记录为 avatar-apk-verification.txt。

## Consequences

同一用户在所有入口保持相同形状与动画能力，角标由展示场景决定。可见作者共享批量补齐与表情目录，避免逐头像请求；元数据在换号时销毁。

原始动图可能远大于缩略图，补充解码器限制输入与画布，并按目标尺寸采样。自定义表情和资质依赖远程资源，错误时保留头像和可读语义。未知资质图标名使用通用认证图标。用户卡片可能因访客权限或验证不可用，此时仅显示已返回的元数据。通用状态更新以重新进入页面及缓存有效期为准，不增加独立的状态推送连接。

APNG 使用确定性样本验证，未找到可稳定复用的真实站点APNG头像。API26/27尚无真机验收，API33强制执行旧版WebP分支不能替代旧系统兼容性结论。真实手机仍为访客；本人入口的状态/资质优先级使用账号夹具验证，不将浏览器账号视为手机登录。
