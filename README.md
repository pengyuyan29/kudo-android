# 酷DO

Linux.do 社区的 Android 原生第三方客户端。Kotlin + Jetpack Compose，包名 `com.kudo.android`，最低 Android 8.0。以 Linux.do Logo 的 `#FFB003` 为主色，参考酷安的悬浮胶囊导航和帖子操作栏，使用 Backdrop 实时采样内容，呈现模糊、折射和高光。

## 功能

- 首页筛选、分类、搜索、刷新和分页。
- 原生帖子阅读、后续楼层、引用、代码块、图片缩放及分享。
- 官网 WebView 登录与验证，共享 Cookie；通知、收藏和个人资料。
- 点赞、收藏、发帖、回复、图片上传、按账号和话题保存草稿。
- 浅色 / 深色 / 跟随系统，字号和材质设置。

Android 13+ 使用完整液态玻璃；Android 12 使用背景模糊；Android 8–11 使用半透明。底部导航为 56dp 高、22dp 图标，中央发布按钮采用横向胶囊。设置中可以主动切换兼容材质。

同机对照酷安调整字号和间距：列表摘要为 14sp / 22sp，长文正文为 16sp / 32sp；首页标签为 19 / 16sp，详情操作栏最小 48dp。底栏胶囊运动时透明，当前标签与经过标签同时高亮；拖动时页面保持不变，松手后切换并从当前位置吸附，只保留目标标签高亮。帖子进入 / 返回使用 280 / 220ms 淡变与轻微缩放；动画遵循系统时长设置。

## 构建与安装

需要 JDK 17+、Android SDK Platform 36，首次构建需要访问 Google Maven、Maven Central 和 Gradle。设置 `ANDROID_HOME` 或在未入库的 `local.properties` 中填写 `sdk.dir`。仓库包含 Gradle Wrapper，不需要全局安装 Gradle。

```powershell
.\gradlew.bat :app:assembleDebug
adb devices -l
adb -s <真机序列号> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <真机序列号> shell am start -n com.kudo.android/.MainActivity
```

输出：`app/build/outputs/apk/debug/app-debug.apk`（0.1.0，约 25 MiB）。这是 Debug 签名的开发包；正式发布需配置自己的签名，不提交 keystore。

## 验证

```powershell
npm run verify-notes
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
adb -s <真机序列号> install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# 使用 Android CLI 检查过布局时，先停止它的辅助测试进程，避免 UiAutomation 冲突。
adb -s <真机序列号> shell am force-stop com.android.cli.interact.instrumentation
adb -s <真机序列号> shell am instrument -w -r -e class com.kudo.android.CommunityUiTest com.kudo.android.test/androidx.test.runner.AndroidJUnitRunner
```

2026-10-07：18 项 JVM 测试已通过；本轮 7 项 RMX2202 / Android 13 真机界面测试通过，Lint 无错误。前轮关闭系统动画时的 6 项界面测试通过，本轮未重跑关闭动画的 7 项测试。测试覆盖请求来源、CSRF、失败不重发、重定向、解析、退出竞争，以及玻璃导航点击、松手吸附、滑动双高亮、帖子动作、主题、字号和深色文字可读性；透明胶囊的同机截图与慢拖录屏已人工检查。真机检查记录与手动验收用例见 [TASKS.md](TASKS.md)。只在真实社区做只读检查，自动化中的帖子与互动是测试夹具。

## 使用边界

- 首次访问可能需要在应用的官网页面手动完成 Cloudflare 验证；完成后返回并刷新。登录后点“完成登录”，由 `/session/current.json` 确认账号。代理、站点风控和客户端指纹仍可能影响部分请求。
- 当前站点搜索要求登录。尚未用真实账号验收搜索结果、发帖、回复、上传、点赞、收藏、通知与重启恢复；代码已接入，但接口模拟和界面测试不等于完整线上验收。
- 复杂 Discourse 插件、数学公式、音视频和部分富文本格式请通过详情页的浏览器入口阅读。
- 网络结果不明的写请求不会自动重试；请先刷新社区确认是否成功，避免重复提交。
- Cookie 与草稿存放在应用私有目录，禁用备份与设备迁移，退出会清除本机账号数据。外部图片向其来源站点直接发起请求。
- Android 12 专用模糊分支、平板和折叠屏尚未实测；玻璃流畅度未做正式帧率基准。

## 结构与参考

`data/` 负责 Discourse 协议和本地设置；`ui/` 按信息流、帖子、账号、编辑器拆分；`ui/components/` 封装玻璃与公共组件。重要取舍记录在 `.agents/notes/`，提交前运行 `npm run verify-notes`。

接口行为参考 [FluxDO](https://github.com/Lingyan000/fluxdo)（研究版本 `e1bd839`），独立实现 Kotlin 代码。酷安仅作为布局和交互参考，不包含其 APK 资源或反编译代码。使用的开源组件及许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
