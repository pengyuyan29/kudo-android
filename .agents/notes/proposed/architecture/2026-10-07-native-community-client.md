# Agent Note: 酷DO 原生社区客户端与玻璃渲染边界

Status: proposed

## Problem

空仓库需要形成可安装的 Linux.do 原生客户端。参考酷安的视觉与交互，同时社区数据和账号能力遵循 Linux.do 的 Discourse 行为；玻璃效果不能让旧设备无法使用。

## Proposal

采用 Kotlin、Jetpack Compose、单 Activity 和按功能分包的单应用模块。ViewModel 持有界面状态，Repository 负责 Discourse 契约，OkHttp 负责请求，Coil 负责图片。应用名酷DO，包名 com.kudo.android，最低 API 26。品牌黄为参考项目 Logo 中的 #FFB003，文字采用深色或可读的深琥珀色。

玻璃用 Apache-2.0 的 Kyant Backdrop 1.0.6，封装背景采样、模糊、折射、高光和胶囊选中动画。此版本使用 Android Compose，避免为客户端引入跨平台工程。API 33+ 支持完整玻璃，API 31–32 模糊，较旧系统半透明；用户也能手动选择材质。

官网 WebView 仅用于登录和网页验证，原生请求与其共享受限于 linux.do 的 Cookie。以 /session/current.json 验证会话；写请求附带 /session/csrf 获取的令牌，不使用站点管理员 API Key。只允许可信 HTTPS 来源进入带会话的请求层。写请求不自动重试，避免重复发帖；401、403 验证、429 与一般网络失败有不同反馈。

正文按服务端 cooked HTML 解析为原生文字、引用、代码块、图片和链接；复杂嵌入提供浏览器入口。长帖按 post_stream 的 id 补齐，不能用首批返回数量冒充完整话题。

## Research

已检索仓库：无 .agents/notes 历史笔记。用户于 2026-10-07 批准完整基础范围和 Android 8.0 支持。

- FluxDO e1bd839：参考 lib/services/discourse 下的 API 契约以及会话处理；其代码 GPL-3.0，本项目独立实现，不复制 Flutter 源码。
- 酷安 16.6.4 的布局含 ComposeView，DEX 字符串含 com.kyant.backdrop 与 LiquidGlassSurface、LiquidGlassTabPanel；仅用于研究，不打包其资源或反编译实现。
- Backdrop 公开源码包含 lens / blur / layerBackdrop；正式依赖锁定 1.0.6，不跟随主分支升级。

## Alternatives considered

- Flutter 复用 FluxDO：可直接利用成熟的论坛功能，但用户明确要求安卓原生，并要求贴近 Compose 玻璃交互，因此独立实现 Kotlin 客户端。
- 自写整套 AGSL 渲染器：能控制全部折射参数，但背景采样、坐标和性能适配会扩大风险；复用开放许可的 Backdrop，再独立实现酷DO组件。
- 全站 WebView：能完整支持所有 Discourse 插件，但无法实现要求的原生信息流与玻璃控件；仅保留验证和外部内容入口。

## Acceptance criteria

TASKS.md 的基础闭环可运行；生成 Debug APK；单元测试覆盖模型、分页、请求来源与写操作错误；模拟器检查玻璃和核心页面。真实账号写操作留给用户主动操作验收。

## Risks

Cloudflare 验证可能对 HTTP 客户端指纹敏感；即使网页验证成功也不能承诺所有网络环境均可原生访问。复杂帖子插件可能需要外部网页。实时折射的帧率必须在真机上确认；模拟器只能确认渲染和交互正确。
