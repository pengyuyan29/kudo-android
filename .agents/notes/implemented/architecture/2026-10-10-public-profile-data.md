# Agent Note: 公开主页数据与插件分页契约

Status: implemented

## Problem

基础 Member 与个人中心四项摘要不能表达公开用户活动，且完整资料接口不保证返回卡片背景。

## Decision

PublicProfile 包装 Member 及关注数、粉丝数、活跃、注册、地点、网址、头衔与私信权限，不改变原 Member 的账号语义。公开卡片独立读取 /u/{username}/card.json，背景缺失和读取失败保持区别。ProfileSummary 解析未知统计为 null，并按侧载主题连接回复原楼层，不把回复来源主题误列为热门主题。

ProfileTab 定义九个标签。动态/话题/回复/赞为 user_actions 的 4,5 / 4 / 5 / 1 过滤，offset 按原始行数推进，不能按去重后行数推进。回应支持顶层数组及包装对象，20条一页，使用 before_reaction_user_id；Boost 使用 before_boost_id，并从嵌套帖子 url 提取楼层；已解决读取 user_solved_posts，30条 offset 分页。投票遵守网站 more_topics_url，所有 URL 都经过同源验证，用户名按路径段编码。

数据契约核对 FluxDO e1bd839 的 _users.dart、user.dart、user_action.dart，代码独立实现。检索历史：[个人中心](../feature/2026-10-10-member-account-dashboard.md)、[用户装饰](../feature/2026-10-10-user-decoration.md)部分重叠，保留其“我的”摘要和背景规则；无同主题旧数据协议或需否决提案。

## Alternatives considered

- 扩展个人中心 UserSummary 并强制所有入口加载全部列表：复用一个类型，但公开主页各插件和可选数据会耦合已有账号展示，采用独立包装。
- 用去重后的可见行数推进 offset：计算简单，但重复或无效行会令分页反复请求旧数据，使用服务端原始数量。

## Testing

UserProfileDataTest 验证未知统计、侧载关联、重复行分页、Boost楼层、回应游标、缺失集合报错、卡片和关注接口。

## Consequences

保留网站独立数据与原楼层跳转，代价是维护插件分页格式；缺少插件集合时明确报错而非显示空成功。UI 的取消与账户隔离另由公开主页状态管理负责。
