# Agent Note: 分类页使用官网图标并移除顶部说明

Status: implemented

## Problem

分类页用统一的井号占位，丢失官网为各分类设置的视觉标识。“发现社区”、独立刷新按钮和“找到你的同好”占用顶部空间，与已选择的底栏分类入口重复。用户确认删除这三处，保留卡片介绍及跳转。

## Decision

分类 icon 元数据和矢量目录独立于页面排版，便于随官网字段和图标库更新。

沿用 /site.json 的分类请求，读取 icon 字段，不根据分类名字或 id 猜图标。官网目前用23种 Font Awesome Free 图标；使用已核对的 SVG path 和 viewBox 构建原生 ImageVector，按需缓存，沿用分类颜色与现有45dp图标底面。资源随 APK 分发，不引入运行时网页或额外图标网络请求。空、null、非字符串及未收录图标回退现有 Tag 图标；保留 Fonticons 署名和 CC BY 4.0 许可。

删除分类页标题行及副标题，列表直接排在状态栏安全区下。卡片标题、简介、跳转和底栏避让保持原契约；加载失败时仍提供现有重试/登录入口，删除的是独立常驻刷新按钮。

## Related decisions

检索分类、category、Category：与[请求数据契约](../../implemented/architecture/2026-10-07-discourse-data-contract.md)部分重叠，补充已有分类模型字段；与[首页标签](../../implemented/feature/2026-10-08-home-tabs-and-collapse.md)及[会话筛选](../../implemented/feature/2026-10-08-session-feed-filters.md)仅共享分类入口，不改变筛选或登录规则；液态玻璃及密度笔记为背景，不修改底栏材质；[首页推送](../../implemented/feature/2026-10-08-home-live-refresh.md)中的刷新专指首页，本篇仅移除分类常驻按钮。没有完全吸收或过时提案。

## Alternatives considered

- 使用分类 uploaded_logo 图片：接口已有地址且能自动跟随网站换图，但它是分类页的大尺寸Logo，与网页分类名称前的小图标不同；本次采用 icon 字段对应的图形。
- 用现有 Material 图标近似替代：无需增加资源，但幼苗、资源分享和阅读等轮廓会与官网不同；使用官网实际矢量路径。
- 每次下载网页 SVG sprite：新图标能自动覆盖，但增加启动网络依赖和整站资源解析；23种已使用图标内置，出现新名字时明确回退并可更新目录。

## Testing

CategoryMetadataTest 的2项测试先复现原解析丢失 icon 字段，再通过：两种分类JSON结构、服务端换图标、颜色和简介保留、缺失/null/非字符串/空白字段。日志为 artifacts/category-metadata-repro.txt / category-metadata-checks.txt。浏览器实际分类数据、图形分别保存在 category-web-metadata.json / category-web-glyphs.json；23种 path 和 viewBox 逐一与 Font Awesome Free 6.7.2 官方 SVG 比对相同。资源与字段单元已完成，页面接入及真机验收是后续单元。

## Consequences

官网更换为未收录图标时显示默认图标，需要更新矢量目录；图标名称随接口实时读取，但图形目录不会远程自动更新。分类卡片下方内容较多时仍需滚动，底栏避让不可随标题删除一起减掉。
