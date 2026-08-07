# 工具链与来源治理

## 1. 目标

所有开发、CI 和发布工具必须来自可验证来源、固定版本并能在干净环境重建。项目不接受“本机已有工具”作为发布依据，也不从输入 APK、客户环境或未审查第三方 Skill 获取可执行依赖。

## 2. 基线工具链

M0-03 必须以仓库配置固化以下基线，不允许依赖开发者全局默认值：

| 类别 | 决策 |
| --- | --- |
| Host/Android JVM | Eclipse Temurin `17.0.19+10` |
| 构建入口 | Gradle Wrapper `9.5.0` |
| Host 语言 | Kotlin/JVM `2.4.10`，JVM target 17 |
| Android 构建 | Android Gradle Plugin `9.3.0` |
| Android SDK | `compileSdk 36`、Platform 36、Build Tools `36.1.0` |
| Runtime 语言 | Java 17；Android 模块不叠加 Kotlin Android plugin |
| Native 构建 | Android NDK `29.0.14206865`、CMake `4.1.2`、C++17 |
| Native 密码后端 | Mbed TLS `4.1.1` 官方完整归档；仅静态链接 bundled TF-PSA-Crypto `1.1.1` |
| 治理脚本 | Node.js `24.12.0` |
| Host 测试 | Windows x64 与 Ubuntu x64 |
| Runtime 最低平台 | `minSdk 29`；fixture `targetSdk 36`，输入 APK 的 targetSdk 保持不变 |
| Runtime ABI | `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` |
| 签名读取与验证 | Android `apksig` 官方库，版本与 AGP 工具链统一锁定 |
| Host 原子无覆盖发布 | JNA/JNA Platform `5.19.1`；Maven Central，官方 tag `5.19.1`/commit `1a91122853f6ab6f1fb2a4a284a6cf2ed8af0a4d`；仅桥接 Windows `MoveFileExW`/文件 ID 与 Linux `renameat2(RENAME_NOREPLACE)` |
| APK 对齐验证 | Build Tools `36.1.0` 中的 `zipalign` |
| 发布校验和 | SHA-256 |
| SBOM | CycloneDX JSON |

该组合是项目决策，不由 M0-03 实现者重新选择。AGP `9.3.0` 按官方兼容表配套 Gradle `9.5.0` 和 JDK 17；Kotlin/JVM 只用于 Host 模块，Android Runtime 使用 Java，避免 Kotlin Android plugin 与 AGP 版本边界耦合。任何后续升级必须单独提交，包含兼容性与供应链证据。

开发机可以继续保留供其他软件使用的全局 JDK 8，但本项目的终端、Gradle daemon 和 CI 必须显式使用上述 JDK 17，不得修改全局配置来破坏旧项目。

版本来源以 [AGP 9.3.0 release notes](https://developer.android.com/build/releases/agp-9-3-0-release-notes)、[Gradle 9.5.0 release notes](https://docs.gradle.org/9.5.0/release-notes.html)、[Kotlin releases](https://kotlinlang.org/docs/releases.html)、[SDK Build Tools releases](https://developer.android.com/tools/releases/build-tools)、[Android NDK downloads](https://developer.android.com/ndk/downloads)、[CMake 4.1 documentation](https://cmake.org/cmake/help/v4.1/)、[Node.js 24.12.0 release](https://nodejs.org/en/blog/release/v24.12.0) 和 [Eclipse Temurin 17 releases](https://github.com/adoptium/temurin17-binaries/releases) 为准。M0-03 必须把具体下载产物的 SHA-256 写入 verification metadata 或工具链清单；网页中未列出精确 SDK package revision 时，还必须保存官方 SDK manager 的 package-list 输出作为证据。

## 3. 允许来源

依赖来源按优先级：

1. Android/Google、OpenJDK、Gradle、CMake 等项目官方发行渠道；
2. Maven Central 等由构建配置明确允许的标准仓库；
3. 经安全与许可证审查、固定 commit/tag 且校验和已记录的源码依赖。

禁止：

- 动态版本，如 `+`、未固定 snapshot 或浮动分支；
- URL 指向个人网盘、聊天附件或临时 CI artifact；
- 未记录来源与许可证的复制源码或二进制；
- 构建时执行从网络即时下载且未验证的脚本；
- 依赖真实客户 APK、签名凭据或私有构建环境。

## 4. Gradle 与 Maven 治理

- 仅在 settings 级别声明允许仓库，项目模块不得追加仓库。
- 启用 Gradle dependency verification，并提交校验 metadata。
- 使用 version catalog 集中固定版本。
- 提交 Gradle Wrapper JAR 和 properties，并验证官方校验和。
- CI 使用依赖缓存时，cache key 必须包含 lockfile、verification metadata 和 wrapper 版本。
- 发布构建执行离线二次构建，证明依赖已完整解析并锁定。

依赖图变化需要在 PR 中附 before/after 摘要。

## 5. Android SDK、NDK 与 Native

- `compileSdk`、Build Tools、NDK 和 CMake 使用精确版本。
- CI 通过官方 SDK manager package ID 安装，并记录 package 清单。
- NDK 的四 ABI 使用同一 NDK、CMake toolchain 和源 commit 构建。
- 禁止提交从本机 SDK/NDK 复制的二进制。
- 发布记录 Native 编译器版本、flags、符号处理与 stripping 步骤。
- release 与 symbol artifact 分离；symbol artifact 受发布权限控制，但不得包含密钥或 DEX 明文。

M0-04 的设备验收额外固定以下官方 Android 包：

| 用途 | SDK package / 版本 | 官方归档 | 字节数 | 官方 SHA-1 | 项目 SHA-256 |
| --- | --- | --- | ---: | --- | --- |
| API 29 x86_64 | `system-images;android-29;default;x86_64` revision 8 | `x86_64-29_r08-windows.zip` | 689676765 | `e4b798d6fcddff90d528d74ef22ce3dd4a2ca798` | `b5c3fda1f4b4931c30518d342e4ad5f7464945e0cdced3538d4ff2e12f7bf201` |
| API 36 x86_64 | `system-images;android-36;default;x86_64` revision 2 | `x86_64-36_r02.zip` | 844217077 | `829c076e8ff448a336097ae25a355b495ba36e2c` | `e1b9d9fb665001ef27b16e57d8762a2d54aec6bff617e17506edb8676667b9da` |
| Emulator | `emulator` 37.1.11 build 15917651 | `emulator-windows_x64-15917651.zip` | 441926448 | `54fa750822ff462d57e04fc8e98e60f08df2bb61` | `5ff441f3b12ace9b13e9cf96fb0007d233967718652a8110705e995ac47bfeb7` |

机器可读锁位于 `tools/validation/m0-04-android-packages.json`。这些大体积包、解压后的 SDK、AVD、Android 用户目录和缓存只允许位于仓库根的 `.toolchains/android-m0-04/`，该目录被 Git 忽略且不得提交；归档通过 `node tools/validation/verify-m0-04-android-packages.mjs` 同时核对字节数、官方 SHA-1 和项目 SHA-256。

M0-05 的 GitHub Linux/KVM 验收复用上述 API 29 revision 8 与 API 36 revision 2 system image，并固定 Linux Emulator 归档：

| 用途 | SDK package / 版本 | 官方归档 | 字节数 | 官方 SHA-1 | 项目 SHA-256 |
| --- | --- | --- | ---: | --- | --- |
| Linux/KVM Emulator | `emulator` 37.1.11 build 15917651 | `emulator-linux_x64-15917651.zip` | 334378080 | `1b1f78891abf8ec268264356e1365c25519e8379` | `95771e0ae431897b2a4bd2d97fa095f29a8b0624a7b216baf529f9306161c266` |

机器可读锁位于 `tools/validation/m0-05-linux-kvm-packages.json`。GitHub Actions 将固定归档下载到仓库根的 `.toolchains/android-m0-05-ci/`，先核对长度和 SHA-256，再启动 KVM；不得使用 `sdkmanager` 的浮动 `emulator` 或 system-image 版本代替。

M2-07 的 Native 密码后端固定为 Mbed TLS `4.1.1` 官方 release asset `mbedtls-4.1.1.tar.bz2`：`7099934` bytes，SHA-256 `3359a349e23db3d5536fcee032ae7b2ecbfc08972fab643089b5cbf2a375c98c`；annotated tag object 为 `783058d12831aedd3ef57a64577f6f8a88d23bd3`，指向 commit `0a8fda272a5a0abef3b47c91bed37185d5a726b1`。完整归档内 bundled TF-PSA-Crypto 的实际版本为 `1.1.1`。机器锁位于 `tools/validation/m2-07-native-crypto.json`；归档和解压源码只允许位于仓库根 `.toolchains/native-crypto/` 并保持 Git 忽略。准备顺序固定为下载、解包前长度/SHA-256 校验、成员路径校验、空临时目录解压、完整常规文件树 `3927` files/`60515866` bytes/SHA-256 `7c4ba655...d2140` 与 Unix `147` 个限定前缀 symlink（Windows 固定 extractor 跳过时为 `0`）校验、写入双 hash stamp、原子提升；失败清理归档与候选树。`node tools/validation/verify-m2-07-native-crypto.mjs` 必须在任何 Native 配置前核对全部不可变锁字段、完整树、许可证和 bundled 版本；Gradle/CMake 不得自行联网且 CMake 拒绝无 stamp 目录。

项目选择上游双许可证中的 Apache-2.0。构建只加入 `tf-psa-crypto` 子项目并静态链接 AES/GCM/SHA-256/HMAC/HKDF 所需对象；不构建或链接 TLS、X.509、RSA/ECC 或 Android 私有 BoringSSL。上游 4.1.1 release body 把 bundled TF-PSA-Crypto 写成 `1.2.0`，但官方完整归档的 CMake/ChangeLog 均为 `1.1.1`；该差异已在 ADR 0009 记录，锁定归档 bytes 是构建事实来源。

M2-07 Windows Host 向量固定到最终通过 run 自报的 GitHub `windows-2025-vs2026` runtime `ImageVersion=20260803.193.1`；对应发布 ref 为 `win25-vs2026/20260803.193` 的[不可变官方清单](https://github.com/actions/runner-images/blob/win25-vs2026/20260803.193/images/windows/Windows2025-VS2026-Readme.md)：LLVM/`clang-cl` `20.1.8`、Visual Studio Enterprise 2026 `18.8.12023.21`（x64 tools component `18.8.11901.359`、`cl.exe` runtime `19.51.36252`）、Windows SDK `10.0.26100.0`，固定 CMake/Ninja `4.1.2`。workflow 逐项断言 runtime image、编译器与环境版本；镜像滚动时失败关闭并要求独立工具链复核，不静默接受新版。

Ubuntu 24.04 KVM runner 还固定安装 `libpulse0=1:16.1+dfsg1-2ubuntu10.1`，版本记录在同一机器可读锁的 `host_packages` 中。workflow 必须以精确版本安装并在启动 Emulator 前逐字比对 `dpkg-query` 结果，不得接受仓库候选版本漂移。

## 6. GitHub Actions

- 第三方 Action 使用完整 commit SHA 固定，不使用浮动 tag。
- 注释记录对应上游 release tag，便于审计。
- workflow 权限默认只读，按 job 最小化提升。
- fork PR 不获得发布凭据。
- 构建、测试和发布 job 分离；发布只消费已验证 commit 的产物。
- artifact 包含 SHA-256 manifest，下载后再次验证。

## 7. 第三方代码与许可证

每个直接依赖在 `THIRD_PARTY_NOTICES.md` 记录：

- 名称、版本、官方主页和源码 URL；
- 获取方式与校验和；
- 许可证名称与许可证文件；
- 项目内用途；
- 是否打包进 Host 或 Runtime；
- 审查任务/PR。

间接依赖进入 SBOM。许可证不兼容、来源不明或无法固定的依赖不得合并。

## 8. 第三方 Agent Skill

当前不安装外部 Apktool、JADX 或 Handoff Skill。任何第三方 Skill 在使用前必须经过 `.agents/skills/audit-third-party-skill/`，至少审查：

- 许可证；
- 固定 commit 或不可变版本；
- 包含的脚本与二进制；
- 网络访问目标；
- 文件系统与凭据访问；
- 是否读取 keystore、私钥或执行签名；
- 是否修改输入 APK 或扩大任务范围。

审查结论与哈希必须入库。未通过审查的 Skill 不得执行。

## 9. SBOM 与发布来源

每个发布生成 CycloneDX JSON SBOM，覆盖：

- Host JVM 依赖；
- Android Runtime JVM 依赖；
- Native 源码与链接依赖；
- Gradle plugins；
- 随发布包分发的工具或运行库。

发布证据建立以下关联：

```text
git commit
-> locked toolchain
-> dependency verification
-> build logs
-> test evidence
-> release artifacts
-> SHA-256 manifest
-> SBOM
```

每个发布压缩包内包含版本、commit、构建平台与许可证声明，但不包含构建机绝对路径。

## 10. 漏洞响应

发现工具链或依赖漏洞时：

1. 由安全复核者判断项目是否可达及影响范围。
2. 将受影响版本和发布物建立关联。
3. 在独立升级分支更新固定版本与校验 metadata。
4. 运行完整安全负面测试、API/ABI 矩阵和跨平台构建。
5. 更新 SBOM、第三方声明和发布证据。

不得通过关闭依赖验证、降低签名验证或跳过测试来临时解除告警。

## 11. 来源验收证据

M0-03 与每个发布必须保存：

- `java -version` 与 Gradle Wrapper 版本；
- Android SDK package 清单；
- NDK、CMake 和编译器版本；
- dependency verification 结果；
- 直接与间接依赖清单；
- Action commit SHA 清单；
- SBOM 校验结果；
- 发布物 SHA-256；
- Windows 与 Ubuntu 构建命令和退出码。
