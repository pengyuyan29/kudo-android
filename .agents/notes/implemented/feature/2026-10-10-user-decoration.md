# Agent Note: 用户签名与个人背景图

Status: implemented

## Problem

帖子忽略网站独立签名，个人中心也没有显示用户已设置的资料标题和卡片背景。签名可以包含动图，且用户要求应用默认不显示。

## Decision

Post 读取 user_signature（图片 URL 或 cooked HTML），Member 读取 profile_background_upload_url 和 card_background_upload_url。签名使用本机 show_signatures 偏好，默认 false，设置界面立即更新；关闭时不创建图片请求。主帖和完整回复正文下显示签名，楼中楼短预览不放签名。原生解析文字、链接和图片，不执行网页脚本；图片等比限制高度，复用 GIF/WebP/APNG 解码及前台生命周期。

“我的”卡片使用 card_background_upload_url，公开个人主页标题使用 profile_background_upload_url。图片居中裁剪、文字加遮罩，失败和空值回退原背景。会话接口不保证背景字段，个人中心按账号补齐公开资料，较早账号的响应不得覆盖新账号。

## Related decisions

检索签名、背景图、个人中心、动图、界面设置。[账号与草稿](../../implemented/feature/2026-10-07-account-and-composer.md)、[头像](../../implemented/feature/2026-10-08-avatar-presentation.md)、[设置弹窗](../../implemented/feature/2026-10-08-glass-settings-modals.md)和[原生架构](../../implemented/architecture/2026-10-07-native-community-client.md)部分重叠，分别保留会话、头像、偏好与解码边界，没有被完全吸收或过时的提案。

## Alternatives considered

- 用 bio_cooked 当签名：不增加字段，但个人简介与帖子签名是网站不同内容，不能混用。
- WebView 渲染签名：最接近网页 HTML，但为每个回复引入网页实例、脚本与生命周期；使用已有原生文章解析和受限图片渲染。
- 两张背景混用：总能展示一张图，但网站独立配置两种用途，分别绑定个人卡片和资料标题。

## Testing

集中验证覆盖图片 URL、HTML、缺失和错误类型；默认关闭、持久化及立即切换；GIF/APNG/WebP 实际动画与后台暂停；两类背景及失败回退；账号切换不串资料。功能开发期间尚未运行本批新增测试，不以已有解码器测试代替新入口验收。

## Consequences

原生签名支持内容子集，不复刻网页主题 CSS。远程图片失败时回退；资料补齐失败不能影响登录和现有个人信息。
