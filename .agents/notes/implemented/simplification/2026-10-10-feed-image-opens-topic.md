# Agent Note: 信息流封面统一进入帖子详情

Status: implemented

## Problem

主题列表只返回 image_url 和同一封面的 thumbnails 尺寸变体，完整主帖图片需要额外读取详情。用户取消多图和图片点击设置，要求保留单图并统一进入帖子详情。

## Decision

信息流封面在加载中或成功后，以及其宽图／长图角标，都调用主题点击回调，不打开相册、不补取主帖。保留现有裁切、比例角标、头像及卡片操作。正文和回复仍打开原有按楼层分组的相册。

列表封面按 Coil 请求显示加载、成功和失败状态。加载中维持原来的 1.9 裁切比例和 190dp 高度上限，采用淡色底与既有黑／白／黄三色指示器；解码成功后移除提示。失败停止指示器并显示“加载失败，点击重试”，此时点击封面仅重发图片请求，标题和卡片的其他区域仍可进入主题。显式重试以请求代次重新创建 AsyncImage，避免相同失败请求不再执行；不自动循环重试。图片 URL 变化时清空上一张的加载、失败、重试代次和解码比例，避免复用列表项留下错误提示或角标。此约定沿用[正文图片](../bug-fix/2026-10-09-article-image-layout.md)已有的状态反馈，不改变取源、网络客户端或缓存策略。

删除生产列表缩略图的相册来源注册及可变预览状态。相册已有延迟补齐／状态恢复契约保留，供旧版本保存的查看器会话恢复；相关竞态测试使用独立测试来源，不再把列表当入口。

## Existing note audit

- [相册会话](../../implemented/feature/2026-10-09-image-gallery-and-transitions.md)与[来源过渡](../../implemented/feature/2026-10-09-image-origin-animation.md)：部分取代列表直接预览约定；正文相册、过渡和恢复仍有效。
- [比例角标](../../implemented/feature/2026-10-09-image-aspect-badges.md)：部分重叠，列表角标继续显示，其点击与封面一致。
- [移除列表操作](../../implemented/simplification/2026-10-09-remove-list-actions.md)：部分重叠，本次收窄图片点击，不恢复三点或长按菜单。
- 用户签名和网页浏览相关记录无关；没有同主题 proposed 或 rejected 记录，也没有完全吸收的旧篇。

## Alternatives considered

- 为可见主题补齐全部图片：能显示多图与准确总数，限制并发和缓存能控制请求；用户在了解额外请求后明确取消，因此不实现。
- 保留图片点击设置：兼容直接预览习惯；用户明确要求统一进入详情，不新增偏好。
- 删除全部延迟相册代码：生产代码更少；旧保存会话仍可能包含 topicId，保留恢复能力和独立竞态回归。
- 失败后仅显示静态占位：不会增加点击行为，卡片仍可进入帖子；用户无法直接重试列表封面，采用失败时点击重试、其他状态点击进入主题。

## Testing

2026-10-12：新增 FeedImageLoadingUiTest 的慢响应、失败重试、成功封面替换与失败封面替换 4 项在旧实现上均失败，日志 `artifacts/feed-image-loading-repro-device.txt`。修复后这 4 项与列表角标位置、封面只进入主题和比例阈值 3 项关联用例在 medium_phone / Android 16 模拟器全部通过（14.839 秒，`artifacts/feed-image-loading-final-device.txt`）；断言包含等待真实第二次请求、重试不打开主题、标题仍能进入、图片区域不跳动与旧角标不残留。初轮标题触摸使用合并语义节点，实际点中整张卡片中央的失败封面；改用未合并的标题节点后通过，没有修改产品跳转行为。Debug / AndroidTest 构建及 Lint 通过（0 errors / 41 warnings / 1 hint，`artifacts/feed-image-loading-fixed-build.txt`）。浅色等待、深色 130% 字号等待和失败截图已检查，文件分别为 `feed-image-loading-light.png`、`feed-image-loading-dark.png` 和 `feed-image-error-dark.png`。当前无真机连接，这批结果不代表真机验收；后续遵照用户要求直接打包，不追加测试。

新用例先在 RMX2202 / Android13 复现封面打开查看器，日志 artifacts/feed-signature-repro-device.txt。修复后 ImageGalleryUiTest、TopicActionsUiTest、TopicPresentationUiTest 共36项真机用例通过（56.927秒，artifacts/feed-image-fixed-device.txt），覆盖封面和角标只打开一次主题、零补齐请求，以及正文、回复相册、迟到结果竞态、返回动效与状态恢复。Debug 与 AndroidTest 构建通过，日志 artifacts/feed-image-build.txt。

## Consequences

列表无需为了图片预览额外读取主帖，也没有新增设置。旧列表来源在恢复时已不存在，查看器可正常关闭但只能淡出；不为恢复已取消的入口继续保留列表来源注册。布局与签名开关不改变。
