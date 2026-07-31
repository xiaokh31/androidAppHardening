---
id: M3-01
title: "Android 合成测试 Fixtures"
milestone: M3
status: planned
owner_role: qa-governance-agent
depends_on:
  - M1-06
  - M2-04
required_skills:
  - validate-protected-apk
security_sensitive: false
---

## Goal

建立完全可公开、可重复构建的 Android fixture 集合和端到端测试驱动器，覆盖 v0.1 支持的 Java/Kotlin、单/多 DEX、自定义启动组件、四 ABI Runtime 与输入 Native ABI 限制。

## Background

测试不得依赖真实客户 APK、商业应用、生产证书或不可再生成的二进制。产品输出始终未签名；只有集成测试驱动器可在被忽略的构建输出目录生成一次性非生产证书，并在产品外部签名后执行安装测试。

## Inputs

- M1-06 的 CLI 与 JSON 报告。
- M2-04 的四 ABI Runtime 产物和 ABI 兼容规则。
- 产品需求、兼容矩阵与测试策略。
- Android SDK 中固定版本的 `apksigner`、`adb` 和 emulator 工具。

## Expected Outputs

- 具备源码的合成 fixture Gradle 工程。
- 版本化 `fixtures/catalog.yaml` 与机器可读预期结果。
- 构建、加固、外部测试签名、安装、启动、断言和清理驱动器。
- 输入/输出哈希、设备信息和测试报告。

## In Scope

- `java-single-dex`、`kotlin-single-dex`、`kotlin-multidex`、`custom-application`、`custom-factory`、`startup-provider`、`multi-process`、`jni-four-abi` 和 `jni-arm-only` 九个 fixture。
- 每个 fixture 的确定性成功信号、组件初始化顺序和原始 APK SHA-256。
- Host 输入只读验证和未签名输出验证。
- 外部测试签名与设备安装仅属于集成测试驱动器。

## Out of Scope

- 真实客户 APK、闭源第三方 APK、商店下载包和生产签名材料。
- AAB、Split APK、Flutter、Unity、React Native、热修复和已有加固壳的运行支持。
- 在产品 CLI 中加入签名能力。
- 把 ARM-only fixture 转换为 x86 应用。

## Implementation Decisions

- fixture 源码位于 `fixtures/android/src/<fixtureFlavor>/`，九个固定 product flavors 生成独立 APK，构建产物只进入模块 `build/`；仓库不提交 APK、DEX、keystore 或私钥。
- `catalog.yaml` 为每个 fixture 固定记录 `id`、语言、DEX 模式、启动定制、payload ABI、预期组件事件和预期兼容结果。
- 测试驱动顺序固定为 build、记录输入哈希、运行产品、复核输入哈希不变、检查输出未签名、在外部生成一次性证书、外部签名、安装、启动、断言、卸载、删除证书。
- 一次性证书固定生成到 `integration-tests/build/test-signing/`，每次运行重新生成，别名只存在于测试驱动器进程；该目录必须被 Git 忽略。
- `jni-arm-only` 在 ARM 设备应运行，在 x86/x86_64 设备只验证报告明确限制，不要求安装成功。
- fixture 的成功信号通过应用内只读事件序列和 instrumentation 断言获取，不解析不稳定的人类日志文本。

## Public Interfaces

- `fixtures/catalog.yaml` 及其 schema。
- `FixtureDescriptor`，字段为 `id`、`inputApk`、`expectedEvents`、`payloadAbis` 和 `expectedOutcome`。
- Gradle 入口 `:fixtures:android:assembleFixtures` 与 `:integration-tests:runFixtureMatrix`。
- 测试结果 `integration-tests/build/reports/fixture-results.json`。

## Security Constraints

- 产品代码和产品 CLI 永不签名，永不接收私钥、keystore、alias 或密码。
- 集成测试只能使用运行时生成的一次性非生产证书；生成目录必须被忽略，测试结束后删除，提交扫描不得发现私钥或 keystore。
- fixture 不含真实应用代码、证书、网络凭据、用户数据或明文客户 DEX。
- 测试日志不得输出一次性证书密码、完整设备路径或应用私有数据。

## Compatibility Requirements

- fixture 的 `minSdk` 不低于 29。
- 支持矩阵覆盖 Java/Kotlin、单/多 DEX、自定义 `Application/AppComponentFactory`、Provider、独立进程和 JNI。
- Runtime 覆盖 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64`。
- x86/x86_64 设备本身不是风险信号；对应正常 fixture 不得因 ABI 被拒绝。

## Acceptance Criteria

- `./gradlew :fixtures:android:assembleFixtures :integration-tests:test` 退出码为 `0`。
- 九个 fixture 均可从干净 checkout 重建，连续两次构建的输入 APK SHA-256 相同。
- 每次加固前后输入 APK SHA-256 不变，产品输出均通过未签名检查。
- 支持场景经外部一次性证书签名后按预期启动并产生完整事件序列；`jni-arm-only` 在 x86/x86_64 上产生明确兼容限制。
- 测试结束后 `integration-tests/build/test-signing/` 不再包含密钥文件，仓库敏感材料扫描结果为零。

## Required Tests

- catalog schema、fixture ID 唯一性和预期事件完整性测试。
- 每个 fixture 的构建、输入只读、未签名输出、外部签名、安装、启动与卸载测试。
- 自定义工厂、早期 Provider、多进程和多 DEX 顺序回归测试。
- 四 ABI 与 ARM-only 限制测试。

## Required Evidence

- 构建与运行命令、退出码、JDK/Gradle/SDK 和设备 API/ABI。
- 九个输入 APK、未签名输出 APK、外部签名测试 APK 与结果报告 SHA-256。
- 输入哈希前后对照、组件事件序列和 ABI 兼容结果。
- 一次性证书生成/删除记录和敏感材料扫描结果，不包含私钥内容。

## Likely Files

- `fixtures/android/src/`
- `fixtures/android/catalog.yaml`
- `fixtures/android/catalog.schema.json`
- `integration-tests/build.gradle.kts`
- `integration-tests/src/test/`
- `integration-tests/src/androidTest/`
- `integration-tests/src/main/kotlin/FixtureDriver.kt`

## Dependencies and Blockers

M1-06 CLI 或 M2-04 Runtime 装配格式未稳定时不得冻结 fixture 驱动器。若测试环境缺少某 ABI 的设备，可完成 fixture 构建但任务保持 blocked，直到设备级证据齐全。

## Agent Handoff Requirements

使用分支 `chore/m3-01-android-fixtures`，只处理 Issue `M3-01` 并仅创建一个对应 PR。交接包必须列出九个 fixture、事件契约、全部命令与退出码、设备矩阵、前后哈希、测试报告 SHA-256 和临时证书清理证据；不得提交生成 APK 或任何密钥。
