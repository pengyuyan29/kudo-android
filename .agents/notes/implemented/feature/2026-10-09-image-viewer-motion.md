# Agent Note: 全屏图片手势与窗口状态

Status: implemented

## Problem

横滑相册已经保留当前图片和来源定位，但简单切换覆盖层不能表达从缩略图放大、返回另一张图的连续关系。旧缩放围绕画布中心且拖动无边界，顶部按钮占用全屏阅读空间。

## Decision

沿用[相册会话](../../implemented/feature/2026-10-09-image-gallery-and-transitions.md)及同窗口覆盖层。双指缩放围绕触点中心，图片平移按实际 Fit 图片边界限制；未放大时横滑交给 Pager，向下拖动开始关闭预览，取消或距离不足恢复。单击、下拉或系统返回关闭；长按信息与浏览器入口由[图片导出菜单](2026-10-10-image-export-menu.md)部分替代，提供分享与保存。可访问操作保留关闭、翻页与缩放，背景动作不能穿透。纯黑全屏与单图不显示页码的布局来自同机酷安截图。

关闭先锁住当前索引与交互，再恢复进入前的系统栏可见性，等待布局后定位图片。正文定位矩形带入110dp底部预留，避免返回图片被玻璃操作栏遮住。单独的[来源缩放过渡](2026-10-09-image-origin-animation.md)管理进出几何，不改变手势归属。

GalleryTransformState 管理图片 Fit 矩形、缩放和平移；没有触点的结束事件不能写入 NaN。最大放大允许长图展开到屏幕宽度，上限20倍；普通图片至少5倍。查看器加载目标最多按视口2倍、最长4096与8M像素约束取较小值，不请求无限原始位图。动图只在当前页及前台播放，离开停止。

## Existing note audit

- [相册会话](../../implemented/feature/2026-10-09-image-gallery-and-transitions.md)：部分重叠，该篇管理分组、加载与返回协议，本篇管理手势和几何。
- [正文图片](../../implemented/bug-fix/2026-10-09-article-image-layout.md)：部分重叠，使用原图比例与元信息，不改变正文过滤。
- [原生架构](../../implemented/architecture/2026-10-07-native-community-client.md)：部分重叠，复用 Coil 与动图生命周期。
- [客户端验证](../../implemented/testing/2026-10-07-client-verification.md)：部分重叠，区分功能断言、像素截图与同机动效证据。
- 原相册提案中的手势与动画部分移入本篇，原篇保留已实现的状态边界；没有其他过时提案。

## Alternatives considered

- 直接对查看器整体做 scaleIn/scaleOut：参数少且容易与导航集成；无法从任意来源位置放大，也无法在第 3 张关闭时缩回第 3 张。
- 单独使用 Navigation shared element：能自动处理固定元素的共享动画；相册保留来源、列表换预览和拖动释放已有明确协议，采用真实边界过渡可同时覆盖 Crop / Fit 与手指位移。
- 引入完整图片浏览库：能提供成熟缩放和分块加载；来源定位仍要独立实现，本轮复用 Compose / Coil，超大长图清晰度若成为实际问题再评估分块解码。

## Verification

GalleryTransformTest 中最后一指抬起引入无效 Offset 的测试先失败，日志 gallery-gesture-repro.txt；修复后4项几何和6项相册状态通过，日志 gallery-gesture-fixed.txt。正文返回图被底栏遮住由 gallery-ui-fixture-fixed.txt 单独复现，增加预留后 ImageGalleryUiTest 的8项真机测试全部通过，17.747秒，日志 gallery-gestures-instrumentation.txt。覆盖正文/评论/列表当前图返回、真实宽图比例、中心缩放和拖动、下拉取消/关闭、长按信息及状态恢复后系统返回。

首次设备截图失败来自 Android CLI 的 UiAutomation 会话未释放，停止 com.android.cli.interact.instrumentation 后重跑；测试活动显式启用与产品相同的 edge-to-edge。短拖夹具最初停住600ms再一次移动，实际触发长按；改为持续移动及真实取消，未放宽产品判定。截图为 gallery-wide-inline.png、gallery-third-fullscreen.png、gallery-return-third.png、gallery-list-third.png；同机酷安参考为 gallery-coolapk-inline.png / gallery-coolapk-fullscreen.png。

原图重试不生效由 gallery-transition-instrumentation.txt 复现：结构相等的Coil请求被复用。用重试次数重建AsyncImage后通过；离开页面清理Painter回调，避免会话保留全部已看大图。最终93项JVM、41项完整真机回归及额外1项补齐后直接退场专项通过；关闭动画5项通过，三项系统缩放恢复为1。真实正文和列表第1张进入、第3张下拉返回，以及长图、纯黑全屏和长按信息均已截图或录屏查看；完整证据见[来源过渡](2026-10-09-image-origin-animation.md)。

## Consequences

图片占满可用画布，附件元信息不占阅读画面；缩放不再把画布空白当成可拖动图片。长图和放大采样增加解码内存，约束目标尺寸不会提供无限原像素细节。系统栏隐藏与恢复依赖宿主窗口；手势与真正动画需共同回归。
