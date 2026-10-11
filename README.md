# 酷DO

[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-yellow.svg)](LICENSE)

Linux.do 社区的 Android 原生第三方客户端。Kotlin + Jetpack Compose，包名 `com.kudo.android`，最低 Android 8.0。以 Linux.do Logo 的 `#FFB003` 为主色，参考酷安的悬浮胶囊导航和帖子操作栏，使用 Backdrop 实时采样内容，呈现模糊、折射和高光。

## 功能

- 首页筛选、分类、搜索、刷新和分页。
- 首页有新数据时，底栏图标及文字切换为“刷新”，点击回顶并更新，保留收到通知时的阅读位置。
- 原生帖子阅读、后续楼层、引用、代码块、图片缩放及分享。
- 官网 WebView 登录与验证，共享 Cookie；通知、收藏和个人资料。
- 点赞、收藏、发帖、回复、图片上传、按账号和话题保存草稿。
- 浅色 / 深色 / 跟随系统，字号和材质设置。

Android 13+ 使用完整液态玻璃；Android 12 使用背景模糊；Android 8–11 使用半透明。底部导航直接改编自 Backdrop 官方 `LiquidBottomTabs` 示例，保留阻尼、速度拉伸、触点高光，以及可见底栏 / 隐藏放大标签 / 前景镜片三层结构。底栏为56dp高、22dp图标和10sp标签；高亮为 `#FFB003`，静止选中面为12%透明中性灰，绘制在图标下方以保持品牌黄。镜片静止48dp高、宽度等于标签格，首尾外侧和上下留白均为4dp；运动时增宽8dp、约66.9dp高，折射距离和内阴影随镜片高度同比适配，中心文字和图标放大，上下形成内凹折射带。玻璃底栏采用20%主题表面色和4dp模糊，半透明兼容材质仍为94%；中央发布按钮采用横向胶囊。关闭系统动画时停用速度附加拉伸，保留基础按压放大与折射。设置中可以主动切换兼容材质。

同机对照酷安调整字号和间距：列表摘要为 14sp / 22sp，长文正文为 16sp / 32sp；首页标签为 19 / 16sp，详情操作栏最小 48dp。底栏胶囊运动时透明，当前标签与经过标签同时高亮；拖动时页面保持不变，松手后切换并从当前位置吸附，只保留目标标签高亮。帖子进入 / 返回使用 280 / 220ms 淡变与轻微缩放；动画遵循系统时长设置。

首页有新数据时，底栏使用圆底镂空的顺时针回转箭头与“刷新”文案；图形复用 Material 的 ReplayCircleFilled 并水平翻转，前景采样层同步显示，点击仍执行原有回顶刷新。

首页移除社区横幅和快捷入口；顶部 Tab 普通字号高40dp，上滑收起头像搜索行并吸顶在状态栏下，反向滑动重新展开。未登录显示“最新、热门、排行榜、类别”；登录后显示“未读、最新、新、热门、排行榜、我的帖子、已读、书签、类别”，默认未读，支持横向滑动。“新”显示服务端跟踪结果计算的数量，未知时省略数字；退出或换号清空旧列表、计数和分页。

首页接入 Linux.do 的 MessageBus 长轮询，收到消息后合并检查当前筛选的第一页，确认新话题或内容更新才提示“刷新”。消息不插入当前列表；点击底栏刷新后回顶，成功清除提示，失败保留。后台和详情页暂停订阅，返回首页检查遗漏并保留阅读位置；顶部“新”的数量仍在主动刷新时更新。检查按15秒合并，实际提示时间还取决于网络和站点数据可见性。

分类页直接展示列表，移除“发现社区”、顶部刷新按钮及“找到你的同好”。卡片读取官网的 icon 配置，使用与官网一致的23种 Font Awesome 矢量图标和分类颜色；缺失或未收录的图标使用默认图标。卡片介绍、跳转及加载失败时的重试/验证入口保留，图标资源随安装包提供。

详情页“写回复”和“赞 / 收藏 / 分享”两块胶囊直接采用官方 `Surface Liquid Button` 样式：30%主题表面色、2dp模糊、12 / 24dp边缘折射、触点高光及按压放大；四个操作保持独立，关闭、请求忙碌与缺失主帖时的权限不变。

设置页选项使用底部 ActionSheet，当前项以黄色勾选标记；关于、隐私、致谢与退出确认采用官方 Dialog 材质。模态支持遮罩与返回键关闭、背景可访问隔离、键盘焦点、小窗口滚动和系统安全区，替代默认 Material AlertDialog。

## 构建与安装

需要 JDK 17+、Android SDK Platform 36，首次构建需要访问 Google Maven、Maven Central 和 Gradle。设置 `ANDROID_HOME` 或在未入库的 `local.properties` 中填写 `sdk.dir`。仓库包含 Gradle Wrapper，不需要全局安装 Gradle。

```powershell
.\gradlew.bat :app:assembleDebug
adb devices -l
adb -s <真机序列号> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <真机序列号> shell am start -n com.kudo.android/.MainActivity
```

输出：`app/build/outputs/apk/debug/app-debug.apk`。没有私有签名配置时，源码克隆使用 Android 默认 Debug 证书。

### 本地签名

首次初始化固定签名（需要 Python 3.10+ 和 JDK 的 `keytool`）：

```powershell
python scripts/generate_signing.py
.\gradlew.bat :app:assembleDebug :app:assembleRelease
```

生成器创建独立的 RSA 4096 / PKCS12 Debug、Release 证书，有效期 10000 天；已有 `signing/` 或 `signing.properties` 时拒绝覆盖。Gradle 自动读取根目录的私有 `signing.properties`，分别输出 `app/build/outputs/apk/debug/app-debug.apk` 和 `app/build/outputs/apk/release/app-release.apk`。Release 开启 R8，缺少完整签名时构建失败。

请把 **`signing/` 与 `signing.properties` 一起私密备份**。其中 `kudo-debug.p12` / `kudo-release.p12` 是密钥库，`.pem` 是公开证书，`certificates.json` 记录 SHA256 指纹。密码只保存在本机配置中；这些文件均被 Git 忽略。正式签名丢失后无法继续覆盖更新相同签名的已安装应用。

已有密钥可参考 [signing.properties.example](signing.properties.example) 填写；路径相对仓库根目录，也可使用绝对路径。`"-PsigningPropertiesFile=<路径>"` 可切换配置文件，PowerShell 中请对整个参数加引号。环境变量优先于配置文件，Debug / Release 分别支持以下同名后缀：

| 配置项 | Debug 环境变量 | Release 环境变量 |
| --- | --- | --- |
| 密钥库路径 | `DEBUG_KEYSTORE_PATH` | `RELEASE_KEYSTORE_PATH` |
| 密钥库密码 | `DEBUG_STORE_PASSWORD` | `RELEASE_STORE_PASSWORD` |
| 别名 | `DEBUG_KEY_ALIAS` | `RELEASE_KEY_ALIAS` |
| 私钥密码 | `DEBUG_KEY_PASSWORD` | `RELEASE_KEY_PASSWORD` |
| 格式（默认 PKCS12） | `DEBUG_KEYSTORE_TYPE` | `RELEASE_KEYSTORE_TYPE` |

Debug 与 Release 保持相同包名、使用不同签名，不能互相覆盖安装。新生成的 Debug 证书也与此前机器默认签名不同；需要保留手机数据时，请继续使用原签名构建，不要直接卸载旧应用。

### Tag 自动发布

[release.yml](.github/workflows/release.yml) 在推送 `vX.Y.Z` 时运行：脚本测试、笔记检查、JVM 测试和 Lint 通过后恢复正式签名，构建 Release，核对证书、包名、版本及不可调试标记，生成 SHA256 文件，再发布 GitHub Release。构建作业只有仓库读取权限；独立发布作业使用 `GITHUB_TOKEN` 写入 Release，无需额外的长期发布令牌。

仓库建好后，在 **Settings → Secrets and variables → Actions** 添加以下 Repository secrets；本地与 Actions 应使用同一套 Release 证书：

| Secret | 本地来源 |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | `signing/kudo-release.p12` 的 Base64 内容 |
| `RELEASE_STORE_PASSWORD` | `signing.properties` 的 `release.storePassword` |
| `RELEASE_KEY_ALIAS` | `release.keyAlias`（生成器默认 `kudo-release`） |
| `RELEASE_KEY_PASSWORD` | `release.keyPassword` |

Windows 可将密钥库 Base64 直接复制到剪贴板，再粘贴到对应 Secret，避免把私钥输出到终端或写入仓库：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path 'signing/kudo-release.p12').Path)) | Set-Clipboard
```

将包含工作流的代码推送到仓库、配置好 Secrets 后，推送新版本 Tag：

```powershell
git tag -a v0.2.0 -m '发布 0.2.0'
git push origin v0.2.0
```

当前发布基线为 `v0.1.9` / `versionCode=1010`，下一版从 `v0.2.0` / `versionCode=2001` 开始。示例产物为 `kodo_0.1.9_release_arm64-v8a.apk` 和 `kodo_0.1.9_release_arm64-v8a.apk.sha256`，正式 Release 只包含 `arm64-v8a`。自动发布从上一个已公开正式 Release 到当前 Tag 的 commit 中筛选 App 运行时变化，失败 Tag 和草稿不截断更新日志；整理为“新增、修复、调整”，去除文档、测试、工具链、CI、合并提交与重复标题，首次版本包含完整历史。新提交请使用 `feat:`、`fix:`、`perf:`、`doc:`、`test:`、`build:`、`ci:`、`chore:` 等前缀，冒号后写中文说明；只有 `feat:`、`fix:`、`perf:` 且实际修改 `app/src/main/` 的提交会进入更新日志。发布前请查看生成的日志。工作流把同一份 `release-notes.md` 写入 GitHub Release，App 的“关于酷DO → 检查更新”显示该正文。工作流先准备草稿、上传完整产物后再公开；上传失败留下的草稿可以重跑恢复，已经公开的同名 Release 不会被覆盖，应使用新版本 Tag。

已创建本地版本 Tag 后，可预览和人工整理日志：

```powershell
python scripts/release_notes.py --tag v0.1.9 --previous-tag v0.1.5 --output artifacts/release-notes.md
```

手动发布时用 `gh release create <Tag> --notes-file artifacts/release-notes.md` 写入整理后的文件；自动 Tag 流程使用脚本生成的内容。检查更新只读取公开正式 Release，不使用私有仓库凭据；仓库私有或未发布时显示暂无公开版本。下载和查看源码使用外部浏览器。

### 提交信息

提交标题使用 Conventional Commits 前缀，例如 `feat:`、`fix:`、`perf:`、`doc:`、`test:`、`build:`、`ci:` 或 `chore:`，冒号后写一句中文说明。新克隆仓库后启用本地校验钩子：

```powershell
git config core.hooksPath .githooks
```

钩子会拒绝没有受支持前缀的提交；`scripts/check_commit_history.py` 还会在 CI 中检查本次推送或 Pull Request 的提交范围。历史提交不会被自动改写，发布日志仍只提取实际修改 `app/src/main/` 的 `feat:`、`fix:` 和 `perf:` 提交。

版本名为 Tag 去掉 `v`，严格使用 `vX.Y.Z`，其中 `X`、`Y`、`Z` 各为 `0–9` 的一位数字；`versionCode = major × 1000000 + minor × 1000 + patch + 1`。例如 `v1.2.3` 对应 `1.2.3` / `1002004`，当前 `v0.1.9` 对应 `0.1.9` / `1010`，下一版 `v0.2.0` 对应 `0.2.0` / `2001`。补丁位从 `0.1.9` 进位到 `0.2.0`，大功能可提升 minor 或 major 位；前导零、`-rc`、`-beta` 等格式会失败。每次升级请使用更大的版本号。

普通本地构建保留 `0.1.0` / `1` 默认值，也可按 Tag 值构建相同版本：

```powershell
python scripts/release_version.py v0.1.9
.\gradlew.bat :app:assembleRelease "-PversionName=0.1.9" "-PversionCode=1010"
```

发布验收用例：有效 Tag 应产生版本和签名一致的 APK；非法 Tag、缺失 Secret、签名错误、APK 被修改或版本不符均应停止发布。仓库尚未连接时，可运行下方脚本测试，并在本地执行签名构建；线上 Tag 触发和 Release 上传需在仓库建立后验证。

## 验证

```powershell
npm run verify-notes
python -m unittest discover -s scripts/tests -v
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
adb -s <真机序列号> install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# 使用 Android CLI 检查过布局时，先停止它的辅助测试进程，避免 UiAutomation 冲突。
adb -s <真机序列号> shell am force-stop com.android.cli.interact.instrumentation
adb -s <真机序列号> shell am instrument -w -r -e class 'com.kudo.android.CategoriesUiTest,com.kudo.android.HomeRefreshUiTest,com.kudo.android.FeedHeaderUiTest,com.kudo.android.CommunityUiTest,com.kudo.android.SurfaceLiquidUiTest,com.kudo.android.SettingsModalUiTest,com.kudo.android.NavigationMaterialUiTest' com.kudo.android.test/androidx.test.runner.AndroidJUnitRunner
```

2026-10-08：37项 JVM 测试、37项 RMX2202 / Android13 真机界面测试通过（79.933秒），包括分类图标元数据、23种矢量与默认图标、分类跳转/重试、深色130%窄屏，以及既有首页和底栏回归。真实分类图标、页面清理及开发调优列表跳转已检查。Debug / 测试 APK、Lint 与笔记检查通过，Lint 为0 errors / 27 warnings。此前首页线上只读探针通过（21.297秒），真实访客推送与刷新已验证；登录私有频道仍需账号验收。此前材质慢拖录屏与关闭动画的28项结果保留。真机检查记录与手动验收用例见 [TASKS.md](TASKS.md)。真实社区只做只读检查，自动化帖子与互动使用夹具。

线上探针 `LiveUpdatesProbeTest` 默认跳过，手动运行需同时传 `-e liveReadOnlyProbe true -e class com.kudo.android.LiveUpdatesProbeTest`。它等待最多60秒的真实可见更新，不作为离线回归或CI门禁。

## 使用边界

- 首次访问可能需要在应用的官网页面手动完成 Cloudflare 验证；完成后返回并刷新。登录后点“完成登录”，由 `/session/current.json` 确认账号。代理、站点风控和客户端指纹仍可能影响部分请求。
- 当前站点搜索要求登录。尚未用真实账号验收搜索结果、发帖、回复、上传、点赞、收藏、通知与重启恢复；代码已接入，但接口模拟和界面测试不等于完整线上验收。
- 个人首页列表、新话题数量及登录账号的私有推送频道通过接口模拟与界面夹具验证，仍需真实账号验收。数量在主动刷新时更新；“已读”显示服务器已有阅读记录，本次未增加原生阅读进度上报。
- 复杂 Discourse 插件、数学公式、音视频和部分富文本格式请通过详情页的浏览器入口阅读。
- 网络结果不明的写请求不会自动重试；请先刷新社区确认是否成功，避免重复提交。
- Cookie 与草稿存放在应用私有目录，禁用备份与设备迁移，退出会清除本机账号数据。外部图片向其来源站点直接发起请求。
- Android 12 专用模糊分支、平板和折叠屏尚未实测；玻璃流畅度未做正式帧率基准。

## 许可证

项目主体代码由 Bang Liu 及贡献者以 [Apache License 2.0](LICENSE) 授权。第三方依赖、图标和改编示例继续遵循各自的许可证，详见 [第三方组件与参考](THIRD_PARTY_NOTICES.md)。

## 结构与参考

`data/` 负责 Discourse 协议和本地设置；`ui/` 按信息流、帖子、账号、编辑器拆分；`ui/components/` 封装玻璃与公共组件。重要取舍记录在 `.agents/notes/`，提交前运行 `npm run verify-notes`。

接口行为参考 [FluxDO](https://github.com/Lingyan000/fluxdo)（研究版本 `e1bd839`），独立实现 Kotlin 代码。底部导航、详情操作栏与Dialog材质改编自 [Backdrop 官方 catalog](https://github.com/Kyant0/AndroidLiquidGlass/tree/896a94a3ade1cc1a940b92365f942a34971fecda/catalog)，保留来源、修改说明与完整 Apache-2.0 许可；接入依据见[导航与按钮](.agents/notes/implemented/architecture/2026-10-07-backdrop-demo-navigation.md)和[设置模态](.agents/notes/implemented/feature/2026-10-08-glass-settings-modals.md)笔记。酷安仅作为布局和交互参考，不包含其 APK 资源或反编译代码。开源组件及许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
