# Agent Note: 酷安式举报与编辑表单

Status: implemented

## Problem

举报仍在玻璃浮动面板内使用 Material 单选和描边输入，发帖与回复共用全屏描边表单。用户提供酷安整页举报、开放正文发帖和键盘上方回复面板截图并批准迁移。

## Decision

Foundation 输入和自绘单选、开关、扁平按钮形成统一控件。举报使用独立整页，分组原因行、无描边说明与底部提交，继续消费网站动态原因、字数、违法确认和写入锁。发帖用分类选择行、较大标题、开放正文与底部工具条；分类通过新底部选择面板。回复在话题上方以 CommunitySheet 展示，进入聚焦并显示键盘，保留实际楼层作为发送目标，关闭保存草稿。

回复编辑器在话题导航条目内承载，发布成功刷新该条目并触发首页缓存失效；新的话题仍走独立 compose 页面。上传、Markdown 工具、按用户/话题/楼层隔离的草稿及网站写入逻辑继续复用。发送中禁止关闭以保留当前提交归属，失败保留内容。

同一话题会复用编辑器 ViewModel，成功后同时清理落盘和内存草稿、恢复发送状态；否则再次展开会显示已发送正文且仍被锁定。发布请求允许注入夹具以验证真实 ViewModel 生命周期，不对社区写入。楼中楼点击回复先关闭讨论面板，再打开回复编辑器，避免旧焦点锁阻挡输入。

## Related decisions

[账号与编辑器](../../implemented/feature/2026-10-07-account-and-composer.md)部分重叠，保留草稿、上传、登录和写入契约；[主题操作](../../implemented/feature/2026-10-09-topic-actions.md)部分重叠，举报承载从底部改整页而权限、校验与不重发继续成立；[楼中楼](../../implemented/feature/2026-10-10-nested-replies.md)保留真实回复目标；[底部面板](../../implemented/feature/2026-10-10-coolapk-sheets.md)负责手势与安全区。无完全吸收或过时提案。

## Alternatives considered

- 只去掉 OutlinedTextField 的边框改动最少，但回复仍然全屏、举报仍浮动，无法满足已确认的布局。
- 将回复做成独立导航 Dialog 能复用导航生命周期，但独立窗口会增加话题背景、键盘和已有同窗口模态协调；采用话题条目内的面板，ViewModel 仍按账号和楼层隔离。

## Testing

EditorLifecycleUiTest 先复现成功后内存草稿仍保留，日志 artifacts/editor-reuse-repro-device.txt，再修复；覆盖不同回复目标隔离、失败保留与成功清理。CommunityFormsUiTest 覆盖发帖分类和工具、回复键盘及拖关重开、整页举报校验。构建已通过，完整单测、Lint 和真机集中回归进行中，不声称尚未完成的检查通过。

## Consequences

回复不再通过 compose 导航返回，需要显式连接成功刷新；键盘首次返回应先收起键盘。大字、横屏和长正文必须能滚动且提交按钮可达。
