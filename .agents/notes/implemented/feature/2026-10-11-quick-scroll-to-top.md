# Agent Note: 全局液态玻璃快速回顶

Status: implemented

## Problem

长列表和话题详情缺少便捷回到顶部的入口。用户要求参考酷安，在底部居中显示小圆形液态玻璃上箭头，滚动后出现，设置默认打开；同时精简回复计数，不足最低字数时显示当前/最低字数，达到后仅显示当前字数。

## Decision

显示设置持久化showScrollToTop，缺失旧配置默认true。公共QuickScrollToTopHost采样真实页面背景，复用现有LiquidButton和材质档位。各主列表通过Modifier注册自己的滚动状态、可见视口、启用条件和回顶动作，不替换原生LazyColumn/Grid，不改变原有滚动语义、布局和分页。只选择当前已恢复生命周期且可见面积最大的主视口，隐藏的Pager页不响应；目标离开组合即注销。

按钮位于当前视口底部中央，给系统导航、首页导航和话题操作栏留出距离，键盘显示时隐藏。页面在顶部时隐藏；列表或折叠页头有偏移时淡入，点击平滑回顶并展开页头。操作期间防重复，离开页面取消旧操作。菜单/面板覆盖时禁用底层目标。话题远楼层复用跳转窗口读取1楼，不补齐中间楼层；读取成功后定位标题，失败保留原内容并允许重试。

showScrollToTop在设置中默认打开，开关即时生效；回复底部只保留计数，不足服务端最低字数时显示“当前 / 最低 字”，达标后显示“当前 字”，校验与上传/发布状态不变。

## Related decisions

检索回顶、scrollToTop、液态、折叠、分页。[官方玻璃组件](../../implemented/architecture/2026-10-07-backdrop-demo-navigation.md)部分重叠，复用按钮及兼容边界；[主页连续滚动](../../implemented/bug-fix/2026-10-10-profile-header-scroll.md)和[首页顶部](../../implemented/feature/2026-10-08-home-tabs-and-collapse.md)保留原手势消费顺序；[楼层窗口](../../implemented/bug-fix/2026-10-11-topic-jump-window.md)保留有限窗口策略；[回复长度](../../implemented/bug-fix/2026-10-10-reply-length.md)只精简展示，校验与服务端阈值不变。均部分重叠，无完全取代或过时提案。

## Alternatives considered

- 每页复制按钮和玻璃采样：接入直观且可局部控制；但材质、避让和生命周期逻辑分散，选公共Host，页面只声明实际滚动目标。
- 全面替换LazyColumn为带按钮的容器：能少写接入代码；但Modifier父级布局数据、测试语义、阅读曝光及嵌套滚动可能改变，保留原滚动组件并注册目标。
- 远楼层先逐页补齐再回顶：能形成完整连续列表；但长话题等待不可控，沿用既有按目标窗口读取策略。

## Testing

最终Debug、AndroidTest和Lint构建通过（artifacts/quick-top-final-build.txt），Lint为0错误、38既有警告、1提示。27项JVM关联回归全部通过：ReplyRulesTest 3、TopicFloorNavigationTest 12、TopicResumeReadingTest 12。

最终26项真机用例通过（artifacts/quick-top-final-ui.txt）：QuickScrollToTopUiTest 9项、PublicProfileUiTest 17项。覆盖独立测试配置默认开启/持久化、开关即时生效、分页状态、覆盖和重复点击、公开主页展开、远楼层失败保留/成功回标题、三种材质避让，以及回复0/5/6/10字计数与发布阈值。此前另外40项页面回归通过（artifacts/quick-top-regression-ui.txt），包含首页、分类、个人中心、话题向前分页、编辑器生命周期、设置面板和应用内网页，共66项不同UI用例通过。

先复现外部回复面板打开时按钮未隐藏（artifacts/quick-top-reply-cover-repro.txt），再让话题目标同时遵守readingEnabled，最终新增用例通过。两条旧主页断言限定展开header祖先并显式定位热门主题，保留可见性、手势、资料面板和匿名登录回调检查，避免昵称多节点和固定滑动距离引起误报。

真机1618704c核对Debug签名后使用install -r覆盖，未卸载或清除账号数据。自动化仅使用离线夹具，不切换真实账号或发布内容。另在真实浅色首页检查首屏隐藏、滚动出现且避让底栏、点击回顶并恢复页头（artifacts/quick-top-home-before.png、quick-top-home-scrolled.png、quick-top-home-returned.png）。

同机深色130%字号的LIQUID/BLUR/TRANSLUCENT静止截图分别为artifacts/quick-top-material-*.png，已人工检查按钮居中和底栏间距，液态边缘采样真实双色内容，模糊/半透明遵循既有兼容效果。色块夹具用于材质检查，真实首页截图用于导航避让与页头恢复检查；未将两者表述为所有页面或旧系统运行验收。

Release构建通过（artifacts/quick-top-release-build.txt），版本通过参数保持0.1.7/1008，仅arm64-v8a。正式包artifacts/release/0.1.7/kodo_0.1.7_release_arm64-v8a.apk已验证正式签名、非debuggable、包名/版本/ABI和16KB对齐；SHA256为4ce2d60a0169d288017eb5c91246e6af18e51c1f3ff492754b5b049ec07a5fbd。

## Integration boundaries

首页与分类共用FeedPage目标，只有当前已停稳且匹配筛选条件的分页启用，标签复用同一列表。个人主页和个人中心的折叠偏移与列表回顶并行动画，只有顶部收起、列表尚未移动时也可以恢复完整页头。公开主页只注册当前分页，更多/个人资料/关注/徽章面板覆盖时禁用底层目标。

话题已包含顶部时直接动画回标题；有前部缺页时调用jumpTo(1)，目标窗口成功后回标题，跳转期间禁用按钮。失败保留原窗口并显示错误，再次点击可重试；固定主帖副本不能作为1楼已在当前窗口的判断依据。外部回复面板通过readingEnabled禁用目标。

搜索、分类目录、收藏、游客页面、通知分页、个人话题/历史/私信、草稿、账号记录、忽略用户、关于、徽章网格和等级详情均注册原有滚动状态，短列表没有位移时不出现按钮；删除/分类/邀请/许可等面板显示时禁用底层目标。

聊天反向列表按视觉顶部定位最早已加载项，最新消息位置不显示入口；输入栏之外的视口决定按钮位置。应用内网页使用WebView真实scrollY与动画scrollTo，错误遮罩时禁用、释放时移除监听，不改变网页内链接分流。

## Consequences

公共Host统一材质、位置和生命周期，页面保留原始分页、阅读曝光及嵌套滚动。新增滚动页面必须注册目标并声明遮挡条件，避免入口遗漏或覆盖误触。视口筛选依赖Compose布局与导航生命周期，未来新增多窗格需复查最大面积规则。远楼层回顶仍需要网络；顶部未成功加载前保留当前正文。真实玻璃观感需设备检查，语义断言不能证明折射。
