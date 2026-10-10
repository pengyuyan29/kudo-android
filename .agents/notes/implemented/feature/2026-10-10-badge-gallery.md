# Agent Note: 徽章宫格与获得条件详情

Status: implemented

## Problem

“我的徽章”是仅已获得徽章的长文字列表，缺少图标与未获得状态，点击跳转网页，难以浏览和查看获得条件。

## Decision

专用徽章页面合并/badges.json目录与/user-badges/{username}.json?grouped=true的个人授予记录。获得状态仅以指定用户的授予记录为准，不使用目录中当前会话的has_badge。目录保留long_description，补入目录外的已获得徽章；读取失败不把未知状态显示成未获得。

使用惰性宫格，约100dp最小单元，普通手机三列，窄屏大字减少列数、宽屏增加。宫格只显示图标和名字，已获得先列出，其他按服务端分组顺序排列。图片徽章保留原图，未获得转灰；Font Awesome图标按金银铜着色，未获得为中性灰。已知站点图标随应用打包，未知图标回退通用徽章。

名称预留两行，超出两行显示省略，弹窗标题保留全名。打包34个站点使用的Font Awesome字形，vote-up-filled使用同义thumbs-up；图片徽章加载失败回退对应字形。授权说明随资源与关于页同步。

点击打开现有贴边、可拖动关闭的CommunitySheet，显示徽章、获得状态、授予时间/次数、服务端详细说明与获得条件，并保留查看官网入口。说明缺失时明确显示暂无说明，不推测门槛。普通账号列表与待审核、聊天、邀请行为不变。

## Existing note audit

检索徽章、账号菜单和弹窗。[账号与草稿](../../implemented/feature/2026-10-07-account-and-composer.md)的“仅列已获得”呈现被部分取代，个人授予关联与账号隔离继续成立；[个人中心](../../implemented/feature/2026-10-10-member-account-dashboard.md)的入口保持；[酷安式表单](../../implemented/feature/2026-10-10-coolapk-forms.md)继续约束弹窗交互。无完全吸收或过时提案。

## Alternatives considered

- 直接使用目录has_badge只需一次请求，但它反映当前会话，缺少指定用户的授予时间与次数，不能代替个人记录。
- 网页整页展示能复用官网所有功能，但无法满足用户要求的紧凑宫格与原生详情弹窗。
- 每项仅用通用奖章最简单，但丢失网站各徽章Logo；使用已有字体图标来源与官方徽章图片。

## Testing

核对官网82项徽章目录、图片字段、图标名称和long_description，并读取公开用户授予记录确认grouped的count、granted_at与badge_id结构。真实社区只读，不申请或修改徽章。

14项相关JVM测试通过，其中BadgeDataTest的4项覆盖未获得、个人授予关联、目录外已获得、重复授予、缺失字段及只读GET请求（artifacts/badges-signature-checks.txt）；AccountExtrasTest的3项继续覆盖待审核与邀请。Debug与测试APK构建成功，Lint为0错误、30项既有警告和1项提示。

RMX2202 / Android13的BadgeGalleryUiTest共4项全过（artifacts/badge-gallery-final-device.txt），验证360dp三列、700dp六列、280dp暗色130%字号两列、图片灰化像素、全部已知图标、获得次数/日期、条件内链接、官网入口及拖动关闭。已查看badges-grid-light.png、badges-grid-wide.png、badges-grid-dark-narrow.png和badge-dark-narrow-details.png。首次链接检查错误地点击整行Text语义；改为点击链接字形的实际位置后通过，产品链接回调未改变。

最终日期标签使用“获得时间”，避免将grouped接口时间推断为最近一次获得；重新构建并覆盖安装后，详情文案、条件链接和拖动关闭的定向真机用例再次通过（artifacts/badge-gallery-time-label-device.txt，1项）。

## Consequences

宫格能同时浏览已获得与未获得徽章，详情打开不再请求网络。首次加载需要目录与个人授予两个只读请求。服务端不公开的获得条件无法补出；未来新增图标可能暂时显示通用奖章，官网仍可查看原始详情。
