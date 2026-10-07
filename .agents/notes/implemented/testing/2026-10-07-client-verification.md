# Agent Note: 客户端契约与原生界面验证

Status: implemented

## Problem

社区接口会返回验证页和限流，真实写操作又不能作为自动测试数据。需在可控环境确认异常分支，并将视觉验证与网络可用性分开。

## Decision

JUnit 与 MockWebServer 检查请求来源、CSRF、失败不重试、JSON 与 HTML 解析。FeedViewModelTest 先复现分类 429 误伤话题列表，再修复至通过。Compose 仪器测试使用明确的测试内容，验证玻璃导航、帖子动作和主题切换；测试内容不进入正式应用数据源。

按用户要求，后续测试只使用已连接真机。通过 adb 指定序列号安装测试 APK 并运行 instrumentation，保留主应用数据与网页验证 Cookie；测试截图留在 external files，运行后拉取人工检查。测试前停止 Android CLI 的布局 instrumentation，避免两个 UiAutomation 争用同一设备。截图等待系统对话框退出动画完成，避免将过渡帧误当成页面结果。

深色模式增加实际 TextLayoutResult 前景亮度断言，弥补仅验证主题枚举改变却未检查文字可读性的缺口。

## Alternatives considered

- 只在真实站点自动化：最贴近线上，但会受到限流和会话影响，也可能发出真实社区写操作；真实请求仅做只读验证。
- 只测静态页面：可以检查配色，却不能发现 JSON、CSRF、分页与状态竞争；同时保留网络契约测试。

## Consequences

网络错误可稳定复现，仪器测试可以生成可视化检查材料。真实账号、Cloudflare 和真机帧率仍需独立验收；不能用模拟测试通过来宣称其已验证。

## Verification

2026-10-07：18 项 JVM 测试通过，Debug 构建和 Lint 通过。RMX2202（Android 13 / API 33、WebView 153）运行 5 项 Compose 测试全部通过，截图确认原生帖子和深色大字号可读。首页、分类列表、分类话题及多楼层详情已使用真实 API 验证。分类 429、规范地址 301、对象标签、访客搜索和深色默认文字均先复现失败再修复。详细命令和限制见仓库 TASKS.md。

较早在 MI 6X（Android 9 / API 28）安装启动正常，半透明兼容材质正常，但当时网络连接失败；未以旧机完成在线验收。用户换机后，不再运行模拟器。较早 API 36 模拟器结果仅为历史验证，不替代本次真机结果。
