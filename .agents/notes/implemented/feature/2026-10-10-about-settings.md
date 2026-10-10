# Agent Note: 关于入口归拢与双品牌页头

Status: implemented

## Problem

界面设置下重复出现关于菜单和说明弹窗，与个人中心独立关于页分散；站内链接标题在窄屏换行过多。

## Decision

设置保留显示偏好、内容偏好、链接和退出登录，链接行简化为“站内链接”。原关于整组四行入口迁移至独立关于页正文下方，版本行打开版本信息，许可与隐私沿用现有模态，指南沿用网页回调。顶部两个现有品牌矢量图标与各自名称并排，中间红心，版本在下方居中。正文合并为“酷DO是独立 LINUX DO 第三方安卓原生客户端”。保留底部社区 slogan。

## Existing note audit

检索关于、设置、未登录和模态。[访客账号布局](../../implemented/feature/2026-10-10-guest-account-layout.md)部分重叠，独立入口、版本和 slogan 保留；[设置模态](../../implemented/feature/2026-10-08-glass-settings-modals.md)继续提供焦点隔离、可滚动说明与退出确认。没有完全吸收或过时提案。

## Alternatives considered

- 设置页保留重复入口方便访问，但用户明确要求关于内容集中到关于页，两个入口会继续分散说明与许可。
- 将说明都铺在关于页最直接，但长许可和隐私会淹没品牌及简介，使用扁平入口与已有模态。

## Testing

AboutPageUiTest 覆盖设置移除整组、短链接标题、双品牌位置、四项入口及许可链接、暗色280dp宽130%字体与各材质模态。SettingsModalUiTest 的说明弹窗用例迁移到关于页，显示设置和退出确认仍验证原入口；GuestProfileUiTest 更新独立关于正文断言。结果随本轮集中测试补齐。

## Consequences

设置里的关于入口移除后需要从“我的”进入，用户仍可返回原账号页。品牌名称按官方 LINUX DO 拼写统一。
