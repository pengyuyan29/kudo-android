# Agent Note: 固定开发签名与按 Tag 发布正式包

Status: proposed

## Problem

Debug 依赖开发机器的默认证书，Release 没有签名配置，仓库没有 GitHub Actions。不同机器的安装包无法保持签名一致，手动打包也不能保证版本名、签名与发布 Tag 对应。

## Proposal

用户已批准分签名配置、Tag 发布两个单元实施并分别提交。生成独立的 Debug、Release RSA 4096 / PKCS12 密钥库，有效期10000天，分别导出公开证书及SHA256指纹。私钥、密码与生成结果保存在忽略Git的 signing/ 和 signing.properties；生成器遇到已有文件必须拒绝覆盖。新签名与当前手机旧Debug签名不同，验证使用构建与验签，不卸载现有应用。

Gradle 从本地 signing.properties 读取各变体配置，环境变量优先。配置自定义Debug证书时使用固定开发签名；没有私有配置的普通源码克隆仍可使用Android默认Debug签名。Release构建缺少签名时明确失败，不能静默生成未签名包或回退Debug证书。证书生成与GitHub Secrets配置不打印密码或私钥。

Tag发布只接受 vMAJOR.MINOR.PATCH，版本名去掉v，versionCode使用 major×1000000 + minor×1000 + patch + 1；minor与patch为0到999，最终值不超过Android的2100000000上限。拒绝前导零、预发布后缀与其他Tag格式，避免不同Tag映射到同一版本号。普通本地构建保留当前默认版本，可用Gradle属性覆盖。

GitHub Actions在v开头的Tag推送时校验Tag、执行脚本测试、笔记校验、JVM测试及Lint，然后从GitHub Secrets恢复Release密钥库，构建签名Release APK并校验签名与版本，发布APK和SHA256文件。检查失败不得创建Release。固定外部Action的提交SHA；密钥放临时目录，产物只包含APK和校验文件。工作流在目标仓库内使用GITHUB_TOKEN，不要求额外长期发布令牌。配置和本地验证不创建测试Tag或提前发布正式版本。

## Related decisions

检索现有笔记：[构建入口](../../implemented/process/2026-10-07-android-build.md)部分重叠，保留Wrapper、AGP、JDK与签名不入库的决定并互链；[客户端验证](../../implemented/testing/2026-10-07-client-verification.md)部分重叠，保留真机与测试夹具边界。其他UI、账号与头像笔记不承载发布流程，不归档或覆盖旧决策。

## Alternatives considered

- 继续使用机器默认证书并手工选择正式密钥：配置最少，但签名依赖机器且容易误发未签名包；使用独立、固定的私有配置与Release构建门禁。
- 将密钥库提交Git：新机器和CI无需配置就能复现签名，但会公开长期私钥，且违反现有构建约定；仅提交配置示例，CI使用Secrets。
- 用GitHub run_number作为versionCode：重跑和构建次数容易递增，但依赖工作流历史，不能由Tag独立复现；使用有范围校验的三段版本映射。
- 同时支持任意预发布Tag：适合复杂发布通道，但需要额外约束alpha、beta、rc及正式版的安装升级顺序；当前只支持已批准的三段正式版本。

## Acceptance criteria

两套证书实际生成且指纹不同，重复生成不会覆盖已有私钥。Gradle的Debug和Release APK分别通过apksigner验证并匹配各自证书；缺少Release签名会失败。脚本测试覆盖Tag格式、版本排序、范围与覆盖保护。工作流通过actionlint，本地执行相同打包与验签步骤。签名文件与密码未被Git跟踪，笔记门禁通过。目标仓库与登录可用后配置Secrets；实际Tag发布由用户推送触发，不为验证虚构已成功运行的线上Release。

## Risks

正式证书与密码必须备份，丢失后无法用同一签名更新已安装的正式包。当前仓库没有远程地址，用户明确暂缓远程仓库与Secrets配置；本次完成本地证书、Gradle和可提交的工作流，线上运行留待仓库建立。Debug和Release签名不同且保持相同包名，不能互相覆盖安装。云端构建依赖Google Maven、Maven Central、Android SDK及GitHub服务。
