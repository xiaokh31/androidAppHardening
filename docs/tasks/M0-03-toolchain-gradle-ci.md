---
id: M0-03
title: 固定工具链、Gradle 骨架与持续集成
milestone: M0
status: planned
owner_role: qa-governance-agent
depends_on:
  - M0-02
required_skills:
  - plan-apk-hardening-change
  - validate-protected-apk
security_sensitive: false
---

## Goal

建立可复现、可离线缓存且跨 Windows/Ubuntu 一致的 Gradle 多模块工程与 CI 门禁，为 Host、Android Runtime、fixtures 和治理校验提供唯一工具链基线。

## Background

后续模块同时包含 JVM CLI、Android library、JNI/CMake 和测试工程。版本漂移会使 APK 字节、原生库和验证结果不可比较，因此在 PoC 前必须固定版本、依赖来源、校验元数据和基础任务。

## Inputs

- M0-02 合并后的治理与任务定义。
- 可下载 Maven Central、Google Maven、Gradle distribution、Android SDK/NDK 的受控网络环境。
- 空的业务模块目录；不得带入 APK 后处理或 Runtime 保护实现。

## Expected Outputs

- Kotlin DSL Gradle 根工程和 Wrapper。
- `:host:cli`、`:host:apk-inspector`、`:host:axml`、`:host:container`、`:host:repacker`、`:runtime:bootstrap`、`:runtime:native`、`:runtime:policy`、`:fixtures:android`、`:tools:validation` 十个可编译空骨架。
- Linux 全量检查与 Windows JVM/治理检查工作流。
- 依赖锁、Gradle verification metadata、版本目录和构建缓存策略。

## In Scope

- 固定 JDK、Gradle、Kotlin、AGP、Android SDK、NDK、CMake 和 Node.js 基线。
- 配置编译、单元测试、lint、原生空库构建、文档治理校验。
- 设置依赖仓库白名单、版本锁和 SHA-256 artifact verification。
- 输出不含业务逻辑的 Hello-free 模块骨架及 CI。

## Out of Scope

- ClassLoader PoC、APK fixture 行为、真实 Host/Runtime 实现。
- 发布制品、代码签名、Gradle remote build cache。
- macOS CI 和 Android 设备矩阵。

## Implementation Decisions

- 固定版本为 Temurin JDK `17.0.19+10`、Gradle `9.5.0`、Kotlin/JVM `2.4.10`、AGP `9.3.0`、`compileSdk 36`、Build Tools `36.1.0`、NDK `29.0.14206865`、CMake `4.1.2`、Node.js `24.12.0`。
- Kotlin/JVM plugin 只用于 Host 模块；Android Runtime 骨架使用 Java 17，不应用 Kotlin Android plugin。
- `minSdk` 全局固定为 `29`；生产代码的 Java/Kotlin bytecode target 为 `17`。
- 仓库仅允许 `google()` 与 `mavenCentral()`，`RepositoriesMode.FAIL_ON_PROJECT_REPOS` 生效；禁止 `mavenLocal()`、动态版本和 snapshot。
- Gradle Wrapper distribution 使用 `-bin` 包并记录 SHA-256；依赖锁覆盖所有可解析 configuration，verification mode 为 strict。
- CI 使用 `ubuntu-24.04` 执行 `clean check lint` 与四 ABI native configure/build，使用 `windows-2025` 执行 JVM、治理和路径兼容检查；第三方 Actions 固定到完整 commit SHA。
- 分支名固定为 `feat/m0-03-toolchain-gradle-ci`，Issue 标题固定为 `[M0-03] Toolchain, Gradle, and CI`，仅允许一个关联 PR。

## Public Interfaces

- Unix 入口：`./gradlew`；Windows 入口：`.\gradlew.bat`。
- 基线验证任务：`clean check lint verifyGovernance`。
- 模块坐标与 `docs/ARCHITECTURE.md` 一致：`:host:cli`、`:host:apk-inspector`、`:host:axml`、`:host:container`、`:host:repacker`、`:runtime:bootstrap`、`:runtime:native`、`:runtime:policy`、`:fixtures:android`、`:tools:validation`。
- 版本唯一来源：`gradle/libs.versions.toml`。

## Security Constraints

- CI 使用最小 `contents: read` 权限，不接收仓库 secrets，不上传输入样本或签名材料。
- Gradle Wrapper、依赖和 Actions 必须固定并验证来源；校验失败不得降级为宽松模式。
- 构建日志不得打印环境凭据、用户目录或完整环境变量。
- fixture 只能是仓库自有源码生成的合成 APK。

## Compatibility Requirements

- 五个 Host 模块在 Windows x64 和 Ubuntu x64 的 JDK 17 上构建。
- Android 模块以 `compileSdk 36` 构建并支持 API 29 及以上；fixture 使用 `targetSdk 36`。
- CMake 配置必须覆盖 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64`，本任务只生成无业务能力的空符号库。
- 路径处理不得依赖 shell 专属语法或大小写不敏感文件系统。

## Acceptance Criteria

1. Ubuntu 执行 `./gradlew --no-daemon clean check lint verifyGovernance` 退出码为 `0`。
2. Windows 执行 `.\gradlew.bat --no-daemon clean check verifyGovernance` 退出码为 `0`。
3. `./gradlew projects` 输出十个规定模块且无额外业务模块。
4. `./gradlew :runtime:native:assemble` 生成四个规定 ABI 的共享库，`readelf`/`llvm-readobj` 证实架构匹配。
5. 删除或篡改一个 verification metadata checksum 后，依赖解析以非零退出；恢复后通过。
6. 仓库扫描不存在动态版本、snapshot、`mavenLocal()`、未固定 Actions 标签或业务实现。
7. CI 的 Linux 与 Windows required checks 在唯一 PR 上均为成功。

## Required Tests

- Gradle Wrapper SHA-256 验证和 `./gradlew --version` 版本断言。
- 十模块 compile/test/lint smoke test。
- 四 ABI CMake configure/build smoke test。
- 依赖校验负向测试和 lockfile 一致性测试。
- Windows/Ubuntu 路径、换行和治理脚本等价测试。

## Required Evidence

- 两个平台的 OS、JDK、Gradle、Node.js、SDK、NDK、CMake 版本。
- 所有验收命令、退出码和 CI run 链接。
- Wrapper、版本目录、lockfile、verification metadata 和四 ABI 空库的 SHA-256。
- 提交 SHA、Issue 与唯一 PR 链接；依赖下载来源清单。

## Likely Files

- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle/libs.versions.toml`
- `gradle/verification-metadata.xml`
- `gradle/wrapper/`
- `.github/workflows/`

## Dependencies and Blockers

- M0-02 的治理校验入口必须稳定。
- 任一固定工具版本不可从允许的官方来源取得时，提交 blocked 交接，不擅自升级或换源。
- 版本组合发生已证实的不兼容时，由 `/root` 通过 ADR 修订后再继续。

## Agent Handoff Requirements

- 本任务固定使用分支 `feat/m0-03-toolchain-gradle-ci`、同编号 Issue 和一个 PR。
- 完成状态必须提供命令、退出码、平台、CI 链接及全部构建产物 SHA-256。
- worker 不修改根 `HandOff.md`，不在骨架中加入相邻 PoC 或业务逻辑。
- 工具版本或依赖来源冲突必须以 blocked 交接上报，不自行改变技术基线。
