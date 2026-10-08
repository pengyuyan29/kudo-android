# Agent Note: 固定开发签名与按 Tag 发布正式包

Status: implemented

## Problem

Debug 依赖开发机器的默认证书，Release 没有签名配置，仓库没有 GitHub Actions。不同机器的安装包无法保持签名一致，手动打包也不能保证版本名、签名与发布 Tag 对应。

## Decision

生成器创建独立的 Debug、Release RSA 4096 / PKCS12 密钥库，有效期 10000 天，分别导出公开证书及 SHA256 指纹。私钥、密码与生成结果保存在忽略 Git 的 signing/ 和 signing.properties；生成器遇到已有文件必须拒绝覆盖。固定两套签名使本机与 CI 使用相同更新身份，原 Android 默认 Debug 密钥不被修改。

Gradle 从本地 signing.properties 读取各变体配置，DEBUG_* / RELEASE_* 环境变量优先，signingPropertiesFile 可切换本地配置文件。配置自定义 Debug 证书时使用固定开发签名；没有私有配置的普通源码克隆仍可使用 Android 默认 Debug 签名。Release 构建缺少签名时明确失败，不能静默生成未签名包或回退 Debug 证书。密码经环境变量传给 keytool，生成与验签脚本不打印密码或私钥。

Tag 发布只接受 vMAJOR.MINOR.PATCH，版本名去掉 v，versionCode 使用 major×1000000 + minor×1000 + patch + 1；minor 与 patch 为 0 到 999，最终值不超过 Android 的 2100000000 上限。拒绝前导零、预发布后缀与其他 Tag 格式，避免不同 Tag 映射到同一版本号。普通本地构建保留 0.1.0 / 1 默认值，可用 versionName、versionCode Gradle 属性覆盖。Tag 通过环境变量进入带引号的 shell 参数，不能直接插入命令源码。

GitHub Actions 在 v 开头的 Tag 推送时校验 Tag、执行脚本测试、笔记校验、JVM 测试及 Lint，然后从 GitHub Secrets 恢复 Release 密钥库，构建签名 APK。工具链为 Ubuntu 24.04、JDK 21、Node 22、Android SDK 36 和 Build Tools 35.0.0；外部 Action 固定为已向官方仓库核实的提交 SHA。Windows 的 gradlew.bat 与 Linux 的 bash ./gradlew 均可使用仓库 Wrapper。

构建作业只读仓库，关闭共享 Gradle 缓存和 configuration cache；四个 Secrets 为 RELEASE_KEYSTORE_BASE64、RELEASE_STORE_PASSWORD、RELEASE_KEY_ALIAS、RELEASE_KEY_PASSWORD。密钥放 runner.temp，验证完成或失败后均尝试移除。prepare_release.py 使用 apksigner 验证 APK 完整性，比较唯一签名证书与正式密钥库导出的证书，使用 aapt 验证包名、Tag 版本及非 debuggable 标记，全部通过后才复制 APK 和生成对应 SHA256 文件。

发布作业仅下载这两个产物并复查 SHA256，使用目标仓库的 GITHUB_TOKEN 写 Release，不需要额外长期发布令牌。它先创建草稿、上传完整文件后再公开；同 Tag 遗留草稿可重试，已公开版本拒绝覆盖。检查或构建失败不会进入发布作业，上传失败不会公开不完整草稿。Actions 中转产物保留 14 天，公开 Release 附件不受此限制。

## Related decisions

检索现有笔记：[构建入口](2026-10-07-android-build.md)部分重叠，保留 Wrapper、AGP、JDK 与签名不入库的决定并互链；[客户端验证](../testing/2026-10-07-client-verification.md)部分重叠，保留真机与测试夹具边界。其他 UI、账号与头像笔记不承载发布流程，不归档或覆盖旧决策。

## Alternatives considered

- 继续使用机器默认证书并手工选择正式密钥：配置最少，但签名依赖机器且容易误发未签名包；使用独立、固定的私有配置与Release构建门禁。
- 将密钥库提交Git：新机器和CI无需配置就能复现签名，但会公开长期私钥，且违反现有构建约定；仅提交配置示例，CI使用Secrets。
- 用GitHub run_number作为versionCode：重跑和构建次数容易递增，但依赖工作流历史，不能由Tag独立复现；使用有范围校验的三段版本映射。
- 同时支持任意预发布Tag：适合复杂发布通道，但需要额外约束alpha、beta、rc及正式版的安装升级顺序；当前只支持已批准的三段正式版本。

## Verification

2026-10-08 本地验证：

- Debug 与开启 R8 的 Release 均构建成功，通过 apksigner 验证并匹配各自独立的 RSA 4096 证书。真实重复生成后对比所有签名文件哈希，原文件不变；Git 忽略生效，已跟踪及未忽略文件中没有生成的密码。
- python -m unittest discover -s scripts/tests -v 的 15 项测试通过，覆盖覆盖保护、无效 Base64、Tag 格式和范围、组件进位后的版本排序、GitHub 输出注入、错误签名和 Manifest、校验失败不得产生发布文件。
- 四项真实 Gradle 配置检查通过：缺失 Release 签名、部分配置、密钥文件不存在均失败；空配置仍允许默认 Debug 构建。
- 按 CI 顺序使用空本地签名配置运行 testDebugUnitTest 与 lintDebug，检查成功；57 项 JVM 测试结果为零失败，本次复用 Gradle 判定未改变的测试结果；Lint 为 0 errors / 28 warnings。再由 Base64 临时恢复真实 Release 密钥，仅使用环境变量完成版本 0.1.0 / 1001 的 R8 构建（1分2秒）、验签及打包，临时密钥已清理，原始密钥哈希不变。
- 对真实 Debug APK、篡改后的 Release APK、与 Tag 不一致的版本执行发布脚本，三者均被拒绝且未生成发布文件。签名 APK 和校验文件位于 artifacts/release/，过程日志在 artifacts/release-validation/；这些目录不入库。
- release.yml 通过 actionlint 1.7.12。提取真实发布 shell、用本地 gh 替身验证新版本、遗留草稿、已公开版本、上传失败及校验失败五条路径；没有调用远程 GitHub。日志在 artifacts/publish-validation/。
- PowerShell 中包含路径的 -P 参数须整体加引号；未引用的路径在测试命令中被拆分，修正调用后通过。README 示例保留整体引号。

## Consequences

签名与版本检查使发布包能保持更新身份并对应 Tag；代价是开发者必须备份 signing/ 和 signing.properties。丢失正式证书或密码后无法使用原签名更新已安装应用。Debug 和 Release 保持相同包名、使用不同签名，不能互相覆盖安装；当前手机仍保留旧 Debug 签名应用，本次未卸载或替换，也未把构建和验签当成真机运行验收。

当前仓库没有远程地址，用户明确暂缓远程仓库与 Secrets 配置；本次完成本地证书、Gradle 和工作流，未创建 Tag、远程 Release 或线上运行记录。仓库建立后须配置四个 Secrets 并真实推送 Tag，才能验证云端环境、权限与上传。关闭共享缓存使首次云端构建更慢，且依赖 Google Maven、Maven Central、Android SDK 及 GitHub 服务。稳定三段版本暂不支持 alpha/beta/rc；需要这些通道时须先设计安装升级顺序。
