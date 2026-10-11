# Agent Note: 返回话题列表保持阅读现场

Status: proposed

## Problem

读帖timings确认后，当前未读/新/已读筛选执行整页refresh，出现下拉刷新动画并替换分页内容。分类和标签页每次ON_RESUME也执行refresh。首页Pager恢复时首次发射页码会把已保存的折叠页头展开，看起来像重新加载。

## Proposal

服务端确认阅读后仍使其他阅读相关筛选缓存失效，但当前列表保留条目、next、滚动请求和加载状态，只设置hasUpdates提示用户刷新；首页新话题计数独立静默读取。用户主动刷新、登录/发布事件继续加载。分类和标签恢复只同步账号，不自动重载。首页Pager首次发射只同步当前筛选，只有实际切页才展开页头。

拆成每单元最多3文件：先状态层复现测试和本笔记；状态层修复/测试/笔记；导航生命周期与复现界面测试/笔记；页头恢复与界面测试/笔记。集中验收后转implemented。

## Related decisions

检索refreshReadListsIfRequested、返回列表、Pager、恢复刷新。[阅读同步](../../implemented/bug-fix/2026-10-10-topic-reading-sync.md)部分取代当前阅读列表立即重载的策略，保留timings确认和账号隔离；[实时更新](../../implemented/feature/2026-10-08-home-live-refresh.md)部分取代分类恢复时自动刷新，保留首页提示和合并检查；[分页缓存](../../implemented/feature/2026-10-10-home-pager.md)与[顶部折叠](../../implemented/feature/2026-10-08-home-tabs-and-collapse.md)部分重叠，保留手动切页/刷新行为。无完全吸收或过时提案。

## Alternatives considered

- 仅去掉loading动画：能隐藏刷新圈，但仍替换整个列表并丢失后续页，因此保留当前快照直到用户主动刷新。
- 完全停止阅读同步后的失效：改动最少且位置稳定，但其他已缓存的个人筛选会长期过时，因此仍失效非当前阅读列表并更新计数。
- 返回后静默替换第一页：数据立即最新，但会移除已读项并影响当前锚点，尤其破坏分页；采用刷新提示。

## Acceptance criteria

先复现旧逻辑的额外列表请求、loading和页头展开；修复后确认阅读不重载当前页、不改next和回顶请求，同一epoch只消费一次。真实导航返回分类/标签不请求列表，首页返回保留折叠位置；手动刷新、切页及账号边界仍正常。

## Risks

未读条目会保留到用户主动刷新，这是保持阅读现场的有意取舍；hasUpdates提示不等于新增内容。计数请求失败时显示未知，不影响当前内容。模拟服务器/离线夹具验证，不向社区提交测试阅读。

## Verification progress

FeedPageCacheTest先在原逻辑复现“阅读确认不能触发返回后的刷新动画”失败（artifacts/feed-return-state-repro.txt），再将当前阅读筛选改为hasUpdates提示，保留其他阅读列表失效和独立计数请求。

FeedPageCacheTest、FeedSessionTest、FeedUpdatesTest关联回归通过（artifacts/feed-return-state-fixed.txt）。追加分页用例通过（artifacts/feed-return-pagination-fixed.txt），验证已加载两页保留next与回顶请求，用户点击刷新后才替换第一页并清除提示。

真实NavHost加离线OkHttp拦截的分类/标签返回用例，以及保存列表恢复的折叠页头断言，先在旧实现复现3项失败（artifacts/feed-return-ui-repro.txt）。恢复回调改为幂等同步账号，不再刷新，页头在下一个小单元修复。
