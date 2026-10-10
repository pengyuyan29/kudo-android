# Agent Note: 关于页公开GitHub版本检查

Status: implemented

## Problem

关于页缺少版本检查和源码入口，用户要求未来公开仓库的Release成为版本及更新日志来源。

## Decision

通过独立无会话OkHttp客户端请求woliubang256/kudo-android的公开latest Release API，不带社区Cookie或GitHub凭据。只接受三段正式版本、比较数字分量，拒绝草稿与预发布。点击检查打开可滚动更新面板，展示版本、日期和Release正文；已有最新版本也可看日志，更新入口通过外部浏览器打开Release页面。检查期间去重，退出取消请求，错误可重试。404显示尚无公开版本而非已是最新；网络、限流及无效响应分别提示。源码入口始终外部打开仓库。

## Existing note audit

[关于页](../../implemented/feature/2026-10-10-about-settings.md)部分重叠，保留品牌与已有三项入口；[签名与发布](../../implemented/process/2026-10-08-signing-and-tag-releases.md)继续约束三段版本和APK签名。没有现有更新协议、完全吸收或过时提案。

## Alternatives considered

- 直接打开Release网页最简单，但不能在App显示新版本与日志；原生读取API并提供外部下载入口。
- 内置APK下载及安装能减少跳转，但引入下载生命周期和安装权限，当前需求使用Release页提供现有附件下载。

## Verification

离线夹具验证数字排序、同版旧版、草稿预发布、404、限流、网络失败、空日志、重试以及源码外部回调。

GitHubUpdatesTest的5项JVM测试通过，末项断网用例记录于artifacts/github-network-test.txt。RMX2202 / Android13上UpdateUiTest的4项通过，验证外部源码回调、新版日志和下载回调、错误重试与同版空日志、退出取消请求以及360dp窗口130%字号长日志可滚动。AboutPageUiTest的4项同时通过，日志artifacts/update-device-tests.txt；同批独立开关像素测试失败另行修正，不归为更新功能失败。没有向私有仓库注入凭据或创建真实Release。

实际账号About页截图artifacts/interface-about-live.png已查看。真机从“检查更新”匿名请求当前仓库，展示“暂无公开版本，仓库可能尚未公开或尚未发布正式版本”、重试和仓库入口，记录artifacts/interface-update-live.json；未误报已是最新。

## Consequences

App与GitHub Release共用一份日志，避免维护两套版本说明；代价是检查依赖GitHub网络和公开正式版本。私有仓库公开前匿名API为404；GitHub网络不可达时检查失败。当前本地Debug签名不能直接被正式签名APK覆盖。
