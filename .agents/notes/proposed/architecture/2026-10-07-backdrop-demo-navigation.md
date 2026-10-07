# Agent Note: 采用 Backdrop 官方示例底部导航

Status: proposed

## Problem

用户安装 Backdrop demo 后确认其材质和动作就是目标，要求直接复用、仅微调参数。现有底栏自行组织采样和中心缩放，虽满足前轮几何回归，观感仍反复偏离参考。Backdrop 发布包只有绘制基础 API，LiquidBottomTabs 是 catalog 示例源码。

## Proposal

接入 AndroidLiquidGlass 的 Android 1.0.6 catalog 源码，来源提交 `896a94a3ade1cc1a940b92365f942a34971fecda`，与现有 Backdrop 1.0.6 依赖保持一致。先接入阻尼动画、手势检查和触点高光，再用官方可见底栏、隐藏放大采样层、组合镜片替换 GlassNavigation；最后集中真机验证与更新交付记录。源码保留来源、Apache-2.0 许可和修改说明。

采用官方位置 / 速度 / 按压弹簧、整栏抬升、1→1.2 隐藏标签放大和速度拉伸。参数调整限于品牌黄、五入口、56dp 底栏与48dp静止镜片、64dp运动镜片、22dp图标和10sp文字。完整液态模式保留官方 blur / lens；兼容模式遵守现有 API 33折射、31模糊、26半透明边界。

页面选择与滑动预选分离：松手才切页；取消或在发布入口松手返回原页；中央发布仅通过点击触发。隐藏采样层不能接收点击或暴露重复可访问节点。手势放到固定外容器以覆盖所有入口，移动越过系统触摸阈值后消费事件，阻止拖动误触发布。

## Existing note audit

- [玻璃与原生阅读](../../implemented/feature/2026-10-07-liquid-glass-ui.md)：部分重叠，本篇接管导航渲染决策；该篇保留主题、阅读、网络和历史取舍，双方互链，不整篇归档。
- [酷安密度与过渡](../../implemented/feature/2026-10-07-coolapk-density-and-motion.md)：部分重叠，保留文字与页面过渡；导航动画和图层的后续事实由本篇维护。
- [客户端验证](../../implemented/testing/2026-10-07-client-verification.md)：部分重叠，继续保留真实渲染、只用真机、夹具不写社区的验证契约；本篇记录源码来源和接入验证。
- Android 构建与原生社区架构：无关，不改变工具链、网络或整体导航结构。活跃 proposed / rejected 无同主题提案。

## Integration status

阻尼动画、手势检查和触点高光已接入 `ui/components/liquid/`，完整许可随 APK 放入 assets。手势辅助保留官方阻尼参数，增加独立取消回调与越过触摸阈值后的事件消费；现有底栏尚未切换。`:app:compileDebugKotlin` 已通过。

## Alternatives considered

- 继续微调手写采样中心缩放：文件少且前轮行为与几何已通过，但用户确认官方 demo 已符合目标；不断推测私有效果会增加不必要分歧，改用示例实际图层和动作。
- 直接使用最新 KMP 2.0.1 示例：可以对齐上游最新代码，但其 Kotlin 2.4.10、Compose 1.12.0 和平台 Shader API 超出当前工具链；Android 1.0.6 示例已有相同 LiquidBottomTabs 结构，可避免无关升级。

## Acceptance criteria

官方阻尼、高光辅助代码编译通过；替换底栏后，真机检查与已安装 demo 的慢拖帧对照。行为测试保留双高亮、点击、松手连续、取消、发布隔离、深色大字号和实际中心放大。Lint、Debug / 测试 APK、笔记检查通过，覆盖安装并同步分发 APK。

## Risks

示例是源码而非稳定组件 API，需要维护少量适配和许可。隐藏采样增加一层绘制；帧率、Android12和更宽窄设备仍不能用单台真机结果推定。参数先以已连接 RMX2202 的观感为依据。
