# Agent Note: 个人中心按内容撑高并统一上下留白

Status: implemented

## Problem

个人中心方形背景在头像上方留下过多空白。用户确认取消“我的”顶部1:1，只裁掉多余上方空间，头像到状态栏底边与统计区到底部均留26dp，内部尺寸、点击及吸顶行为保持现状。公开用户主页不属于本次调整范围。

## Decision

MemberHeader与MemberProfileScreen以自然测量高度决定视口。状态栏安全区外上下统一26dp，保留身份行与统计区原有20dp加40dp的间距，以及正常窗口64dp头像。短窗口沿用原有48dp头像和12dp额外间距的选择边界，尺寸选择根据窗口空间而非新的内容高度，避免缩短头图意外缩小内容。

测量后的展开高度决定折叠范围，为下方列表保留原来的120dp空间；完整内容持续参与测量，吸顶时通过透明度和语义隐藏，避免折叠后高度变成零而循环跳动。背景继续按完整高度绘制并在视口内平移，吸顶仍为同一张图片底部及额外0.08遮罩。异步统计、字体与窗口变化触发重新测量，折叠值始终限制在有效范围内。

## Related decisions

检索member-square、个人中心顶部、内容撑高、26dp和identityGap。[方形个人中心顶部](../../implemented/feature/2026-10-10-member-square-header.md)部分重叠，本篇只替代1:1展开高度及其多余上方空白，点击、图片、角标及折叠顺序继续有效，两篇保留并互链。[个人中心仪表盘](../../implemented/feature/2026-10-10-member-account-dashboard.md)部分重叠，数据来源和菜单不变。[公开主页连续滚动](../../implemented/bug-fix/2026-10-10-profile-header-scroll.md)与[公开主页方形顶部](../../implemented/feature/2026-10-10-square-profile-header.md)属于其他页面，本次不修改；没有完全吸收或过时提案。

## Alternatives considered

- 固定一个更小高度改动最少，也不会产生首次测量反馈；但字体、状态栏和统计加载提示都能改变内容高度，固定值无法保证上下留白一致。
- 继续按比例缩小头图能保持图片比例且易于预测；但用户明确取消1:1，比例仍会在宽屏留下无关空白，并可能触发原先依赖高度的头像及间距缩小。

## Testing

- 修改前，contentHeaderBalancesInsetsCollapsesFromListAndKeepsIdentityAndProfileArrow在真机复现上方空白385px，预期78px（26dp）；日志artifacts/member-content-header-repro.txt。
- MemberHeaderUiTest、MemberHeaderStatusUiTest和MemberAccountUiTest原有及迁移用例共10项通过，覆盖26dp上下留白、64dp头像和60dp内部间距、列表收起与恢复、点击分流、完整角标、原图底部和轻微遮罩、白底及状态栏恢复、异步统计和短窗口菜单；日志artifacts/member-content-header-ui.txt。
- fontAndWindowChangesRemeasureContentAndKeepCollapsedIdentity新增1项通过，动态切130%字体和280dp窄窗口后测量正确，吸顶时重新测量保持紧凑身份，展开仍为26dp留白、64dp头像、窄窗口32dp内部间距，列表至少保留120dp；直接拖动顶部也能收起。日志artifacts/member-content-header-resize-ui.txt。
- 实际账号展开与吸顶截图artifacts/member-content-live-expanded.png、member-content-live-compact.png已查看。展开头像top=185px，状态栏底边107px，统计文字bottom=695px，头图底边773px，上下各78px；图片底部、完整角标、昵称后等级与箭头均保留。覆盖安装保留账号及底部搜索偏好。
- Debug与AndroidTest构建通过；未修改公开用户主页、设置或通知功能，不重复无关全量测试。

## Consequences

自然高度消除与内容无关的上方空白，并随字体、统计提示重新测量。内容测量产生一次状态反馈，隐藏内容持续测量、折叠值受限，吸顶期间重测仍保持收起。极矮窗口仍受为列表保留120dp的上限约束；当前真机验收为Android13的RMX2202，其他设备仍需独立验收。公开主页的1:1不变。
