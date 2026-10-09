# Agent Note: Linux.do 深层链接路由

Status: implemented

## Problem

系统入口仅接收 /t/ 链接，用户主页及其他站内地址无法进入应用。同一个 URL 再次唤起不会触发 Compose effect，恢复 Activity 时又可能重复导航。扩大入口后显式浏览器动作可能被应用自身接回。

## Decision

同源 HTTP(S) 链接解析为帖子、用户、首页三种目标；解析与 Android 导航分离。帖子保留 /t/id 兼容，并支持 /t/slug/id/number；用户路径忽略用户名后的子路径。每次新 Intent 作为独立事件消费，重建后不重放已消费地址。其他站内地址清回首页并选中首页页签，非站内地址不参与路由。显式浏览器动作使用浏览器包名避免自身回环。

## Existing note audit

[原生客户端](../../implemented/architecture/2026-10-07-native-community-client.md)、[数据契约](../../implemented/architecture/2026-10-07-discourse-data-contract.md)和[详情作者](../../implemented/feature/2026-10-09-topic-author-and-responses.md)部分重叠，分别保留导航结构、网络同源和深链主帖补齐的职责。本篇不替代上述决策。检索未找到独立深链决策或过时提案。

## Alternatives considered

- 将所有 URL 直接注册为 Navigation deepLink：可少写入口代码，但用户子路径及任意站内地址的首页回退仍需统一解析，且需要独立处理重复 Intent，故使用显式目标解析。
- 按 URL 文本去重：能阻止恢复时重复入栈，但无法区别恢复与用户再次打开同一地址，改为事件消费和恢复标记。

## Verification

LinuxDoLinkTest 的4项测试通过；运行时编译通过。解析用例覆盖 slug、楼层、用户子路径、无效 ID、查询参数及非本站来源；新增 DeepLinkUiTest，集中真机验证冷/热启动、同址再开、旋转重建、首页回退及浏览器退出应用。提交前 verify-notes 通过。

## Consequences

Android 12 及以上对未验证的域名需要用户在系统“默认打开”中启用；应用不能自行发布 linux.do 的 assetlinks.json。深链目标内容仍受服务器权限和网络验证约束。
