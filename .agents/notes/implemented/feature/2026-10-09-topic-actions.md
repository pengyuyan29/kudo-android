# Agent Note: 主题第一排操作与网站举报契约

Status: implemented

## Problem

列表更多图标无操作，长按没有菜单；详情更多菜单不包含复制、收藏、历史与举报。列表没有主帖版本与权限，不能推断历史入口。举报原因和说明字数受网站配置影响。

## Decision

本次数据单元提供主帖权限、举报配置与写入、编辑历史与差异文本契约。界面和屏蔽单元在此基础上分别接入。跨页面菜单由同一 Host 管理，列表按需请求主帖，详情收藏交给既有 TopicViewModel，避免和底部操作状态分叉。列表支持收藏、按需历史、屏蔽、举报；详情用复制替代屏蔽。第一排使用横向圆形图标按钮和底部取消，窄屏允许横向滚动。

历史需要 version > 1 且 can_view_edit_history，读取 /posts/{id}/revisions/latest.json，翻页跟随 previous_revision / next_revision，不能假定版本连续。原生差异文本保留新增、删除标记及作者、时间、原因。

举报读取 /site.json 中启用且适用于 Post 的 is_flag 原因，与主帖 actions_summary.can_act 求交集。说明下限读取 /latest 的 siteSettings.min_personal_message_post_length，上限遵循 Discourse MAX_MESSAGE_LENGTH=1000。支持 notify_user 私信和 illegal 确认；POST /post_actions 只提交 id、post_action_type_id、必要的 message，不发送工作人员强制处理字段。写请求不自动重试；结果未知时阻止再次提交。

本机隐藏主题使用独立偏好集合，首页、分类和搜索统一过滤，SnackBar 可撤销，设置可恢复全部。不调用网站静音接口、不隐藏作者其他主题、不阻断显式深链。

## Related decisions

检索活跃笔记中的菜单、举报、历史、收藏与首页关键词。[数据契约](../../implemented/architecture/2026-10-07-discourse-data-contract.md)保留网络与会话边界，[主题呈现](../../implemented/feature/2026-10-09-topic-presentation.md)保留分类和标签布局，[玻璃弹窗](../../implemented/feature/2026-10-08-glass-settings-modals.md)提供材质与焦点隔离；均为部分重叠，没有完全替代的旧篇。

## Alternatives considered

- 网站主题静音能多端同步，但会改变社区通知偏好；本次采用用户确认的本机隐藏。
- 每条列表预取主帖可让菜单立即呈现，但滚动产生大量请求；仅打开菜单时查询。
- 举报和历史打开网页可自动覆盖网站细节，但用户需要原生操作，因此按权限和配置实现原生界面。

## Testing

两种列表长按与更多打开同一菜单，置顶主题也有入口。详情复制主帖、收藏同步底栏，无屏蔽。未编辑或无权限的主帖没有历史按钮。举报过滤禁用和无权限原因，校验说明及非法确认，连点只有一次写入。真机检查明暗、大字、取消、背景隔离与顶部折叠。写入测试仅使用 MockWebServer。

## Consequences

权限可能在弹窗打开后变化，提交以服务器结果为准；网络中断可能使结果未知，保留说明并阻止重复写入。主帖读取失败提供重试，不猜测历史或收藏状态。
