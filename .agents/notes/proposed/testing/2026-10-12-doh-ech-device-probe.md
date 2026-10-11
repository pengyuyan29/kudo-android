# Agent Note: 手机非代理网络的 DoH 与 ECH 只读对照验证

Status: proposed

## Problem

[DoH 功能调研](../feature/2026-10-12-doh-network.md)已确认文章节点返回 ECH 配置，但桌面 DNS 查询不能证明手机能直连，也不能证明代理登录后的原生会话继续有效。用户批准开发验证，并说明手机 Wi-Fi 已切换为非代理网络。完整设置和网络接管应建立在可重复的技术结果上。

## Proposal

独立实现测试用 Rust JNI 网络探针，使用 rustls 的 ECH 与正常受信任根证书，依靠 Android 网络实际直连文章节点和主站。它只打包进 androidTest APK，不接管 App 的日常请求，不改变登录、代理、VPN 或用户设置。

分小单元完成：原生解析／TLS 和离线测试；可复现 Android arm64 构建；Android opt-in 对照测试；真机记录及结论。每批最多修改 3 个文件，代码与对应笔记同次提交。

对照组为既有 OkHttp + 系统 DNS、既有 OkHttp + 文章 DoH 解析出的地址，以及 Rust + 同节点 DoH + 真正 ECH。固定相同 UA，按 URL 读取现有 Cookie，仅请求会话、话题列表和同站图片。所有组明确不使用 HTTP 代理；若 Android 当前可见 VPN 生效则暂停实验。用户说明的路由器非代理状态是外部条件，App 无法独立证明上游没有透明代理。

探针区分 DNS 配置存在、TLS ECH accepted、HTTP 状态、CF 挑战及 JSON／图片业务成功。DNS 使用标准 wire format，检查响应匹配；TLS 保留原域名证书验证。只允许 HTTPS 主站的白名单 GET；DoH 的 POST 只含 DNS 查询，不携带社区凭据。不跟随重定向、不重试 HTTP 请求、不处理写操作。限制超时和响应大小，不将 Cookie、Set-Cookie、响应正文或账号标识写入报告。

测试只读取既有 Cookie，不清除、伪造或迁移真实登录数据，不把实验响应的 Set-Cookie 写回日常存储。当前会话为空或失效时如实记录，不能宣称完成“代理登录后保持登录”的验证。设备覆盖安装必须核对签名并使用 adb install -r。

## Related decisions

检索 DNS、ECH、JNI、只读探针及网络笔记。与[DoH 功能提案](../feature/2026-10-12-doh-network.md)部分重叠并互链：本篇仅交付技术验证，功能提案继续保持 proposed。原有[数据契约](../../implemented/architecture/2026-10-07-discourse-data-contract.md)和[CF 恢复流程](../../implemented/bug-fix/2026-10-11-cloudflare-verification-flow.md)是实验必须保留的边界，不修改其生产实现。其他 UI、分页和通知笔记无关；没有完全吸收或过时提案。

## Alternatives considered

- 直接上线设置和全局网络层：用户能立即体验开关、节点管理和所有页面，但会在 ECH／CF 尚未验证时扩大回归范围；先交付隔离的对照探针。
- 只在电脑跑 Rust：构建和调试更方便，能验证 DNS、TLS API，但电脑线路和 Cookie 不代表手机；桌面仅用于离线测试，结论以手机为准。
- 通过浏览器 MITM 接管全部请求：可让 WebView 与原生统一出口，但带来证书、私钥和网页兼容成本，超出当前最小原生验证范围。

## Acceptance criteria

- 标准 DNS 解析、响应匹配、缺失 ECH、错误 URL、非白名单请求、凭据隔离及响应上限有离线边界测试。
- arm64 JNI 库可由固定 Cargo.lock 和 NDK 构建，ELF LOAD 对齐支持 16KB；只进入 androidTest APK。
- 日常测试默认不触发线上探针，必须显式指定 instrumentation 参数。
- 当前非代理 Wi-Fi 上三组结果分别记录；ECH 组必须从 TLS 连接实际状态确认 accepted，不能从 DNS 有参数推断。
- 会话、列表、图片成功分开判断；遇到 CF、未登录、超时、证书或 ECH 错误分别记录，不打印真实 Cookie 或正文。
- 笔记校验和关联 Android 编译／测试通过；真实网络失败仍是有效实验结果，不改为跳过或伪造成功。

## Risks

当前手机 Cookie 可能不是刚在代理下获得，无法复原未知历史出口；成功只能证明当前会话在当前非代理线路可复用。单条 Wi-Fi 线路结果不能覆盖蜂窝、其他运营商或长期 CF 策略。Rust HTTP／TLS 特征与 Chromium／OkHttp 不同，ECH accepted 仍可能得到 CF 挑战。首次构建增加 Rust、NDK 和加密库依赖；探针不进入正式包，完整生产集成需另行验证取消、上传、连接池和切换语义。
