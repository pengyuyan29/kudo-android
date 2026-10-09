# Agent Note: 图片长按菜单与原图导出

Status: implemented

## Problem

图片查看器的长按只展示附件信息及浏览器入口。用户要求参考酷安的居中圆角文本菜单，提供系统分享、保存当前原图及多图时保存全部原图。

## Decision

菜单使用自定义 Dialog 内容，居中、小圆角、纯文本行，不使用 Material AlertDialog 外观；只显示分享图片、保存图片和按浏览组数量决定的保存全部图片。长按时固定当前图片与相册快照，防止异步补齐或翻页改变操作对象。菜单取消只关闭弹窗，翻页和来源返回沿用相册协议。

ImageDownloader 使用现有受限图片 HTTP 客户端下载 originalUrl，流式写入临时文件，按文件头判断格式，保留 GIF/APNG/WebP 原始字节；单图最多64 MiB，拒绝把验证 HTML 当图片保存。分享通过仅开放 image_exports 缓存目录的 ImageFileProvider 提供临时只读 content URI，并使用 ACTION_SEND 系统选择器。GalleryImageStore 通过 MediaStore 写入 Pictures/酷DO，Android 10+ 使用 IS_PENDING，失败删除未完成条目；API 26–28 按需请求 WRITE_EXTERNAL_STORAGE，不请求读图库权限。ImageSaver 批量逐张执行，失败继续并显示数量，取消删除当前未完成文件；已经保存的图片保留。重复点击不并发执行。

下载与落盘在工作线程执行，分享启动与 Toast 明确在 Dispatchers.Main.immediate 执行。查看器关闭取消导出协程；分享成功启动后保留文件供接收方读取，后续下载清理24小时前的本功能缓存。LocalImageExportServices 只提供下载/落盘/分享依赖注入，测试复用生产当前页、批量选择及调度逻辑，不向其他应用发送测试图片。

## Existing note audit

[全屏手势](../../implemented/feature/2026-10-09-image-viewer-motion.md)部分重叠，其长按信息弹窗被本篇菜单取代，缩放、返回与过渡继续成立。[相册分组](../../implemented/feature/2026-10-09-image-gallery-and-transitions.md)部分重叠，仍以当前楼层或列表主帖的浏览组为边界。[原生架构](../../implemented/architecture/2026-10-07-native-community-client.md)部分重叠，复用按域发送 Cookie 的图片客户端；不改变 Coil 解码。无完全吸收或过时提案。

## Alternatives considered

- 分享图片 URL 无需下载即可打开系统面板，但接收应用得到链接而非图片，不符合分享图片的要求。
- 把预览 Bitmap 压缩成 JPEG 可复用已解码内容，但会丢失原图清晰度、透明度和动画，直接导出原始字节。
- 使用既有底部 Action Sheet 能复用拖动逻辑，但用户给定的是居中圆角菜单，以本次明确参考为准。

## Verification

ImageExportTest 覆盖动图原始字节与格式修正、HTML/超限拒绝、下载和写入部分失败继续、取消清理。ImageExportUiTest 验证单图隐藏批量、深色130%字号、可读只读分享 URI、私有缓存不可分享、MediaStore 字节一致与 pending 发布、失败清理；测试条目按返回 URI 或唯一文件名删除，不读取用户照片。

ImageGalleryUiTest 从第3张实际长按，确认保存和分享都下载第3张原图，保存全部按整个浏览组顺序执行，取消菜单保留当前页。首轮30项真机回归出现1项后台 Toast 错误，日志 artifacts/image-actions-first-device.txt；显式主线程约束修复后全部30项通过（52.97秒），见 artifacts/image-actions-final-device.txt。JVM 共135项通过，Lint 0错误、30条原有警告和1条提示，完整构建见 artifacts/image-actions-fixed-build.txt。

已查看浅色实际相册菜单截图 artifacts/gallery-image-action-menu.png 和深色130%字号截图 artifacts/image-menu-dark-large.png，文字及触控区域正常；深色截图中的按压涟漪是点击瞬间状态。Android 13 真机从真实首页帖子打开图片，长按单图只显示两项，选择分享后系统选择器展示正确图片预览；证据为 artifacts/image-actions-live-menu.png、artifacts/image-actions-live-share.png 及同名布局 JSON。只验证选择器及文件读取，不选择接收方、不发送图片；相册落盘通过 MediaStore 测试验证并删除测试创建的条目，不额外保存真实帖子图片。

## Consequences

原图导出保留清晰度与动画，不受查看器解码采样影响。下载失败、存储空间不足或旧系统拒绝权限反馈失败；单图超过64 MiB不能导出，未识别格式不能保存。当前真机覆盖 Android 13，API 26–28 的授权分支尚无同版本设备验收。动图字节保留不保证每个系统图库都能播放 APNG 等格式。
