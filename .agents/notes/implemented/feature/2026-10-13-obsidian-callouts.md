# Agent Note: 支持 Obsidian Callout 正文

Status: implemented

## Problem

正文中的 Obsidian Callout 语法（例如 `> [!note]`）当前被当作普通引用文本显示，用户会看到原始标记和引用前缀，无法获得网页端的彩色标题、内容区域和折叠行为。

## Decision

在现有 `ArticleBlock` 解析和 Compose 渲染链路中增加 Callout 块。对 cooked HTML 中首段匹配 `[!type]` 的 blockquote 进行识别，移除语法标记，将后续内容交给现有正文解析器；支持标准类型、可选自定义标题及 `+`/`-` 折叠标记。普通引用保持原逻辑。Callout 的正文、图片、代码和链接继续复用已有递归渲染能力，用户签名中的 Callout 只渲染正文内容。

## Alternatives considered

- 在 `parseInlineArticle` 中仅隐藏 `[!note]` 文本：无法提供块级背景、标题图标和折叠内容，也会丢失类型语义。
- 只识别服务端专用 Callout HTML class：当前服务端返回的原始 Markdown 会被转成普通 blockquote，不能覆盖现有帖子。

## Consequences

- Note、Info、Warning、Todo、Tip 等类型能从 `[!type]` 标记解析为 Callout。
- 标记不再作为正文显示，正文、代码块和图片仍正常解析。
- 自定义标题及折叠状态可用，普通 blockquote 行为不变。
- 单元测试覆盖解析边界，Compose 测试覆盖显示和折叠。

Callout 识别依赖 cooked HTML 中的 blockquote 分段结构；如果服务端改为专用 HTML 结构，需要补充对应 DOM 适配，但不会影响普通引用。新增块参与图片路径和折叠状态计算，折叠状态在正文重组后仍按路径保存。

## Testing

`ContentParserTest.obsidianCalloutsBecomeTypedBlocksAndOrdinaryQuotesStayQuotes` 覆盖类型、正文、自定义标题、折叠标记和普通引用边界。`SpoilerContentUiTest.obsidianCalloutRendersTitleAndCanBeExpanded` 在 RMX2202 / Android 13 真机上通过，验证标题、隐藏正文和展开/收起交互。
