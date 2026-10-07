# Agent Note: 采用 Backdrop 官方示例玻璃组件

Status: implemented

## Problem

用户安装 Backdrop demo 后确认其材质和动作就是目标，要求直接复用、仅微调参数。现有底栏自行组织采样和中心缩放，虽满足前轮几何回归，观感仍反复偏离参考。Backdrop 发布包只有绘制基础 API，LiquidBottomTabs 是 catalog 示例源码。

详情页原有两块操作胶囊使用独立 `.glass()`，表面色62%、8dp blur和18 / 28dp lens，与官方 Surface Liquid Button 的轻表面和按压动作不同。用户提供酷安与酷DO操作栏截图，要求同样替换为官方按钮示例。

## Decision

`ui/components/liquid/` 接入 AndroidLiquidGlass 的 Android 1.0.6 catalog 六份源码，来源提交 `896a94a3ade1cc1a940b92365f942a34971fecda`，与现有 Backdrop 1.0.6 依赖保持一致。GlassNavigation 只提供酷DO入口内容；LiquidBottomTabs 采用官方可见底栏、隐藏放大采样层、组合镜片，DampedDragAnimation 与 InteractiveHighlight 提供官方阻尼、速度拉伸和触点高光。源码保留来源、Apache-2.0 许可和修改说明；完整许可随 APK 放入 assets。

位置、速度、按压进度和横纵缩放沿用官方弹簧，分别为 (1,1000)、(0.5,300)、(1,1000)、(0.6,250)、(0.7,250)。整栏运动比例为 `1 + 16dp / 宽度`，隐藏标签从1放大到1.2；只在隐藏采样层放大内容，不使用手写 onDrawBackdrop 中心缩放。

适配参数为品牌黄、五入口、56dp 底栏、48dp 静止镜片、64dp 运动镜片、22dp 图标和10sp文字。镜片宽度比标签格多8dp并居中，RMX2202 上约70.4×48dp静止、93.9×64dp稳定运动，速度拉伸另叠加。静止中性灰为6%，避免官方10%灰将黄色图标明显压暗；运动灰为3%黑并保留官方高光、内外阴影与色散。可见表面为40%主题表面色、8dp blur和24 / 24dp lens，隐藏表面的lens随按压进度变化，镜片沿用10 / 14dp lens。

隐藏采样标签使用品牌黄，中央发布保留黄底黑色加号；不对整层使用官方的 accent ColorFilter，以免发布图标和黄底变为同色。增加官方相同版本的 `shapes:1.2.0` 编译依赖使用 Capsule，原依赖只作为 Backdrop 的 runtime 依赖可用。兼容模式遵守现有 API 33折射、31模糊、26半透明边界。

## Interaction boundary

页面选择与滑动预选分离：松手才切页；取消或在发布入口松手返回原页；中央发布仅通过点击触发。隐藏采样层不能接收点击或暴露重复可访问节点。手势放到固定外容器以覆盖所有入口，移动越过系统触摸阈值后消费事件，阻止拖动误触发布。

手势辅助增加独立取消回调。高光检查器位于手势链内层，在 Main pass 中先读移动，随后阻尼检查器消费移动，防止高光因事件已消费而提前取消。位置与速度仍由官方动画驱动，保留最多4dp的弹性面板偏移。

## Surface Liquid Button

详情底栏左侧“写回复”与右侧“赞 / 收藏 / 分享”操作组采用同一版本 catalog 的 LiquidButton。官方示例的 Surface 样式即 LiquidButton 加30%表面色，并不需要另一个 Surface 组件。沿用2dp blur、12 / 24dp lens、Capsule、触点高光、按压时 `1 + 4dp / 高度` 放大与 tanh 阻尼平移，不叠加旧 glass 的边框或62%遮罩；浅深色使用各自主题的 surface。

组件适配 enabled 和 nullable onClick：左侧作为独立按钮，话题关闭时不能回复；右侧只是玻璃容器，三个子按钮分别可点击，空白处不能产生第四个点击入口。缺失主帖或请求忙碌时保持点赞/收藏禁用，分享仍可用。停用或更改交互模式时重建高光状态，避免在按下过程中禁用后留下放大。最小48dp高度保留大字号增高能力；左右胶囊保留现有宽度比例、边距和22dp图标。兼容模式继续遵守现有API边界。

## Existing note audit

- [玻璃与原生阅读](../feature/2026-10-07-liquid-glass-ui.md)：部分重叠，本篇接管导航渲染决策；该篇保留主题、阅读、网络和历史取舍，双方互链，不整篇归档。
- [酷安密度与过渡](../feature/2026-10-07-coolapk-density-and-motion.md)：部分重叠，保留文字与页面过渡；导航动画和图层的后续事实由本篇维护。
- [客户端验证](../testing/2026-10-07-client-verification.md)：部分重叠，继续保留真实渲染、只用真机、夹具不写社区的验证契约；本篇记录源码来源和接入验证。
- Android 构建与原生社区架构：无关，不改变工具链、网络或整体导航结构。检索时活跃 proposed / rejected 无其他同主题提案。

## Alternatives considered

- 继续微调手写采样中心缩放：文件少且前轮行为与几何已通过，但用户确认官方 demo 已符合目标；不断推测私有效果会增加不必要分歧，改用示例实际图层和动作。
- 直接使用最新 KMP 2.0.1 示例：可以对齐上游最新代码，但其 Kotlin 2.4.10、Compose 1.12.0 和平台 Shader API 超出当前工具链；Android 1.0.6 示例已有相同 LiquidBottomTabs 结构，可避免无关升级。
- 继续给详情操作栏调旧 glass 参数：可沿用现有 TextButton / IconButton，改动少，但用户已指定官方 Surface 样式，旧材质还有独立边框、浓表面和缺少真实触点形变的问题；直接接入 LiquidButton，并保留原有业务回调。

## Consequences

复用实际图层和动画减少效果猜测；酷DO保留当前/预选双高亮和发布隔离，因此交互不完全等同四标签示例。系统时长缩放仍由 Compose Animatable 支持。

示例是源码而非稳定组件 API，需要维护少量适配和许可。隐藏采样增加一层绘制；帧率、Android12和更宽窄设备仍不能用单台真机结果推定。参数以已连接 RMX2202 的观感为依据。

`THIRD_PARTY_NOTICES.md` 记录源码版本、五份接入文件、修改范围与完整许可路径；应用“开源致谢”明确底栏来自 Backdrop 官方示例，并列出 Shapes。接口与酷安仅作为研究参考，不包含它们的实现。

## Verification

Surface Liquid Button 的编译、Debug / 测试 APK、Lint 通过，0 errors / 27 warnings。新增5项 SurfaceLiquidUiTest 与原有13项合计18项真机回归通过，28.73秒，输出为 `artifacts/surface-final-instrumentation.txt`。检查真实触摸的四个独立动作、容器无多余点击入口、关闭/忙碌/缺主帖禁用、按压内容放大、取消复位、按下期间禁用再启用，以及深色130%下三种材质。

初轮5项中4项通过，字号检查因 `TextLayoutResult.didOverflowWidth` 比较段落可用宽222px与实际文本宽168px而报错，截图文案完整。断言改读每个字符的实际边界，检查一行、未省略及全部字形在文本尺寸内；不修改已正确显示的布局来绕过测试。测试截图等待400ms让系统涟漪退出，冻结Compose时钟的按压截图仍保留按下状态。初轮与排查输出为 `surface-button-instrumentation.txt` / `surface-overflow-repro.txt`。

调整截图等待后，5项按钮测试再次独立通过，10.258秒，输出为 `surface-screenshot-instrumentation.txt`；浅色静止按钮没有残留涟漪圆，深色130%文字和三个动作完整，按压/静止的条纹夹具截图显示真实边缘折射与表面形变。

辅助代码的 Kotlin 编译通过。替换后初次12项回归有3项失败：10%静止灰令黄色图标像素变暗；两项几何断言仍读取未缩放的布局边界。颜色改为6%灰后通过；几何改用 boundsInRoot，镜片高度与可见整栏比较，中心位置允许官方弹性偏移，不再断言手写动画的绝对触点坐标。

新增实际触摸回归，覆盖真实点按分类/发布、从发布入口开始拖动、从未选中入口拖至发布，以及隐藏采样的唯一可访问入口。RMX2202 / Android13 上完整13项 Compose 测试通过，23.749秒，输出为 `artifacts/backdrop-final-instrumentation.txt`。Debug / AndroidTest APK 与 Lint 通过，0 errors / 27 warnings；新增warning为shapes版本升级建议。静止灰、黄图标、中心放大、凸出/复位、双高亮、松手连续、深色130%与发布隔离均有回归。

2026-10-08：通过系统无障碍界面关闭动画，确认三项全局缩放为0后，13项测试全部通过，17.544秒，输出为 `artifacts/backdrop-reduced-motion.txt`。结束恢复三项缩放为1。实际安装的 demo 与酷DO各录12秒双向慢拖，`backdrop-demo-kudo-comparison-00.png` / `-01.png` 已人工检查：镜片中心放大、弧面折射、运动透明和收回行为一致，酷DO保留双高亮与发布按钮颜色；慢拖两端轮廓可见。白底动静图 `backdrop-kudo-scale-comparison.png` 和深色130%测试截图已检查，真实首页为 `backdrop-kudo-home.png`。这些材料验证动作和材质，不证明稳定帧率。
