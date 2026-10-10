# Agent Note: 回帖字数与帖子Boost

Status: implemented

## Problem

普通回帖已遵循[网站字数规则](../bug-fix/2026-10-10-reply-length.md)；详情此前没有Boost短回复及其展示。用户要求底部右侧只保留点赞和Boost。

## Decision

接入Boost的帖子字段、独立写接口、输入与胶囊展示。Boost依据实际网页插件的16字符、最多5个表情规则计数，仅明确发送时POST /discourse-boosts/posts/{postId}/boosts；依据can_boost显示权限，失败保留输入且不自动重试。Boost挂在原帖子，不进入主题楼层流或阅读计时。

Unicode序列表和有效shortcode表取自2026-10-10网站pretty-text/emoji/data，事实数据放在resources/emoji。最长序列匹配覆盖旗帜、ZWJ和肤色，其他字符仍按UTF-16；标准别名忽略大小写，自定义名称由/emojis.json补充。3418个序列均与网站emojiReplacementRegex比对为单一表情。可拖动输入面板采用CommunitySheet，发送中禁止关闭，确认后清空；主帖、回复与楼中楼复用头像加短内容胶囊，保留服务端表情图片。

主帖和回复都显示已有Boost，保留头像与服务端cooked的短内容；发送成功更新同一帖子在主列表、楼中楼和置顶原帖的副本。写操作复用Cookie、CSRF和逐帖互斥，不由UI乐观声称已发送。

帖子操作栏移除左侧灰色“正文 / 回复”标记，保留右侧回复入口和楼层目标。Boost 在各楼层及底部主帖操作栏统一复用官网 Font Awesome rocket 矢量；官网插件 @icon=rocket 的只读证据位于 artifacts/boost-web-source.js。火箭分别为17dp和19dp，与爱心实际图形高度接近；使用主题 onSurfaceVariant 的75%灰色，并继续服从禁用透明度。列表点赞数量用空心爱心表示，帖子和底部已选爱心使用 Discourse love 红色（#FA6C8D），其他品牌选中色继续使用 LogoYellow。

## Existing note audit

已检索回帖、编辑器、楼中楼和阅读笔记。[账号与草稿](../../implemented/feature/2026-10-07-account-and-composer.md)的账号/楼层草稿隔离与确认后清理契约继续成立；[酷安式表单](../../implemented/feature/2026-10-10-coolapk-forms.md)约束输入与可拖动面板；[回应](../../implemented/feature/2026-10-09-topic-author-and-responses.md)的短按/长按行为保留；[阅读同步](../../implemented/bug-fix/2026-10-10-topic-reading-sync.md)对遮挡暂停与真实正文曝光继续成立。无完全吸收或过时提案。

## Alternatives considered

- 普通回帖固定16字实现最少，但网站可调整配置，使用服务端设置并在失败时明确提示。
- Boost复用/posts发布链路可以复用草稿和刷新，但网页使用独立协议及权限，不能生成普通楼层。
- 请求后立即乐观追加胶囊可以更快反馈，但网络结果不明会误报成功，采用确认后更新，发送期间展示忙碌状态。

## Verification

PostBoostsTest验证16/17字、5/6表情、旗帜/ZWJ/肤色、UTF-16普通补充字符、标准别名、自定义shortcode、CSRF独立接口及失败不重试。TopicViewModelTest验证仅确认后更新与回调、Boost和点赞共享逐帖互斥。真机夹具检查输入边界、忙碌禁用、失败保留与成功清理、主帖和楼中楼胶囊、深色大字、拖动关闭及各玻璃材质按钮。

## Consequences

操作栏调整后，TopicBoostsUiTest 与 TopicReadingUiTest 11项真机测试通过（rocket-actions-device.txt）；长文末尾测试改为定位仍存在的Boost操作，继续验证末尾实际曝光才标读。新增主帖/底部红色像素验证与有用操作/回复楼层检查2项通过（heart-rocket-device.txt）。构建通过，post-actions-rocket.png 展示复用官网火箭后的操作行。

火箭尺寸与灰度复现测试先在旧实现因尺寸失败（boost-compact-repro-device.txt），调整后同机通过（boost-compact-device.txt）；同时检查楼层与底栏的实际高度、图形灰度，构建通过。

网页插件的短文本规则或字段可能变化，需要按真实返回确认。网络结果未知时保留输入但不自动重试；用户可在社区确认。
