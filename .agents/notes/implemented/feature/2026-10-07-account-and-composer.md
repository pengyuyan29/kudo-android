# Agent Note: 官网会话、账号页面与持久草稿

Status: implemented

## Problem

社区的读写权限和登录状态需要一致，且网页验证、会话过期、网络超时不应导致假登录、假成功或草稿丢失。

## Decision

AccountViewModel 通过 /session/current.json 确认用户，LoginScreen 提供 HTTPS 官网 WebView，关闭文件和内容访问。通知和收藏按需分页，成功标记已读后更新界面。退出登录清除本机 Cookie 和账号草稿，账号代次阻止过期通知请求重新写回。

设置页持久化主题、材质和字号，并解释兼容材质。个人主页显示实际账号资料。

## Alternatives considered

- 在原生表单收集密码：交互可以统一，但需要自行处理 MFA、OAuth 和站点验证变化；采用官网流程。
- 仅凭本地 Cookie 判定登录：速度快，但不能区分服务器已撤销的会话；以 current_user 响应为准。

## Consequences

密码输入由官网承载。网络验证仍依赖站点当前规则。网页登录成功后的接口可用性需要真实账号和网络环境验收。

## Verification

单元测试验证 CSRF、失败不重发与响应解析；模拟器检查登录入口、设置持久化和未登录时的功能引导。测试不向真实社区发帖、点赞或发送私信。
