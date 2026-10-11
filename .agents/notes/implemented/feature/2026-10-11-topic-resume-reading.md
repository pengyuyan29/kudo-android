# Agent Note: 按服务端阅读进度续读话题

Status: implemented

## Problem

登录用户在 Web 读过话题后再次点击标题会进入续读楼层，App 却始终从首楼开始。现有 timings 已上报实际阅读时间，但详情解析丢弃 last_read_post_number，普通入口未将阅读进度用于定位。

## Decision

详情模型保留服务器 last_read_post_number，并计算 min(last_read_post_number + 1, highest_post_number) 作为 resumePostNumber；零、负数、缺失或null不产生目标，加一使用Long避免溢出。字段是最高已读楼层，不是像素滚动位置，继续使用现有实际正文曝光和 timings 同步。

首次详情统一恢复作为入口约定：普通登录用户进入读取最新进度，明确指定楼层优先，刷新不重新恢复。远楼层复用三页窗口，不逐页下载整个间隔。入口接入属于同一功能的独立小单元，本次数据提交尚未启用自动定位。

工作分为两个不超过3个文件的小单元：详情模型、解析测试、笔记；然后 ViewModel、续读测试、同篇笔记。每个小单元单独提交。用户明确账号切换不用验证，真机体验由用户手试；运行本功能及关联楼层单元测试、构建、Lint 和笔记门禁，不安装或清理手机数据，不升级0.1.7/1008。

## Related decisions

按阅读进度、阅读位置、last_read、timings 和楼层检索活跃笔记：[阅读同步](../../implemented/bug-fix/2026-10-10-topic-reading-sync.md)部分重叠，保留曝光与上报协议；[楼层导航](../../implemented/feature/2026-10-10-topic-floor-navigation.md)与[连续窗口](../../implemented/bug-fix/2026-10-11-topic-jump-window.md)部分重叠，复用明确定位和三页窗口；[前页预取](../../implemented/bug-fix/2026-10-11-topic-previous-prefetch.md)继续管理视口锚点及后续分页。其他检索命中是列表缓存、个人主页或界面呈现，与首次续读无关；没有完全吸收或过时提案。

## Source evidence

Discourse 的 [Topic.lastUnreadUrl](https://github.com/discourse/discourse/blob/main/frontend/discourse/app/models/topic.js) 按已读楼层加一并限制到最高楼层；[TopicViewSerializer](https://github.com/discourse/discourse/blob/main/app/serializers/topic_view_serializer.rb) 返回 last_read_post_number；[TopicUser](https://github.com/discourse/discourse/blob/main/app/models/topic_user.rb) 对 timings 使用最高实际已读编号更新。网页链接包含楼层后缀不等于 API 直接返回完整 href。

## Alternatives considered

- 在每个列表保留已读位置并拼带楼层的路由：最接近网页生成标题链接的方式，也能省一次首楼请求；但各列表缓存的进度可能陈旧，搜索、相关推荐和无楼层正文链接仍需各自接入，选择首次详情统一恢复，接受远楼层多一次只读请求。
- 本地记录最后可见楼层：能精确回到上次停留处，且不依赖上报时延；但与 Web 的最高已读语义不同，也不能自然跨设备同步，用户要求与 Web 对齐，先复用服务端记录。

## Testing

TopicResumeDataTest 的3项单测通过，覆盖缺失/null/零/负数、7楼到8楼、读完及越界封顶、Int.MAX_VALUE防溢出。新增能力最初因字段尚未实现而编译失败（artifacts/topic-resume-data-repro-20261011.txt），这是新契约的缺失验证，不称为旧行为复现；实现后通过（artifacts/topic-resume-data-fixed-20261011.txt）。

## Consequences

服务端进度按最高已读单调前进，向前翻阅再退出不会退回更早楼层；短暂阅读不足上报门槛或网络失败时，重进进度可能稍旧。模型读取位置不会额外写入阅读状态。普通入口的自动定位仍待下一小单元接入，不能将字段解析通过当成整项功能验收。
