# Agent Note: 正文图片比例与附件信息

Status: implemented

## Problem

正文将图片宽高比限制在 0.65–2.5。1688×339 的横图因此进入约两倍高的灰色容器，长图也有同类留白。Discourse lightbox 的文件名、分辨率和体积被递归解析成正文，挤占阅读空间；查看器只得到缩略图地址，丢失原图和附件信息。

## Decision

图片块分别保留缩略图、原图、有效比例及 lightbox 附件信息。正文用真实比例布局，尺寸缺失时由解码结果补齐；不设图片最小高度，不通过 Crop 隐藏留白。只剔除 lightbox 的自动元信息，保留用户图注、正文和行内表情。原图地址和元信息保留在图片模型中，正文点击传递原图地址。

## Existing note audit

- [行内表情](../../implemented/bug-fix/2026-10-09-inline-emoji-images.md)：部分重叠，继续将 emoji 与普通图片分开，不修改表情排版。
- [原生阅读](../../implemented/feature/2026-10-07-liquid-glass-ui.md)：部分重叠，本篇细化正文图片规则。
- [数据契约](../../implemented/architecture/2026-10-07-discourse-data-contract.md)：部分重叠，保持 HTTPS 与图片 Cookie 同源边界。
- [客户端验证](../../implemented/testing/2026-10-07-client-verification.md)：部分重叠，新增横图、长图和 lightbox 结构的复现。
- proposed / rejected 中没有同主题提案；没有完全吸收或需要归档的决定。

## Alternatives considered

- 保持比例限制并改用 Crop：布局高度稳定，容易移除灰边；会裁掉用户截图，正文按真实比例展示。
- 删除所有名为 meta 的节点：过滤简单；会误删普通正文中同名结构，只识别 lightbox 自动信息。
- 正文继续显示附件信息：无需新增查看入口；用户明确要求精简阅读区，信息迁到大图菜单。

## Verification

1688×339 比例及 lightbox 信息泄露两项先失败，日志 artifacts/image-layout-repro.txt；修复后 ContentParserTest 的 14 项通过，日志 image-layout-fixed.txt。覆盖横图、长图、缺失/非法尺寸、原图 HTTPS 边界、嵌套附件信息与图注/emoji 混排。加载后实际尺寸与视觉留白的真机检查随相册交互统一执行。

## Consequences

正文完整呈现横图与长图，附件信息不再占用阅读空间。长图按实际高度布局增加单个楼层的高度。尺寸缺失时首次解码可能触发布局调整。图片初次读取仍受网络与站点权限影响。
