# Third-Party Notices

## 固定构建工具与直接依赖

构建/验证工具与 Host 直接依赖共用本表；每行单独声明是否随产品分发。Maven artifact 的逐文件 SHA-256 位于 `gradle/verification-metadata.xml`，解析版本位于各模块 `gradle.lockfile`。

| 名称 | 固定版本或提交 | 官方来源 | 许可证 | 项目用途与分发 |
| --- | --- | --- | --- | --- |
| Eclipse Temurin JDK | `17.0.19+10` | `adoptium/temurin17-binaries` release `jdk-17.0.19+10` | GPL-2.0 with Classpath Exception | 本地与 CI JVM；不随产品分发 |
| Gradle | `9.5.0` | `services.gradle.org/distributions/gradle-9.5.0-bin.zip` | Apache-2.0 | Wrapper 构建入口；仓库只提交 Wrapper JAR/脚本 |
| Kotlin Gradle Plugin | `2.4.10` | Maven Central，`org.jetbrains.kotlin:kotlin-gradle-plugin` | Apache-2.0 | 仅五个 Host 空模块；后续分发边界由实现任务决定 |
| Android Gradle Plugin | `9.3.0` | Google Maven，`com.android.tools.build:gradle` | Apache-2.0 | Android/Native 空模块构建；不随产品分发 |
| Android apksig | `9.3.0` | Google Maven，`com.android.tools.build:apksig` | Apache-2.0 | M1-02 Host 输入签名验证；随 Host 产品分发但产品不调用其签名 API |
| Java Native Access (JNA/JNA Platform) | `5.19.1` | Maven Central；`java-native-access/jna` tag `5.19.1` (`1a91122853f6ab6f1fb2a4a284a6cf2ed8af0a4d`) | LGPL-2.1-or-later or Apache-2.0 | M1-05 Host 在 Windows 调用 `MoveFileExW`/文件 ID、在 Linux 调用 `renameat2(RENAME_NOREPLACE)`，保证原子且不覆盖发布；随 Host 产品分发 |
| Android SDK Platform | `platforms;android-36` | Android SDK Manager | Android SDK License | 编译 API；不随产品分发 |
| Android SDK Build Tools | `build-tools;36.1.0` | Android SDK Manager | Android SDK License | Android 构建和后续对齐验证；不随产品分发 |
| Android NDK | `ndk;29.0.14206865` | Android SDK Manager | Apache-2.0 and bundled third-party notices | 四 ABI 空库构建；M0-03 产物不发布 |
| CMake | `cmake;4.1.2` | Android SDK Manager / Kitware | BSD-3-Clause | Native 配置与构建；不随产品分发 |
| Node.js | `24.12.0` | `nodejs.org` official release | MIT | 治理和供应链脚本；不随产品分发 |
| actions/checkout | `3d3c42e5aac5ba805825da76410c181273ba90b1` | `actions/checkout` | MIT | CI checkout，不随产品分发 |
| actions/setup-node | `820762786026740c76f36085b0efc47a31fe5020` | `actions/setup-node` | MIT | CI Node.js 固定安装，不随产品分发 |
| actions/setup-java | `03ad4de0992f5dab5e18fcb136590ce7c4a0ac95` | `actions/setup-java` | MIT | CI Temurin 固定安装，不随产品分发 |
| actions/cache | `caa296126883cff596d87d8935842f9db880ef25` | `actions/cache` | MIT | 以 lockfile、verification metadata、Wrapper 和版本目录为键的 CI 缓存 |

Gradle distribution SHA-256 为 `553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746`；Wrapper JAR SHA-256 为 `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`；Windows x64 Temurin ZIP SHA-256 为 `b5b235c48adf6a081874b812c630b9f4b5f637b7a5ed18b9174d08a41ec4c235`。

当前未安装或执行外部 Agent Skill。JADX 与 Apktool 仍只是候选验证工具，未进入 M0-03。

## 登记要求

任何新增库、二进制、源码、生成器、GitHub Action、Agent Skill 或测试资产必须记录：

- 名称、版本和不可变来源。
- 上游项目和固定提交或 SHA-256。
- 许可证、版权和 NOTICE 义务。
- 在本项目中的用途与分发方式。
- 本地修改和安全审计日期。
- 负责 Issue 和批准者。

未完成来源、许可证与哈希登记的第三方内容不得进入发布产物。
