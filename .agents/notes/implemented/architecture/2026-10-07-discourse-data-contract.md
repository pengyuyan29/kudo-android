# Agent Note: Discourse 请求与数据契约

Status: implemented

## Problem

话题列表、详情和写操作具有不同的响应和认证边界。只读取首批楼层会截断长帖，盲目重试写请求会重复发帖，错误地跟随分页地址则可能泄露会话。

## Decision

data 包分开模型解析、DiscourseClient 和 CommunityRepository。分页使用 more_topics_url，帖子使用 post_stream.stream 按 20 个 id 补齐；JSONObject 显式处理缺失或 null 字段。请求层只允许 baseUrl 同 scheme、host、port 的地址，禁用自动重定向和失败重试。

AndroidSessionCookies 与官网 WebView 共用 CookieManager。CSRF 通过互斥刷新；失败的写请求不自动重发。401、验证页、权限拒绝、429、服务端错误和网络错误分型。图片仅向 HTTPS linux.do 附加 Cookie，跨站图片不携带账号凭据。备份禁用，设置和草稿在应用私有目录；退出清除账号草稿。

## Alternatives considered

- 使用 User API Key 执行所有接口：接口层简单，但 FluxDO 记录了 linux.do 的 scope 和限流限制，不能假定 write scope 可用。
- 按页码猜测所有接口分页：实现一致，但 topic post_stream 不是普通页码，且服务端可能改变列表游标；使用服务端返回的链接和楼层 id。

## Consequences

接口错误可以在原生界面提供具体恢复入口。直接 HTTP 仍可能遇到 Cloudflare 指纹限制，网页验证成功不保证 OkHttp 在所有网络可用。复杂 JSON 插件数据不在首版模型内，正文保留网页入口。

## Verification

集中测试覆盖同源地址限制、null 字段、作者关联、点赞与收藏、写请求不可自动重放以及 401 / 403 / 429。当前公开 latest.json 的无会话探测返回 403 HTML，因此必须展示网页验证，而非假数据。
