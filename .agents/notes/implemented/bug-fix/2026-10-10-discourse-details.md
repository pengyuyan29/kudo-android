# Agent Note: 正文 details 折叠与相册定位

Status: implemented

## Problem

话题2894260的更新日志使用 details/summary，原生解析将标题和子内容平铺，隐藏内容直接显示。

## Decision

增加独立 Details 块，保留 summary 富文本、子块和 open 属性。正文默认遵循网页折叠状态，点击标题切换，恢复页面保留状态。相册递归收集折叠内图片但不计入标题图标，所有图片序号保持稳定；相册返回隐藏图片先展开其所有引用和折叠祖先再定位。

## Existing note audit

检索 details、折叠、引用及相册。[引用分组](../../implemented/bug-fix/2026-10-10-discourse-quotes.md)部分重叠，复用不可变路径与 Saver；[用户签名](../../implemented/feature/2026-10-10-user-decoration.md)保留受限图片尺寸。没有被完全吸收或过时提案。

## Alternatives considered

- 将 details 当引用，复用全部渲染，但引用默认展开、标题语义与原帖跳转不同，无法忠实保留 open 属性。
- 用 WebView 渲染整篇能自然支持 HTML 折叠，但会放弃现有正文、相册锚点和阅读曝光，新增原生块更适合既有结构。

## Testing

DetailsParserTest 先复现实际更新日志被分为3块而非一个容器（artifacts/details-parser-repro.txt）。新增解析覆盖默认关闭、open、嵌套、缺少标题和图片序号；QuoteContentUiTest 增加展开/收起、状态恢复、嵌套引用与折叠相册返回。验证结果随本轮集中测试补齐。

## Consequences

折叠中的正文直到展开才参与实际阅读；大量嵌套结构仍增加解析工作。
