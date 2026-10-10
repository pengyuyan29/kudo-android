# Agent Note: 回帖字数与帖子Boost

Status: proposed

## Problem

普通回帖已遵循[网站字数规则](../../implemented/bug-fix/2026-10-10-reply-length.md)；详情尚无Boost短回复及其展示。用户要求底部右侧只保留点赞和Boost。

## Proposal

接入Boost的帖子字段、独立写接口、输入与胶囊展示。Boost依据实际网页插件的16字符、最多5个表情规则计数，仅明确发送时POST /discourse-boosts/posts/{postId}/boosts；依据can_boost显示权限，失败保留输入且不自动重试。Boost挂在原帖子，不进入主题楼层流或阅读计时。

主帖和回复都显示已有Boost，保留头像与服务端cooked的短内容；发送成功更新同一帖子在主列表、楼中楼和置顶原帖的副本。写操作复用Cookie、CSRF和逐帖互斥，不由UI乐观声称已发送。

## Existing note audit

已检索回帖、编辑器、楼中楼和阅读笔记。[账号与草稿](../../implemented/feature/2026-10-07-account-and-composer.md)的账号/楼层草稿隔离与确认后清理契约继续成立；[酷安式表单](../../implemented/feature/2026-10-10-coolapk-forms.md)约束输入与可拖动面板；[回应](../../implemented/feature/2026-10-09-topic-author-and-responses.md)的短按/长按行为保留；[阅读同步](../../implemented/bug-fix/2026-10-10-topic-reading-sync.md)对遮挡暂停与真实正文曝光继续成立。无完全吸收或过时提案。

## Alternatives considered

- 普通回帖固定16字实现最少，但网站可调整配置，使用服务端设置并在失败时明确提示。
- Boost复用/posts发布链路可以复用草稿和刷新，但网页使用独立协议及权限，不能生成普通楼层。
- 请求后立即乐观追加胶囊可以更快反馈，但网络结果不明会误报成功，采用确认后更新，发送期间展示忙碌状态。

## Acceptance criteria

先复现短回复被接受，再验证不足、临界值、配置错误、草稿保留和发送互斥。以离线服务器验证Boost路径、字段、权限、Unicode/表情边界与失败状态；真机检查普通/Boost编辑器、胶囊、楼中楼、亮暗和大字布局。真实账号不发布测试内容。

## Risks

网页插件的短文本规则或字段可能变化，需要按真实返回确认。网络结果未知时保留输入但不自动重试；用户可在社区确认。
