# Agent Note: 原生工程与提交校验入口

Status: implemented

## Problem

初始仓库只有参考技能，没有 Android 构建入口，也不能在根目录运行约定的笔记校验命令。

## Decision

工程采用 Gradle Wrapper 8.13、AGP 8.13.2 和 Kotlin 2.3.10，compileSdk / targetSdk 36、minSdk 26。Kotlin 使用 compilerOptions 声明 JVM 17。根 package.json 暴露 verify-notes，调用现有技能脚本并锁定 tsx 版本。local.properties、构建输出和签名文件不入库。

## Alternatives considered

- 全面升级到最新 AGP 9：可使用新工具链能力，但会额外引入构建迁移和更高 SDK 依赖；当前稳定版本满足 API 36 和 Backdrop 1.0.6。
- 只依赖本机 Gradle：可以快速启动，但其他开发者无法得到一致版本；使用 Wrapper 固定可复现入口。

## Consequences

Android Studio 或 JDK 17+ 的命令行都能打开工程。首次构建仍需联网下载依赖。原生功能开发按 TASKS.md 分单元提交，完整功能测试在开发完成后集中运行，编译失败则当场修复。

## Verification

构建入口为 gradlew.bat :app:assembleDebug；笔记入口为 npm run verify-notes。Kotlin 2.3 拒绝旧 kotlinOptions.jvmTarget DSL 的编译错误通过改用 compilerOptions 解决，构建本身作为此配置问题的复现检查。
