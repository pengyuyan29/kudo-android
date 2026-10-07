# 第三方组件与参考

酷DO 是独立的 Linux.do 第三方客户端，与 Linux.do、酷安及下列项目不存在官方关联。各依赖的版权和许可归其权利人所有。

| 项目 | 许可 | 用途 |
| --- | --- | --- |
| [AndroidX / Jetpack Compose](https://android.googlesource.com/platform/frameworks/support/) | [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0) | 原生界面、生命周期、导航与测试 |
| [Kotlin](https://github.com/JetBrains/kotlin) / [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | Apache-2.0 | 语言运行库与协程 |
| [Kyant Backdrop 1.0.6](https://github.com/Kyant0/AndroidLiquidGlass) | Apache-2.0 | 背景采样、模糊与玻璃折射 |
| [OkHttp](https://github.com/square/okhttp) / [Okio](https://github.com/square/okio) | Apache-2.0 | HTTP 与 I/O |
| [Coil](https://github.com/coil-kt/coil) | Apache-2.0 | 图片加载 |
| [Jsoup](https://jsoup.org/) | [MIT](https://jsoup.org/license) | HTML 内容解析 |
| [JUnit 4](https://junit.org/junit4/) | [EPL-1.0](https://www.eclipse.org/legal/epl-v10.html) | 测试，不进入正式应用运行代码 |

接口行为研究参考 [FluxDO](https://github.com/Lingyan000/fluxdo)（GPL-3.0），没有复制其 Flutter 实现。酷安 16.6.4 仅作为界面比例和交互研究参考，没有复制其图标、图片或反编译实现。应用图标使用本项目绘制的黄色 K 标记。

依赖的确切版本见 `app/build.gradle.kts`；传递依赖可用 `gradlew.bat :app:dependencies --configuration debugRuntimeClasspath` 查询。正式分发时应连同各依赖要求的 LICENSE / NOTICE 保留完整许可文本。
