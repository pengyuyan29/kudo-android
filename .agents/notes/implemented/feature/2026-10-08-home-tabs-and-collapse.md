# Agent Note: 首页紧凑标签与登录态信息流

Status: implemented

## Problem

首页 Tab 的文字、指示器和默认内边距叠加，使顶部过高；搜索行固定在列表外，滚动后继续占据阅读空间。社区横幅和快捷入口重复首页筛选。所有账号共用同一组 Tab，也没有按官网区分访客与个人信息流。

## Decision

首页删除社区横幅和四个快捷入口，信息流直接从话题开始。紧凑 Tab 与搜索行分离，前者固定、后者使用 Material3 enterAlwaysScrollBehavior 协调嵌套滚动。布局阶段读取高度偏移，不额外复制列表滚动状态。

首页搜索行最小44dp，头像28dp，搜索内层30dp，左右12dp、上下2dp留白；搜索行随上滑收起，Tab 留在状态栏下，反向滑动即展开搜索。首页采用 enter-always 嵌套滚动，状态栏安全区始终保留，Tab 不参与纵向折叠。大字号允许 Tab 增高。分类详情保留返回标题，不折叠。

标签横向滚动，选中项通过 BringIntoViewRequester 保持可见；标签文字与指示器共用 34dp 最小布局，不叠加 Material Tab 默认文本内边距。选中标签使用17sp文字，未选中15sp；指示器为文字下半部背后的 LogoYellow 横向渐变，宽度跟随文字。字号增大时布局随字形增高。

账号变化时重建横向标签滚动位置，避免退出后保留在个人标签末端。两组顺序、计数、分类动作和列表会话隔离由[登录态筛选](2026-10-08-session-feed-filters.md)维护。

## Related decisions

已检索 proposed / implemented / rejected 的首页、Feed、Tab、吸顶关键词。与[密度与节奏](../../implemented/feature/2026-10-07-coolapk-density-and-motion.md)和[玻璃界面](../../implemented/feature/2026-10-07-liquid-glass-ui.md)部分重叠，仅取代其中首页介绍区和顶部标签布局，保留其余排版、导航和材质决定。与[原生架构](../../implemented/architecture/2026-10-07-native-community-client.md)和[验证边界](../../implemented/testing/2026-10-07-client-verification.md)兼容；设置模态、底栏官方示例与本次顶部改动无关。

## Alternatives considered

- 将搜索行和 Tab 都放进 LazyColumn，用 stickyHeader 固定 Tab：结构直观、无需自定义高度，但反向轻滑不能立即显示搜索，要滚回列表顶部才出现；因此采用酷安观察到的 enter-always 折叠。
- 保持所有筛选固定显示，并在点击时提示登录：权限实现简单，但访客看到无权使用的个人入口，与用户给定顺序不同；改为按会话生成列表并在状态层校验。
- 用当前加载的新话题条数充当数量：无需额外请求，但分页长度不等于全量新话题数；未知计数不展示数字。

## Testing

2026-10-09 紧凑顶部调整复用 FeedHeaderUiTest 的折叠、反向展开、账号切换与窄屏大字号回归，最终同批功能完成后集中执行。历史验证记录如下。

FeedHeaderUiTest 在 RMX2202 / Android13 先复现两个失败：上滑后 Tab 位置不变、横幅仍存在。修复后 2 项通过（4.1秒），验证连续上滑吸顶稳定、反向滑动搜索展开、首屏直接显示话题。日志为 artifacts/home-tabs-repro.txt 与 home-tabs-layout-test.txt。最终24项JVM及32项真机界面回归通过（68.994秒），构建与Lint通过（0错误/27警告）。真实首页、排行榜吸顶与反向展开截图分别为 home-tabs-real-top.png、home-tabs-real-pinned.png、home-tabs-real-expanded.png，已人工检查；吸顶Tab位于状态栏107px下方，40dp高，与同机酷安一致。

## Consequences

滚动后的阅读区增加一个搜索行的高度，移除重复入口使首页直接进入内容。顶部折叠需要与下拉刷新及分类页安全区共同检查。34dp 为普通字号视觉目标，放大字体优先保证文字完整。普通字号 Tab 的布局触控高度随视觉高度为34dp，低于48dp常规建议；使用宽松的水平留白和完整语义入口，并按用户确认的酷安比例实现。
