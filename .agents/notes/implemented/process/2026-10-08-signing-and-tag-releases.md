# Agent Note: 固定开发签名与按 Tag 发布正式包

Status: implemented

## Problem

Debug 依赖开发机器的默认证书，Release 没有签名配置，仓库没有 GitHub Actions。不同机器的安装包无法保持签名一致，手动打包也不能保证版本名、签名与发布 Tag 对应。

## Decision

当前正式版基线为 `0.1.8` / `1009`，下一版从 `0.1.9` / `1010` 开始。Release APK 只打包 `arm64-v8a`，发布脚本将验证 APK 的 `native-code` 清单只包含该 ABI；公开产物统一命名为 `kodo_<version>_release_arm64-v8a.apk` 及对应的 `.sha256` 文件。

`release_version.py` 集中定义发布 ABI 并生成小写文件名，`prepare_release.py` 在复制 APK 前解析 `aapt dump badging`，缺少 native-code、包含多个 ABI 或出现其他 ABI 都会拒绝发布。

生成器创建独立的 Debug、Release RSA 4096 / PKCS12 密钥库，有效期 10000 天，分别导出公开证书及 SHA256 指纹。私钥、密码与生成结果保存在忽略 Git 的 signing/ 和 signing.properties；生成器遇到已有文件必须拒绝覆盖。固定两套签名使本机与 CI 使用相同更新身份，原 Android 默认 Debug 密钥不被修改。

Gradle 从本地 signing.properties 读取各变体配置，DEBUG_* / RELEASE_* 环境变量优先，signingPropertiesFile 可切换本地配置文件。配置自定义 Debug 证书时使用固定开发签名；没有私有配置的普通源码克隆仍可使用 Android 默认 Debug 签名。Release 构建缺少签名时明确失败，不能静默生成未签名包或回退 Debug 证书。密码经环境变量传给 keytool，生成与验签脚本不打印密码或私钥。

Tag 发布只接受 vX.Y.Z，三位组件均为单个 0 到 9 的数字；版本名去掉 v，versionCode 使用 major×1000000 + minor×1000 + patch + 1。补丁位从 0.1.0 递增到 0.1.9 后进位为 0.2.0，大功能提升 minor 或 major 位。拒绝前导零、预发布后缀与其他 Tag 格式，避免不同 Tag 映射到同一版本号。普通本地构建保留 0.1.0 / 1 默认值，可用 versionName、versionCode Gradle 属性覆盖。Tag 通过环境变量进入带引号的 shell 参数，不能直接插入命令源码。

GitHub Actions 在 v 开头的 Tag 推送时校验 Tag、执行脚本测试、笔记校验、JVM 测试及 Lint，然后从 GitHub Secrets 恢复 Release 密钥库，构建签名 APK。工具链为 Ubuntu 24.04、JDK 21、Node 22、Android SDK 36 和 Build Tools 35.0.0；外部 Action 固定为已向官方仓库核实的提交 SHA。Windows 的 gradlew.bat 与 Linux 的 bash ./gradlew 均可使用仓库 Wrapper。

构建作业只读仓库，关闭共享 Gradle 缓存和 configuration cache；四个 Secrets 为 RELEASE_KEYSTORE_BASE64、RELEASE_STORE_PASSWORD、RELEASE_KEY_ALIAS、RELEASE_KEY_PASSWORD。密钥放 runner.temp，验证完成或失败后均尝试移除。prepare_release.py 使用 apksigner 验证 APK 完整性，比较唯一签名证书与正式密钥库导出的证书，使用 aapt 验证包名、Tag 版本及非 debuggable 标记，全部通过后才复制 APK 和生成对应 SHA256 文件。

发布作业下载APK、校验文件和[提交日志](2026-10-11-commit-release-notes.md)生成的正文并复查 SHA256，使用目标仓库的 GITHUB_TOKEN 写 Release，不需要额外长期发布令牌。它先创建草稿、上传完整文件后再公开；同 Tag 遗留草稿可重试，已公开版本拒绝覆盖。检查或构建失败不会进入发布作业，上传失败不会公开不完整草稿。Actions 中转产物保留 14 天，公开 Release 附件不受此限制。

本机 Git 认证使用 Git Credential Manager 的 Windows Credential Manager 存储（wincredman），origin 仅保存不含凭据的 HTTPS 地址。读取远程或认证信息时先在进程内脱敏，不能直接输出带凭据的 URL 或 credential fill 的原始结果。GitHub Secrets 由 GitHub CLI 加密提交，密钥及密码通过标准输入传递，GitHub 访问令牌只进入子进程环境。

## Action runtime

v0.1.5 的 build、publish 虽然成功，使用的七个 Action 的 action.yml 都声明 node20，因此 GitHub 强制使用 Node 24 时产生弃用警告，setup-java v4 另有版本弃用提示。Action 自身的运行时由上游 action.yml 决定，与 setup-node 安装供笔记脚本使用的 Node 22 分开管理。

工作流使用截至 2026-10-11 已从各官方仓库核实的稳定发布版本，并继续固定完整提交 SHA：

| Action | 版本 | 上游声明的运行时 |
| --- | --- | --- |
| actions/checkout | v7.0.1 | node24 |
| actions/setup-java | v6.0.1 | node24 |
| actions/setup-node | v7.1.0 | node24 |
| gradle/actions/setup-gradle | v6.4.0 | node24 |
| android-actions/setup-android | v4.0.4 | node24 |
| actions/upload-artifact | v7.0.2 | node24 |
| actions/download-artifact | v8.0.2 | node24 |

Node 24 Action 至少需要支持该运行时的 Actions Runner；官方迁移文档给出的最低版本为 v2.327.1，发布仍使用 GitHub 托管的 ubuntu-24.04。JDK 21、脚本 Node 22、SDK 36、Build Tools 35.0.0 及仓库 Gradle Wrapper 保持原有版本。setup-node 显式设置 package-manager-cache: false，避免新版包管理器自动检测开启共享缓存；setup-gradle 保留 cache-disabled: true，不将签名相关构建状态写入共享缓存。

新版上传支持不压缩的单文件制品，但发布需要 APK、摘要和正文三个文件，因此显式使用 archive: true；下载显式使用 skip-decompress: false，让文件继续直接解压到 dist。digest-mismatch: error 使 Actions 制品摘要不一致时立即失败，发布 shell 仍单独核对 APK 的 SHA256。Tag 触发、权限拆分、临时密钥清理和草稿上传完才公开的契约保持一致。

## Related decisions

检索现有笔记：[构建入口](2026-10-07-android-build.md)部分重叠，保留 Wrapper、AGP、JDK 与签名不入库的决定并互链；[客户端验证](../testing/2026-10-07-client-verification.md)部分重叠，保留真机与测试夹具边界。其他 UI、账号与头像笔记不承载发布流程，不归档或覆盖旧决策。

## Alternatives considered

- 继续使用机器默认证书并手工选择正式密钥：配置最少，但签名依赖机器且容易误发未签名包；使用独立、固定的私有配置与Release构建门禁。
- 将密钥库提交Git：新机器和CI无需配置就能复现签名，但会公开长期私钥，且违反现有构建约定；仅提交配置示例，CI使用Secrets。
- 用GitHub run_number作为versionCode：重跑和构建次数容易递增，但依赖工作流历史，不能由Tag独立复现；使用有范围校验的三段版本映射。
- 同时支持任意预发布Tag：适合复杂发布通道，但需要额外约束alpha、beta、rc及正式版的安装升级顺序；当前只支持已批准的三段正式版本。
- 保留旧 Action 并依赖 GitHub 强制切换 Node 24：改动最少且 v0.1.5 已在兼容模式下发布成功，但上游仍声明已弃用的 Node 20，不能消除运行时和 setup-java 的版本弃用问题；升级到官方声明 node24 的稳定版本，不通过强制环境变量掩盖警告。
- 移除 setup-android，直接使用 runner 的 sdkmanager：可以减少一个第三方 Action，但需要自行维护命令行工具、许可证和安装路径；上游 v4.0.4 已支持 Node 24，保留原有 SDK 安装接口。

## Verification

2026-10-11：使用 `-PversionName=0.1.8 -PversionCode=1009` 完成正式 R8 构建，默认 Gradle 版本保持 `0.1.0 / 1`。正式包为 `artifacts/release/0.1.8/kodo_0.1.8_release_arm64-v8a.apk`，SHA256 为 `9acd96dff6cdddf78f533e782104c868c00ce00d019db7b78f1c5e0cdf850c49`；正式证书与既有发布一致，版本 `0.1.8 / 1009`、仅 `arm64-v8a`、非 debuggable 及16KB zipalign检查通过，详情为同目录 `local-verification.txt`。27项发布工具测试及完整290项JVM测试通过；主页旧夹具遗漏分类目录请求的问题及修正见[公开主页协议](../architecture/2026-10-10-public-profile-data.md)。本次发布前的既有UI与Lint结果见[加载圆环](../feature/2026-10-10-in-app-web-links.md)；本地验签不替代云端附件验收，不覆盖手机已安装的开发版。

本次发布脚本测试覆盖 `v0.1.6` 到 `1007`、`v0.1.7` 到 `1008` 的映射，覆盖 `0.1.9` 到 `0.2.0` 的进位、新小写 APK 名称，以及缺失、多 ABI 和非 `arm64-v8a` 清单的拒绝路径。

2026-10-11：使用 `-PversionName=0.1.6 -PversionCode=1007` 完成正式 R8 构建，`prepare_release.py` 生成 `kodo_0.1.6_release_arm64-v8a.apk` 及校验文件。`aapt` 显示包版本为 `0.1.6/1007` 且只含 `arm64-v8a`；`apksigner` 显示单一签名者、V2 验签通过且没有 debuggable 标记。APK SHA256 为 `7e4898ae810ecd9d7ee6a6da278f9975024b8facb78808079200af3f4b9a7d7c`。

2026-10-11：使用 `-PversionName=0.1.7 -PversionCode=1008` 完成正式 R8 构建，`prepare_release.py` 生成 `kodo_0.1.7_release_arm64-v8a.apk` 及校验文件。`aapt` 显示包版本为 `0.1.7/1008` 且只含 `arm64-v8a`；`apksigner` 显示同一正式证书、V2 验签通过且没有 debuggable 标记。APK SHA256 为 `40d57964f5dbe45938e7f6f73efd772b717515b532988dfba7a0098828631a20`。

2026-10-08 本地验证：

- Debug 与开启 R8 的 Release 均构建成功，通过 apksigner 验证并匹配各自独立的 RSA 4096 证书。真实重复生成后对比所有签名文件哈希，原文件不变；Git 忽略生效，已跟踪及未忽略文件中没有生成的密码。
- python -m unittest discover -s scripts/tests -v 的 15 项测试通过，覆盖覆盖保护、无效 Base64、Tag 格式和范围、组件进位后的版本排序、GitHub 输出注入、错误签名和 Manifest、校验失败不得产生发布文件。
- 四项真实 Gradle 配置检查通过：缺失 Release 签名、部分配置、密钥文件不存在均失败；空配置仍允许默认 Debug 构建。
- 按 CI 顺序使用空本地签名配置运行 testDebugUnitTest 与 lintDebug，检查成功；57 项 JVM 测试结果为零失败，本次复用 Gradle 判定未改变的测试结果；Lint 为 0 errors / 28 warnings。再由 Base64 临时恢复真实 Release 密钥，仅使用环境变量完成版本 0.1.0 / 1001 的 R8 构建（1分2秒）、验签及打包，临时密钥已清理，原始密钥哈希不变。
- 对真实 Debug APK、篡改后的 Release APK、与 Tag 不一致的版本执行发布脚本，三者均被拒绝且未生成发布文件。签名 APK 和校验文件位于 artifacts/release/，过程日志在 artifacts/release-validation/；这些目录不入库。
- release.yml 通过 actionlint 1.7.12。提取真实发布 shell、用本地 gh 替身验证新版本、遗留草稿、已公开版本、上传失败及校验失败五条路径；没有调用远程 GitHub。日志在 artifacts/publish-validation/。
- PowerShell 中包含路径的 -P 参数须整体加引号；未引用的路径在测试命令中被拆分，修正调用后通过。README 示例保留整体引号。

2026-10-08 远程接入验证：

- 私有仓库 woliubang256/kudo-android 已连接，main 包含发布工作流；GitHub API 确认 Actions 已启用，Android Release 工作流处于 active。
- GitHub CLI 2.102.0（下载包经官方 SHA256 校验）写入四个 Repository Secrets。写入前重新导出并核对本地正式证书，写入后通过 API 确认四项 Secret 名称均存在；没有输出密钥、密码或 Secret 值。实际云端取用与签名仍待首次 Tag 构建验证。
- origin 内嵌凭据已迁至 Windows Credential Manager，存储后的回读比对与私有仓库 ls-remote 认证均通过，fetch / push URL 均不再包含用户信息。最初远程检查曾意外输出内嵌令牌，已告知用户撤销并更换；文档和提交不保存该令牌。
- 接入阶段当时未创建版本 Tag、触发发布运行或创建 GitHub Release；仓库接入与 Secrets 配置不等于线上发布验收。

2026-10-11 首次真实 Tag 发布验证：

- 用户确认0.1.5真机修复有效并授权远程Tag与打包发布。main与annotated Tag `v0.1.5` 原子推送，Tag固定指向 `b3c072c5e92e41f3e8d17b05be7bb4f56d1ab3b7`；后续验收笔记提交不移动Tag。
- [Android Release运行38074297223](https://github.com/woliubang256/kudo-android/actions/runs/38074297223)首轮245项JVM测试中，TopicFloorNavigationTest.aSupersededJumpCannotReplaceTheLatestTarget发生5秒等待超时，签名与发布均被跳过。本地独立6项及完整245项JVM测试、Lint通过；未改代码或测试，重跑同一失败构建。第二轮20项发布工具测试、笔记、完整JVM测试与Lint通过；检查构建7分4秒，正式R8构建5分55秒，build与publish均为success。此结果没有确定首次超时根因；再次出现应先保留具体测试报告并排查调度与等待条件，不能把一次重跑通过视为稳定性修复。
- [v0.1.5 Release](https://github.com/woliubang256/kudo-android/releases/tag/v0.1.5)已发布且不是草稿或预发布，认证latest API指向该版本。附件为 `Kudo-0.1.5-release.apk` 与同名 `.sha256`，正文与本地按Tag提交生成的首发日志逐字匹配（忽略尾部空白）。
- 从Release重新下载实际附件后，摘要、apksigner、aapt与16KB zipalign通过：版本0.1.5 / 1006，非debuggable，APK为7,893,508字节；SHA256为 `dea55876a0f537e2c1b1aa8bb73ec6c134fea5dcda9d100fe5665fa312a52aed`，正式证书SHA256为 `457c98f5786d224e5117d732126389b8b7cedad5df1b14366b7e662481d22418`，与原本机正式证书一致。云端和本机APK字节摘要不同，不声明跨平台构建字节可复现。
- 云端附件、正文与校验记录保存于 `artifacts/release/0.1.5/cloud/`；首轮失败日志为 `remote-release-build-failure.log`，第二轮日志为 `remote-release-build-success.log` 与 `remote-release-publish-success.log`。GitHub访问凭据仅用于已有Credential Manager认证和API请求，下载重定向离开api.github.com时移除Authorization，不写入附件、日志或仓库。

2026-10-11 Action 运行时升级本地验证：

- 修改前从固定 SHA 读取七个官方 action.yml，运行时检查全部因 node20 失败；修改后同一检查全部通过，逐一确认声明 node24、工作流参数受支持，固定 SHA 与官方稳定版本 Tag 的实际提交一致。只读检查脚本与前后结果分别保存在 `artifacts/verify_actions_runtime.py`、`actions-runtime-before.json`、`actions-runtime-after.json`，不引入 CI 网络测试依赖。
- 实际 `.github/workflows/release.yml` 通过 actionlint 1.7.12；20 项发布工具测试通过，包含从该工作流提取发布 shell 后验证的新建草稿、恢复草稿、已公开版本拒绝覆盖、上传失败和摘要失败路径。没有改动 App 源码，因此不重复无关的 JVM、Android Lint 或真机测试。
- 本次仅修改工作流和现有发布笔记，并按用户确认推送 main；不创建新发布 Tag，不移动已发布 v0.1.5，不把本地校验视为新 Action 的云端发布验收。

## Consequences

签名与版本检查使发布包能保持更新身份并对应 Tag；代价是开发者必须备份 signing/ 和 signing.properties。丢失正式证书或密码后无法使用原签名更新已安装应用。Debug 和 Release 保持相同包名、使用不同签名，不能互相覆盖安装；当前手机仍保留旧 Debug 签名应用，本次未卸载或替换，也未把构建和验签当成真机运行验收。

远程仓库为 [woliubang256/kudo-android](https://github.com/woliubang256/kudo-android)，私有仓库的 main 已包含工作流，GitHub Actions 已启用，Android Release 工作流处于 active；接入使用本地已生成的正式证书配置四个 Repository Secrets。v0.1.5已完成真实Tag触发、云端构建和Release附件验收。仓库仍为private，本次通过已认证API验收；公开仓库后仍需单独验证匿名latest API与App的更新检查，不能将认证访问通过当成公开可访问。

关闭共享缓存使首次云端构建更慢，且依赖 Google Maven、Maven Central、Android SDK 及 GitHub 服务。稳定三段版本暂不支持 alpha/beta/rc；需要这些通道时须先设计安装升级顺序。

Action 升级跨越多个主版本，本地静态校验及发布 shell 夹具不能替代托管 runner 上的真实 SDK 安装和制品传递。修正仅推送 main，不创建或移动发布 Tag；重跑 v0.1.5 仍使用旧 Tag 的工作流。下一次新版本 Tag 发布需要确认 build、publish 不再出现这两类弃用警告，并重新验签和校验附件摘要；现有首次 JVM 超时问题没有在本次依赖升级中修复。
