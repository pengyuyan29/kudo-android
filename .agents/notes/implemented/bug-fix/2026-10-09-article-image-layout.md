# Agent Note: 正文图片比例与附件信息

Status: implemented

## Problem

正文将图片宽高比限制在 0.65–2.5。1688×339 的横图因此进入约两倍高的灰色容器，长图也有同类留白。Discourse lightbox 的文件名、分辨率和体积被递归解析成正文，挤占阅读空间；查看器只得到缩略图地址，丢失原图和附件信息。移除比例限制后，铺满宽度的长图又会撑高楼层并过度放大站点缩略图；《秘密花园园丁邀请函》4 楼是用户报告的实际入口。

## Decision

图片块分别保留缩略图、原图、有效比例及 lightbox 附件信息。正文与回复用真实比例布局，预览高度最多 300dp；宽度取内容可用宽度与 `300dp × 图片比例` 的较小值，图片靠左，超高时宽高一起缩小。尺寸缺失时由解码结果补齐；不设图片最小高度，不通过 Crop 隐藏留白。相册来源记录缩小后的真实图片矩形，点击仍打开完整原图。只剔除 lightbox 的自动元信息，保留用户图注、正文和行内表情。

## Existing note audit

- [行内表情](../../implemented/bug-fix/2026-10-09-inline-emoji-images.md)：部分重叠，继续将 emoji 与普通图片分开，不修改表情排版。
- [原生阅读](../../implemented/feature/2026-10-07-liquid-glass-ui.md)：部分重叠，本篇细化正文图片规则。
- [数据契约](../../implemented/architecture/2026-10-07-discourse-data-contract.md)：部分重叠，保持 HTTPS 与图片 Cookie 同源边界。
- [客户端验证](../../implemented/testing/2026-10-07-client-verification.md)：部分重叠，新增横图、长图和 lightbox 结构的复现。
- proposed / rejected 中没有同主题提案；没有完全吸收或需要归档的决定。

## Alternatives considered

- 保持比例限制并改用 Crop：布局高度稳定，容易移除灰边；会裁掉用户截图，正文按真实比例展示。
- 只限高并保留满宽 Fit 容器：改动少，能压住楼层高度；两侧空白会让图片来源矩形和后续角标偏离真正的图片，采用宽高同步缩小的容器。
- 删除所有名为 meta 的节点：过滤简单；会误删普通正文中同名结构，只识别 lightbox 自动信息。
- 正文继续显示附件信息：无需新增查看入口；用户明确要求精简阅读区，信息迁到大图菜单。

## Verification

限高复现的两项真机测试先失败：有尺寸和无尺寸的长图分别被撑高到 1640dp、1800dp，日志 artifacts/image-preview-height-repro.txt。修复后 ImageGalleryUiTest 的 16 项全部通过（31.303 秒），长图变为 60×300dp、靠左、下方内容可见，点击仍交付原图；图片翻页、缩放、返回目标及动画像素回归通过，日志 image-preview-height-fixed.txt。93 项 JVM 测试通过，日志 image-preview-height-build.txt。只读核查确认话题 847468 的第 4 楼原图为 1600×8828、src 为 90×500，证据保存在 image-preview-source.json；这次限高先修复过度放大，预览分辨率随后独立处理。

1688×339比例及lightbox信息泄露两项先失败，日志 artifacts/image-layout-repro.txt；修复后ContentParserTest的14项通过，日志 image-layout-fixed.txt。覆盖横图、长图、缺失/非法尺寸、原图HTTPS边界、嵌套附件信息与图注/emoji混排。

真机夹具 gallery-wide-inline.png 使用1688×339原比例，实际边界断言与截图均确认没有上下灰边，文件名/尺寸/体积不进入正文。真实帖子 gallery-live-inline.png 保留作者图注，gallery-live-information.png 将2770×1400、232KB等附件信息放在长按菜单；gallery-live-tall-motion.mp4 检查长图。图片比例、元信息和相册交互进入93项JVM与42项真机测试；完整分批结果见[来源过渡](../feature/2026-10-09-image-origin-animation.md)。

## Consequences

正文完整呈现横图与长图，附件信息不再占用阅读空间。预览高度不超过 300dp，超长图片会变窄，细节阅读依赖点击原图放大。尺寸缺失时首次解码可能触发布局调整。图片初次读取仍受网络与站点权限影响。
