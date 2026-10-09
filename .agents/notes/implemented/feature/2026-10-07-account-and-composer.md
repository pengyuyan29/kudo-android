# Agent Note: 官网会话、账号页面与持久草稿

Status: implemented

## Problem

社区的读写权限和登录状态需要一致，且网页验证、会话过期、网络超时不应导致假登录、假成功或草稿丢失。

## Decision

编辑器的页面呈现与回复承载由[酷安式表单](2026-10-10-coolapk-forms.md)部分替代，以下登录、上传与草稿契约继续成立。

AccountViewModel 通过 /session/current.json 确认用户，LoginScreen 提供 HTTPS 官网 WebView，关闭文件和内容访问，并使用[有界视口](../bug-fix/2026-10-10-webview-viewport.md)避免验证 iframe 被零高度限制隐藏。通知和收藏按需分页，成功标记已读后更新界面。退出登录清除本机 Cookie 和账号草稿，账号代次阻止过期通知请求重新写回。

会话验证也绑定账号代次：退出后较早的 current_user 响应不能恢复旧用户；清理期间禁止新的会话验证。AccountViewModelTest 先复现延迟响应导致退出后重新登录的问题，再验证修复。

设置页持久化主题、材质和字号，并解释兼容材质。个人主页显示实际账号资料。

已登录个人中心的“我的话题”使用 /topics/created-by/{username}.json，仅列出自己创建的话题，不混用 /posted 的参与记录；浏览历史使用 /read.json。私信独立提供收件箱、已发送、归档，分别使用 private-messages、private-messages-sent、private-messages-archive。原生列表遵循服务端 more_topics_url，复用用户选择的信息流或标题样式；账号参与 ViewModel 身份，切换信箱取消旧请求并核验请求代次。界面继续按[个人中心功能](../../proposed/feature/2026-10-10-member-account-dashboard.md)补齐。

等级徽标与菜单打开原生 Connect 页面。ConnectRepository 只请求固定 connect.linux.do 来源，从共享 CookieManager 按该 URL 读取会话，拒绝自动跳转；认证跳转交给应用内 WebView。Jsoup 读取服务器标题、周期、环形/条形进度、限制与否决条件，不硬编码阈值；低等级 empty-state 保留网站说明，不虚构评估。页面结构变化明确报错并提供网页恢复，主站 DiscourseClient 的同源限制不放宽。

未登录“我的”的页面层级由[酷安式未登录个人中心](2026-10-10-guest-account-layout.md)接管：白色登录区和两条菜单行，关于入口使用独立页面；登录后的资料和会话契约继续成立。

EditorViewModel 按用户 id、话题和回复楼层隔离持久草稿。上传限制 10 MB 和明确图片 MIME 类型，成功后插入 Markdown。发送期间禁止再次发送；未知网络结果不重试，草稿保留，返回待审核则明确提示。只有服务端确认接收才删除草稿。

## Alternatives considered

- 在原生表单收集密码：交互可以统一，但需要自行处理 MFA、OAuth 和站点验证变化；采用官网流程。
- 仅凭本地 Cookie 判定登录：速度快，但不能区分服务器已撤销的会话；以 current_user 响应为准。

## Consequences

密码输入由官网承载。网络验证仍依赖站点当前规则。网页登录成功后的接口可用性需要真实账号和网络环境验收。

## Verification

单元测试验证 CSRF、失败不重发与响应解析；真机检查登录入口、设置持久化和未登录时的功能引导。测试不向真实社区发帖、点赞或发送私信。
