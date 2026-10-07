# Agent Note: 客户端契约与原生界面验证

Status: implemented

## Problem

社区接口会返回验证页和限流，真实写操作又不能作为自动测试数据。需在可控环境确认异常分支，并将视觉验证与网络可用性分开。

## Decision

当前底栏使用[Backdrop 官方示例](../architecture/2026-10-07-backdrop-demo-navigation.md)。新增真实触摸测试区分语义点击与实际指针事件，检查隐藏采样不暴露重复入口，以及从发布或未选中标签开始拖动的隔离行为。

详情操作栏的 SurfaceLiquidUiTest 使用独立夹具回调，不向社区点赞、收藏或回复。真实触摸检查写回复、赞、收藏与分享分别一次；右侧玻璃容器没有第四个点击入口。检查关闭/忙碌/缺主帖权限、按压和取消实际内容尺寸、按下过程中停用并重新启用，以及深色130%三种材质。字号判断使用每个字符的实际边界；段落的可用宽不代表字形溢出，不能仅用 didOverflowWidth 判断。

设置模态使用 SettingsModalUiTest 夹具，不退出真实账号或打开外部链接。检查选项唯一与选中语义、即时保存次数、取消/遮罩/系统返回键、背景可访问入口隐藏、面板空白不关闭、退出必须确认且退出动画中不重复、三种材质深色130%、短窗口内容滚动与固定动作，以及连续Tab焦点留在弹窗。键盘用例显式请求 InputMode.Keyboard，合成按键不会自动切换手机的输入模式。材质和同窗口采样契约见[玻璃模态笔记](../feature/2026-10-08-glass-settings-modals.md)。

JUnit 与 MockWebServer 检查请求来源、CSRF、失败不重试、JSON 与 HTML 解析。FeedViewModelTest 先复现分类 429 误伤话题列表，再修复至通过。Compose 仪器测试使用明确的测试内容，验证玻璃导航、帖子动作和主题切换；测试内容不进入正式应用数据源。

按用户要求，后续测试只使用已连接真机。通过 adb 指定序列号安装测试 APK 并运行 instrumentation，保留主应用数据与网页验证 Cookie；测试截图留在 external files，运行后拉取人工检查。测试前停止 Android CLI 的布局 instrumentation，避免两个 UiAutomation 争用同一设备。截图等待系统对话框退出动画完成，避免将过渡帧误当成页面结果。

深色模式增加实际 TextLayoutResult 前景亮度断言，弥补仅验证主题枚举改变却未检查文字可读性的缺口。

导航拖动增加冻结 Compose 时钟的几何检查，比较松手前后的实际胶囊位置，再检查发布入口与取消拖动能回原标签。只验证功能不变量，不把字号、时长等样式常量复制到测试里。系统动画关闭验证通过真机无障碍界面的“关闭动态效果”开关完成，读取三项动画缩放为 0 后运行仪器测试和只读浏览，再恢复开关。此设备限制 adb 直接写系统设置，失败后改用系统界面。

滑动预选测试读取实际 TextLayoutResult 颜色，比较当前、经过与未经过标签；同时检查 selected 在松手前不变，发布入口不触发动作，松手或取消后高亮收敛。在浅色与深色 130% 字号下运行，透明材质另用截图和真实慢拖录屏人工检查，不把颜色常量复制到断言中。交互依据见[酷安密度与过渡对照](../feature/2026-10-07-coolapk-density-and-motion.md)。

用户指出高亮仍偏棕、选中黄底和镜片过矮后，增加三个实际渲染检查：文字颜色与品牌 LogoYellow 比较，标签上半部的图标像素应包含品牌黄；纯白背景的静止选中面检查 RGB 色偏，允许不同的中性灰强度；拖动镜片比较实际上下边界与标签高度，取消后应收回。真实前景折射与裁切另用同机静止截图和慢拖录屏人工检查，完整液态效果的结果只归属于已测 Android 13 真机。材质与图层契约见[玻璃导航](../feature/2026-10-07-liquid-glass-ui.md)。

整栏抬升与中心放大增加两项回归：读取变换后的 boundsInRoot 比较拖动前后的可见尺寸，检查镜片中心仍跟随手指、镜片仅略高于放大后的标签，取消后恢复原尺寸；捕获完整 Compose 根画面，比较同一图标在静止与镜片覆盖时的实际像素宽度，并与镜片外图标比较。只捕获标签子树会漏掉前景镜片，只读取未裁切布局宽度也不能衡量图层缩放。白色夹具的两侧 20dp 边距与正式首页一致，具体光学比例仍由同机录屏校准。

## Alternatives considered

- 只在真实站点自动化：最贴近线上，但会受到限流和会话影响，也可能发出真实社区写操作；真实请求仅做只读验证。
- 只测静态页面：可以检查配色，却不能发现 JSON、CSRF、分页与状态竞争；同时保留网络契约测试。
- 断言 blur / lens 参数与固定高度：运行快且失败位置明确，但不能证明图标进入折射源、镜片未被父层裁切，也无法发现浅色主题使用错误的语义色；检查实际文字、像素和几何，再看真实慢拖材料。

## Consequences

网络错误可稳定复现，仪器测试可以生成可视化检查材料。真实账号、Cloudflare 和真机帧率仍需独立验收；不能用模拟测试通过来宣称其已验证。

## Verification

2026-10-08 设置玻璃模态：新增7项与原有18项共25项真机测试全部通过，正常动画45.988秒、系统关闭动画38.548秒，输出 `artifacts/modal-final-instrumentation.txt` / `modal-reduced-motion.txt`；结束三项系统缩放恢复1。Debug / 测试APK与Lint通过，0 errors / 27 warnings。6项初轮的3个误报均为测试时序、虚拟列表或圆角外坐标；增加有效条件后通过。焦点用例显式键盘模式后通过，没有为测试误报改产品行为。浅/深色130%、三种材质截图已人工检查。

2026-10-08 详情 Surface Liquid Button：新增5项与原有13项共18项真机测试通过，28.73秒，输出为 `artifacts/surface-final-instrumentation.txt`。Debug / 测试 APK 与 Lint通过，0 errors / 27 warnings；三种材质的深色130%、实际触摸、禁用恢复与取消均使用夹具验证。初轮字号断言失败记录为 `surface-button-instrumentation.txt` / `surface-overflow-repro.txt`；实际文案未裁切，改用字符边界后通过。

系统关闭动画后的完整18项通过，25.076秒，输出为 `surface-reduced-motion.txt`；结束确认三项缩放恢复1。截图等待调整后按钮5项再通过，10.258秒，输出为 `surface-screenshot-instrumentation.txt`。官方 demo Buttons 页与夹具动静、浅/深色130%详情及最终安装后的真实滚动截图均已人工检查，具体材料见 TASKS.md。

2026-10-08 官方示例接入：RMX2202 / Android13 的13项 Compose 测试全部通过，正常动画23.749秒，系统关闭动画17.544秒。三项全局动画缩放通过系统界面关闭并恢复为1。Debug / 测试 APK、Lint通过，0 errors / 27 warnings。当前几何测试读取 boundsInRoot，包括镜片在内的真实缩放边界；镜片高度与整栏表面比较，并允许官方弹性面板的少量横向偏移。中心放大仍读取完整根画面像素。输出为 `artifacts/backdrop-final-instrumentation.txt` / `backdrop-reduced-motion.txt`；同机 demo / 酷DO慢拖及浅深色截图已检查，来源与参数见上述笔记。

最终分发 APK 为 `artifacts/Kudo-0.1.0-debug.apk`，覆盖安装成功；构建、分发和设备安装包的 SHA256 相同，完整上游 Apache-2.0 文本进入 `assets/licenses/`。本轮为导航、详情操作栏与开源致谢变化，18项协议 JVM 测试沿用此前结果。详细命令、材料与最终哈希见 TASKS.md。

### 此前自绘底栏的回归记录

2026-10-07：此前 18 项 JVM 测试已通过，本轮 Debug 构建和 Lint 通过（0 errors / 26 warnings）。RMX2202（Android 13 / API 33、WebView 153）本轮正常动画下运行 12 项 Compose 测试全部通过，21.31 秒；前轮系统动画关闭时的 6 项通过，本轮未重跑关闭动画的 12 项测试。截图确认原生帖子和深色 130% 字号可读，摘要 / 正文基线间距分别约 22 / 32dp。首页、分类列表、分类话题及多楼层详情已使用真实 API 验证。分类 429、规范地址 301、对象标签、访客搜索、深色默认文字、胶囊松手跳回、首个拖动事件重复位移、滑动预选未高亮、品牌色、选中面色偏、镜片高度、整栏不放大与错误的整体反向采样均先复现失败再修复。详细命令和限制见仓库 TASKS.md。

本轮失败复现为 artifacts/navigation-scale-repro.txt / navigation-center-repro.txt，完整回归为 navigation-scale-instrumentation.txt。navigation-scale-before-after.png、navigation-raised-lens.png / navigation-material-dark-large.png 截图与 navigation-glass-coolapk-motion.mp4 / navigation-glass-kudo-motion.mp4 同机慢拖确认整栏放大、镜片中心图标和文字继续放大、前景弧面边缘折射、双高亮和松手收敛；navigation-glass-home.png 确认静止灰面和品牌黄且没有额外镜片白圈，结束留在真实首页。此前材质复现与 10 项回归保留在 navigation-material-repro.txt / navigation-material-instrumentation.txt，双高亮复现与 7 项回归保留在 navigation-preview-repro.txt / navigation-preview-instrumentation.txt。录屏仅验证动作与材质，不作为帧率基准。

较早在 MI 6X（Android 9 / API 28）安装启动正常，半透明兼容材质正常，但当时网络连接失败；未以旧机完成在线验收。用户换机后，不再运行模拟器。较早 API 36 模拟器结果仅为历史验证，不替代本次真机结果。
