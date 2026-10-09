# Agent Note: 用户签名与个人背景图

Status: implemented

## Problem

帖子忽略网站独立签名，个人中心也没有显示用户已设置的资料标题和卡片背景。签名可以包含动图，且用户要求应用默认不显示。

## Decision

Post 读取 user_signature（图片 URL 或 cooked HTML），Member 读取 profile_background_upload_url 和 card_background_upload_url。签名使用本机 show_signatures 偏好，默认 false，设置界面立即更新；关闭时不创建图片请求。主帖和完整回复正文下显示签名，楼中楼短预览不放签名。原生解析文字、链接和图片，不执行网页脚本；图片等比限制高度，复用 GIF/WebP/APNG 解码及前台生命周期。

已登录“我的”使用 profile_background_upload_url 作为沉浸式资料标题，覆盖状态栏并加遮罩，空值或失败使用 LogoYellow；公开个人主页标题也使用该字段。card_background_upload_url 保留为用户卡片的独立字段。会话接口不保证背景字段，个人中心按账号补齐公开资料，较早账号的响应不得覆盖新账号。头像进入个人主页，无独立“查看个人主页”行或顶部标题、设置按钮。

个人摘要使用 /u/{username}/summary.json，默认显示访问天数、已读帖子、获赞、发帖数。未知值显示破折号，辅助读取失败可以重试，不能把失败显示成零。主题统一接收状态栏前景覆盖，离开资料页恢复系统主题前景。[个人中心功能](../../proposed/feature/2026-10-10-member-account-dashboard.md)继续补齐原生目的页与消息分组。

## Related decisions

检索签名、背景图、个人中心、动图、界面设置。[账号与草稿](../../implemented/feature/2026-10-07-account-and-composer.md)、[头像](../../implemented/feature/2026-10-08-avatar-presentation.md)、[设置弹窗](../../implemented/feature/2026-10-08-glass-settings-modals.md)和[原生架构](../../implemented/architecture/2026-10-07-native-community-client.md)部分重叠，分别保留会话、头像、偏好与解码边界，没有被完全吸收或过时的提案。

## Alternatives considered

- 用 bio_cooked 当签名：不增加字段，但个人简介与帖子签名是网站不同内容，不能混用。
- WebView 渲染签名：最接近网页 HTML，但为每个回复引入网页实例、脚本与生命周期；使用已有原生文章解析和受限图片渲染。
- 两张背景混用：总能展示一张图，但网站独立配置两种用途，分别绑定个人卡片和资料标题。

## Testing

129项JVM测试通过，覆盖图片 URL、HTML、缺失和错误类型。50项真机回归全部通过（artifacts/community-features-final-device.txt），其中 SignatureSettingsUiTest 验证默认关闭、持久化及立即切换；AvatarUiTest 在签名实际图片像素上采集 GIF/APNG/WebP 的三色帧，并验证后台暂停、恢复和关闭停止，同时验证卡片与资料背景分别使用及失败回退。

首轮动画断言将绿色 #43a047 的亮度阈值误设为0.7，修正为颜色主通道差值后通过，未更改产品解码器。新增签名 HTML 截图放在暂停前，使用 Compose 内容截图避开 Android CLI 持有 UiAutomationService 的冲突；调整后独立重跑通过，日志 artifacts/signature-compose-capture-device.txt。已查看 user-signature-animated.png 和 profile-card-background.png。账号响应隔离由按账号重建的取消作用域与响应身份校验守住，未使用真实账号切换声称网络端验收。

## Consequences

原生签名支持内容子集，不复刻网页主题 CSS。远程图片失败时回退；资料补齐失败不能影响登录和现有个人信息。
