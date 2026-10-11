# Agent Note: 独立网页验证与挑战误报隔离

Status: proposed

## Problem

“完成网页验证”复用登录回调并打开/login，已登录用户可能跳到个人主页，未进入站点专用/challenge。普通HTML403、无法解析的响应以及页面内Turnstile字样也可能被归为验证。独立推送域名失败会替换首页错误状态，通知流也可能重复提示，造成主站已通过后很快又要求验证。

## Proposal

挑战识别只接受cf-mitigated: challenge或真实挑战页的结构组合，不把普通登录页Turnstile、文章引用和非JSON内容等同过盾。明确挑战只对GET延迟600ms重试一次，重试读取更新后的Cookie；写请求不重放。非挑战的格式错误归SERVER，普通403保持FORBIDDEN，404保持不可访问。

后台推送验证失败停止当前订阅，不覆盖正常列表或反复提示主站验证，现有重新激活/会话检查可以恢复订阅。主站实际读取失败仍保留验证入口。

新增App内全屏验证页，直接打开https://linux.do/challenge，与原生请求共享Cookie和明确相同User-Agent。右上角依次“刷新”“完成验证”，下方提示“如果页面显示 404，请点击刷新，直到出现验证页面，再按提示完成验证。”刷新始终重新访问/challenge。网页404原样展示，工具栏始终可操作。完成按钮先flush Cookie，再检查原生/latest.json的真实话题列表结构；成功关闭验证页并调用原页面只读重试，游客同样可用。网页跳转、onPageFinished、Cookie存在均不单独判定成功；失败留在页面显示原因，不清会话。

错误入口使用统一CompositionLocal承载验证动作，避免逐个页面混用登录回调；验证页作为全屏窗口覆盖在当前导航上，保留原生页面及重试回调，不重放发帖等写入。登录入口及账号检查继续单独负责认证。

工作拆为每批不超过3文件的小单元，分别提交：网络识别及单测/笔记；首页推送及单测/笔记；通知推送及单测/笔记；验证页及界面测试/笔记；统一入口接入及笔记。版本仍为0.1.7/1008，手机保留数据，账号切换不做专项验证。

## Related decisions

检索Cloudflare、过盾、challenge、Cookie、User-Agent。[不可访问话题](../../implemented/bug-fix/2026-10-10-unavailable-topic.md)部分重叠并保留404/权限优先级；[账号与登录](../../implemented/feature/2026-10-07-account-and-composer.md)保留共享Cookie及只读会话检查；[应用内网页](../../implemented/feature/2026-10-10-in-app-web-links.md)保留网页内不跳原生的边界；[首页更新](../../implemented/feature/2026-10-08-home-live-refresh.md)与[运行通知](../../implemented/feature/2026-10-10-runtime-local-notifications.md)的后台验证提示由本篇部分更新。其他命中是资源加载或既有协议引用，与独立恢复入口无关。无完全吸收或过时提案。

## Alternatives considered

- 系统浏览器打开/challenge：网页兼容性最好，且用户熟悉；但系统浏览器Cookie不能自动回传WebView和OkHttp，不能完成App会话的恢复，使用App内共享Cookie网页。
- 继续用登录页同时过盾：复用全部界面且代码少；但已登录重定向会绕开专用入口，也无法区分游客验证和账号认证，增加独立验证页。
- 对所有失败统一重试：短暂网络故障更可能自行恢复；但写操作可能已生效，重复提交有副作用，普通权限/格式错误也不应不断请求，只对明确挑战的GET最多重试一次。

## Acceptance criteria

先编写复现用例验证普通403/格式错误不误报、一次短暂挑战恢复、连续挑战只请求两次、写请求不重放、重试读取新Cookie；确认后台错误不覆盖正常列表。验证入口不走/login，刷新位于完成左侧且重新请求/challenge，提示包含404；网页完成不自动退出，只有原生确认成功才触发一次重试，失败保留验证页，游客不要求登录。相关单测、界面夹具、构建、Lint和verify-notes通过。

## Implementation progress

首个小单元完成网络识别与有限GET重试：DiscourseClientTest先复现17项中的4项失败（artifacts/cloudflare-client-repro-20261011.txt），修复后17项全部通过（artifacts/cloudflare-client-fixed-20261011.txt）。覆盖普通HTML403、登录Turnstile、文章引用及常规jsd脚本不误报；短暂挑战重试成功并读取新Cookie；持续挑战最多两次；POST遇盾不重放。其余网页入口及后台推送隔离仍在本篇提案范围内，不能将首个小单元通过称为整体完成。

首页推送用例先复现正常列表被写入VERIFICATION错误（artifacts/cloudflare-feed-repro-20261011.txt）。接收独立推送失败时只暂停订阅，正常话题及分页不变，主站读取错误仍走既有错误状态。

## Risks

Cloudflare仍可能因出口IP或设备/客户端特征再次验证，修复误判和会话恢复不能保证站点永久放行。明确挑战最多增加600ms及一次GET；后台订阅暂停期间没有实时提示，重新激活后恢复。验证/latest.json确认主站原生读取恢复，具体受保护端点仍以原页面重试结果为准。全屏验证覆盖期间必须阻挡底层交互及阅读曝光，关闭后保留原导航和数据。
