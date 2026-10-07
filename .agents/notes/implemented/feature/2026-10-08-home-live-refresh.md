# Agent Note: 首页新数据提示与 MessageBus 订阅

Status: implemented

## Problem

首页停留期间不知道列表已有新话题或回复。用户希望像酷安一样把底栏首页图标和文字切成“刷新”，点击再更新，保留收到消息时的阅读位置。

## Decision

独立 MessageBus 传输提供可取消的 FeedUpdates 流，与首页差异检查、账号和刷新竞态解耦。用户已批准接入。

Linux.do 使用 ping.ldstatic.com 的 HTTP MessageBus 长轮询，非 WebSocket。独立无 Cookie 的客户端发送 Dont-Chunk 请求读取 JSON 数组，禁止重定向。登录时先由主站同源 HTML 获取 shared_session_key，只通过 X-Shared-Session-Key 发到固定推送端点；不持久化或记录密钥，不转发主站 Cookie。订阅 /latest，登录附加 /new、/unread、/unread/{id}。游标从 -1 初始化，处理 /__status，去重并串行轮询；失败退避、遵守 Retry-After，取消立即停止，重新连接检查遗漏。

推送仅作为检查提示。首页按当前筛选向服务器合并请求第一页，最多每15秒一次；由服务器决定未读、静音、个人列表、分类等成员资格。比较话题 ID、bumped_at、回复数及标题等可见内容，不因浏览量、点赞或排序轻微变化提示刷新。不更改现有列表、分页或滚动位置。顶部“新 (数量)”仍为独立跟踪快照。

首页恢复前台进行差异检查，替代此前自动刷新首页的决定；分类页保留原恢复行为。主页面不可见或后台时停止订阅与检查，恢复重新建立游标并检查缺口。切换账号/筛选取消旧任务、清空提示并重新基线。成功刷新只确认刷新开始前的通知；期间新到通知还会检查，失败保留提示。底栏点击“刷新”先回首页和顶部，再请求第一页。

## Related decisions

检索 MessageBus、推送、订阅、FeedViewModel：与[会话筛选](../../implemented/feature/2026-10-08-session-feed-filters.md)部分重叠，替代首页恢复自动刷新但不改变分类恢复和新话题计数；与[请求契约](../../implemented/architecture/2026-10-07-discourse-data-contract.md)部分重叠，独立传输不放宽一般请求同源限制；客户端验证和液态UI记录属于验证背景，不重写其决定。无完全吸收或过时提案。

## Alternatives considered

- WebSocket：原生双向连接成熟且开销小，但站点实际提供 MessageBus HTTP 协议，猜测端点不可用。
- 定时轮询列表：不需要实现推送协议，但高频空请求浪费网络；使用消息触发、限频合并的列表检查。
- 收到 /latest 就直接亮起刷新：最快且省一次请求，但会对静音、个人和其他筛选误报；列表接口确认当前筛选有变化后再提示。
- 复用主站带 Cookie 的客户端访问推送域名：实现少，但打破会话同源边界；固定推送地址及独立共享会话凭据维持最小凭据范围。

## Testing

MessageBusTest 的5项测试通过：无 Cookie、共享密钥、游标初始化/重置/去重、重定向拒绝、限流退避与取消，日志 artifacts/live-refresh-transport.txt。FeedUpdatesTest 的5项测试通过：合并20次提示、忽略仅浏览量变化、切换筛选、失败保留与成功清除、刷新期间更新遗漏/已包含两条路径、后台停止/恢复、换号撤销在途检查，日志 artifacts/live-refresh-state.txt。界面接入和真机验收尚未完成。

## Consequences

共享会话凭据或 HTML、推送主机配置可能变化；站点验证和断网会暂停提示，但手动刷新继续可用。合并检查有最多约15秒延迟。真实账号与其私有频道需要独立线上验收，模拟协议不证明账号链路。
