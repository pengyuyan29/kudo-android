# Agent Note: 关于入口归拢与双品牌页头

Status: implemented

## Problem

界面设置下重复出现关于菜单和说明弹窗，与个人中心独立关于页分散；站内链接标题在窄屏换行过多。

## Decision

设置保留显示偏好、内容偏好、链接和退出登录，链接行简化为“站内链接”。关于页正文下方保留开源致谢、社区指南与隐私说明三行入口，许可与隐私沿用现有模态，指南沿用网页回调。开源致谢与隐私说明不显示副标题。顶部两个现有品牌矢量图标与各自名称并排，中间红心；页头版本号与重复的“酷DO”版本行按用户要求移除，真实版本仍在“我的”页关于入口右侧显示。正文合并为“酷DO是独立 LINUX DO 第三方安卓原生客户端”。保留底部社区 slogan。

双Logo按顶部对齐，不让中英文名称的行高影响图标位置。菜单标题与右值按可用宽度分配空间，窄屏允许说明换行，避免右侧固定最大宽度把短标题挤成逐字换行。

## Existing note audit

检索关于、设置、未登录和模态。[访客账号布局](../../implemented/feature/2026-10-10-guest-account-layout.md)部分重叠，独立入口、版本和 slogan 保留；[设置模态](../../implemented/feature/2026-10-08-glass-settings-modals.md)继续提供焦点隔离、可滚动说明与退出确认。没有完全吸收或过时提案。

## Alternatives considered

- 设置页保留重复入口方便访问，但用户明确要求关于内容集中到关于页，两个入口会继续分散说明与许可。
- 将说明都铺在关于页最直接，但长许可和隐私会淹没品牌及简介，使用扁平入口与已有模态。

## Testing

RMX2202 / Android13 的 AboutPageUiTest 共4项全部通过（artifacts/about-layout-validation-device.txt），覆盖设置移除整组、短链接标题、双品牌与红心位置、四项入口及许可链接、暗色280dp宽130%字体与各材质模态、菜单短标题完整。窄屏标题先复现为3行，再修正到单行（artifacts/about-row-details-diagnosis.txt）。已查看 about-two-brands.png、about-dark-narrow-liquid.png 与 about-dark-narrow-liquid-footer.png，正文、菜单和底部 slogan 可通过滚动访问。

窄屏测试通过 LazyColumn 的 performScrollToNode 定位跨屏条目，避免沿用离屏预取节点的旧坐标；切换材质后仍能从底部返回双品牌页头，不放宽可见性断言。SettingsModalUiTest 的7项通过（artifacts/content-about-fixed-device.txt），说明弹窗用例迁移到关于页，显示设置和退出确认仍验证原入口；GuestProfileUiTest 的4项通过（artifacts/content-about-device.txt），独立关于正文断言已更新。Debug与测试APK构建成功，Lint为0错误、30项既有警告和1项提示。

页面精简后重新构建并覆盖安装，AboutPageUiTest四项和GuestProfileUiTest的关于返回用例共5项通过（artifacts/about-simplify-device.txt），确认页头版本、版本行和两个副标题消失，许可/隐私弹窗、指南链接、底部slogan、窄屏大字以及“我的”页版本号保留。已查看artifacts/about-simplified.png，三个菜单入口与右侧箭头对齐。

## Consequences

设置里的关于入口移除后需要从“我的”进入，用户仍可返回原账号页。品牌名称按官方 LINUX DO 拼写统一。
