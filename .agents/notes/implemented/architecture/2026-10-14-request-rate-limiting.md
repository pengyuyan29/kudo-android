# Agent Note: 请求限流与 429 恢复

Status: implemented

## Problem

真机使用中频繁出现服务端 429（请求较频繁）。此前网络层只有被动上报：DiscourseClient 收到 429 时解析 `Retry-After` 头并抛 ApiFailure，由 ViewModel 用 `retryAfter` 或本地兜底间隔退避。缺少主动限流层，首页 15 秒轮询、话题页 3 秒轮询、头像批量请求、写操作叠加后，短时间内很容易超过 Discourse 按 IP/cookie 维度的阈值，从源头触发限流。

参考实现 FluxDO（Flutter）用「主动滑动窗口限流 + 被动多来源解析 + 统一恢复重放」三层解决，酷DO 此前只有被动层的一半。

## Decision

新增独立包 `com.kudo.android.network.limits`，与 `DiscourseClient` 的 OkHttp 调用链解耦，通过 OkHttp 拦截器接入。

### 主动限流 `RequestRateLimiter`

按 host 共享滑动窗口。`HostState` 以 host 小写为 key 存在 `RequestRateLimiter.states` 中，同一 host 的所有 OkHttp 实例共用一份窗口与队列，避免主站、connect、推送各自计数导致实际速率超限。窗口与并发上限从 [RateLimitSettings] 动态读取，默认并发 3、3 秒窗口 6 个请求。

- 优先级 `RequestPriority`：写操作（POST/PUT/DELETE/PATCH）`HIGH` > 普通 GET `NORMAL` > 静默请求 `LOW`。
- 超过并发或窗口上限时请求进入 `HostState.queue` 排队；`drainLocked` 按优先级 + FIFO 放行。
- 排队线程每 250ms 醒来检查取消并触发一次 `drain`，保证窗口到期后无人 release 时也能重新调度。
- 被 drain 授予配额后若调用方取消，`entry.granted` 为真则归还槽位，避免泄漏。

### 多来源解析 `RateLimitHeaders`

`headerSeconds` 解析响应头：标准 `Retry-After`（秒数或 RFC 1123 日期）、`x-ratelimit-reset` / `ratelimit-reset` / `x-rate-limit-reset`（秒数或 Unix 时间戳）。`fromBodyText` 解析响应体：`extras.wait_seconds` / `extras.time_left`、`error` / `errors` 文案里的中文「请等待 N 秒/分钟/小时/天」与英文 "Please wait N seconds"。`from(response)` 先头后体。DiscourseClient、DiscourseMessageBus 与 `retryAfterSeconds` 共用这一份口径。

### 429 自动重放 `RateLimitRecovery`

- 解析到等待时长且 ≤ `maxWaitSeconds`（默认 30 秒）时，`Thread.sleep` 后重放；
- 超过上限或解析不到时长时原样返回 429，交 DiscourseClient 抛 ApiFailure 提示用户；
- 只对幂等方法（GET/HEAD/OPTIONS）自动重放，写操作绝不重放；
- `maxAttempts`（默认 4）限制重放次数防环。

### 接入

`AppContainer` 构造单个 `RequestLimits`（组合 limiter + recovery），通过 `install(builder)` 注入 `DiscourseClient`、`ConnectRepository` 与 `DiscourseMessageBus` 的 OkHttp 客户端。`DiscourseMessageBus` 新增可选 `limits` 参数，对独立推送 host 同样生效。设置页「网络」分区新增「请求限流」入口，`RateLimitSettingsScreen` 提供并发数、窗口请求数、窗口时长三组预设。

## Alternatives considered

- **只在 ViewModel 层加大退避间隔** — 改动最小，但治标不治本：轮询频率降低仍无法约束多路并发叠加，且写操作与头像请求不受控。主动限流层从源头压制总速率，收益更大。
- **直接引入 OkHttp 的限流第三方库** — 省去自研，但现成库大多不支持按 host 共享窗口、优先级队列和动态配置，且引入额外依赖与许可证审查成本；自研核心逻辑约 150 行，可控性更好。
- **复用 FluxDO 的精确参数（并发 3、3 秒 6 个）硬编码** — 简单，但不同网络环境和账号等级阈值不同，硬编码无法适配；改为可配置并给默认值。

## Consequences

- **收益**：请求速率被主动压到配置阈值内，从源头减少 429；429 出现时只读请求能自动恢复，写请求保持不重放契约；`Retry-After` 解析覆盖 Discourse 实际使用的多种表达。
- **代价与已知上限**：限流排队会让排队请求阻塞 OkHttp dispatcher 线程，默认并发 3 下影响可控；参数设置过严会拖慢浏览；自动重放只处理 ≤30 秒等待，更长等待仍需用户手动重试。与 CF 验证重试、写请求不重放契约的交互需要测试覆盖，避免叠加出重复请求。

## Verification

新增三个测试文件：`RateLimitHeadersTest` 覆盖秒数、HTTP 日期、reset 变体、body wait_seconds、中英文文案与无信息回退；`RequestRateLimiterTest` 覆盖窗口限流、同 host 跨实例共享窗口、排队取消不阻塞；`RateLimitRecoveryTest` 覆盖 GET 自动重放、写请求不重放、超限不等待、预算耗尽停止。

当前沙箱缺少 Android SDK、JDK 与 Rust 工具链，无法执行 `./gradlew test`；以上测试需在具备构建环境的机器上运行确认。`npm run verify-notes` 已通过。
