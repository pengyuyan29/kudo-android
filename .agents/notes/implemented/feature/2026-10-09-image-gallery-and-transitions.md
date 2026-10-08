# Agent Note: 按楼层分组的图片相册与返回目标

Status: implemented

## Problem

图片路由只携带一个 URL，无法翻看同帖多图，也不能从当前大图缩回对应图片。列表的图片点击被整张话题卡片接收；旧查看器的固定按钮和无边界缩放不符合全屏阅读需求。

## Decision

以主帖或单条评论为相册边界，保留每次图片出现的索引，重复 URL 不合并。列表点击图片时立即显示预览，并按需读取该主帖的全部图片；读取失败保留已打开图片并允许重试。列表返回时将当前图同步至同一预览位，正文返回时将该楼层内的当前图片带入可视范围。

查看器放在同一窗口的根覆盖层，保留原页面组合、滚动位置和图片定位信息；不把整组图片编码进导航 URL。覆盖层隔离背景焦点与可访问语义。关闭先固定当前索引再调用来源定位，期间的翻页和重复关闭不改变目标；来源失效不阻塞关闭。会话保存地址、索引和元信息，不保存 LayoutCoordinates、Painter 或回调；来源以 rememberSaveable 的实例 ID 在重建后重新注册。切换账号重置会话。

相册使用 Compose HorizontalPager 与现有 Coil 加载器。列表请求完成只更新仍活跃、未关闭的同一会话；匹配已显示的原图或 Discourse 上传缩略图，并保留原顺序及重复项。独立封面不在正文中时保留封面作为额外首张。[全屏手势](2026-10-09-image-viewer-motion.md)负责图片变换与沉浸窗口。

## Existing note audit

- [正文图片](../../implemented/bug-fix/2026-10-09-article-image-layout.md)：部分重叠，提供原图、比例与元信息；本篇负责相册与交互。
- [原生架构](../../implemented/architecture/2026-10-07-native-community-client.md)：部分重叠，复用单 Activity、受限网络与全局 Coil。
- [详情交互](../../implemented/feature/2026-10-09-topic-author-and-responses.md)：部分重叠，保留楼层与主帖状态；不改变回应、关注契约。
- [原生阅读](../../implemented/feature/2026-10-07-liquid-glass-ui.md)：部分重叠，替换单张图片路由；玻璃材质无关。
- [客户端验证](../../implemented/testing/2026-10-07-client-verification.md)：部分重叠，功能回归与真机动效证据分开记录。
- 未发现同主题的 proposed / rejected 决定；没有完全吸收或需要归档的篇目。

## Alternatives considered

- 给原 image 路由增加 URL 数组：沿用导航最直接；原页面被移出活动组合后，定位尚未可见的第 3 张会更复杂，覆盖层保留源页面更适合双向动画。
- 只对首次图片应用导航 shared element：Compose 能自动管理过渡；不能独自解决切换页面后的源项定位、列表单预览位同步与拖动关闭，使用明确的相册会话和来源定位协议。
- 为图片引入新的完整浏览库：缩放与分块解码有成熟方案；这次主要边界在来源定位和全屏手势协调，先复用现有 Compose / Coil。若超大长图的清晰度或内存成为已验证问题，再考虑分块解码。

## Verification

ImageGalleryStateTest 的 6 项与正文解析 14 项通过，日志 artifacts/gallery-state-tests.txt；Kotlin 编译通过，日志 gallery-core-build.txt。覆盖第 3 次图片出现位置、重复 URL、原图/缩略图匹配、失败后重试、晚到的网络结果、关闭中的重复事件与纯数据状态保存。交互与真实定位随全屏过渡统一真机验证。

## Consequences

列表、正文与评论复用同一相册，返回目标使用当前图片而非初始索引。列表完整相册首次需要一次只读主帖请求；独立封面可能额外占一页。来源保留回调只到该组合销毁，源内容刷新或消失时只能关闭而不能定位。超大静态图片继续受 Coil 采样约束，不承诺无限原像素缩放。
