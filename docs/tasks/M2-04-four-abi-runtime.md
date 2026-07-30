---
id: M2-04
title: "四 ABI Runtime 构建与装配"
milestone: M2
status: planned
owner_role: runtime-security-agent
depends_on:
  - M2-02
  - M2-03
required_skills:
  - implement-runtime-protection
  - validate-protected-apk
security_sensitive: true
---

## Goal

以同一受控 Native 源码和工具链生成、测试并装配 `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` 四种 Runtime ABI 产物。

## Background

四 ABI 承诺仅针对加固 Runtime。若输入应用自身只有 ARM Native 库，本项目不把它转换成可在 x86 设备运行的应用；设备 ABI 与输入应用 Native 库不兼容时应在 Host 检查或报告中明确拒绝。

## Inputs

- M2-02 的 Native Runtime 源码与 JNI ABI。
- M2-03 的完整性验证接口。
- M0-03 固定的 Android Gradle Plugin、NDK、CMake 和 Clang 版本。
- M1-01 输出的输入 APK Native ABI 清单。

## Expected Outputs

- `:runtime:native` 的四 ABI 可重现构建配置和裁剪后的 `libah_runtime.so`。
- Runtime ABI 清单、符号归档、尺寸报告和装配测试。
- 每个 ABI 的 Native 单元测试或设备测试证据。
- 输入应用 ABI 与 Runtime ABI 的兼容性判定函数。

## In Scope

- Gradle `abiFilters`、CMake toolchain、编译/链接加固标志和符号分离。
- 四 ABI 库名称、JNI 导出集合和行为一致性检查。
- 输入 APK Native ABI 兼容性检查与清晰报告。
- Release 产物 strip、Debug 符号独立归档及 SHA-256。

## Out of Scope

- 翻译或重编译输入应用的 Native 库。
- 将 ARM-only 应用承诺为 x86 可运行。
- 以 CPU 架构本身作为模拟器、篡改或风险信号。
- 引入输入应用未声明的第三方 Native 依赖。

## Implementation Decisions

- Native 模块路径固定为 `runtime/native`，ABI 策略 Java 源码位于 `runtime/policy/src/main/java` 并使用 Java 17；Android Runtime 模块不得应用 Kotlin Android plugin。
- 固定输出目录分别为 `runtime/native/build/intermediates/stripped_native_libs/release/out/lib/armeabi-v7a/`、`arm64-v8a/`、`x86/` 和 `x86_64/`，每个目录必须包含 `libah_runtime.so`，四个 ABI 缺一即构建失败。
- 四 ABI 使用相同源码、预处理宏和安全逻辑；仅允许 NDK toolchain 自动提供的架构差异，业务分支必须有显式评审。
- Release 固定启用 stack protector、FORTIFY、RELRO、NOW、不可执行栈和隐藏默认符号；JNI 必需符号通过版本脚本白名单导出。
- 输入无 Native 库时四 ABI Runtime 全部装配；输入含 Native 库时，输出只允许设备同时满足 Runtime ABI 与输入应用 ABI，报告不得声称补齐输入应用 ABI。
- `x86` 或 `x86_64` 单独出现时风险分为 `0`，不得触发拒绝、降级或高风险判定。
- 未剥离符号仅作为受控 CI 调试产物，发布包只包含 stripped 库。

## Public Interfaces

- Gradle variant `release` 生成四 ABI AAR。
- `public final class AbiCompatibility`，构造时复制并保存不可变的 `Set<String>`：`runtimeAbis`、`payloadAbis` 和 `compatibleAbis`。
- `public final class AbiCompatibilityPolicy`，通过静态方法 `public static AbiCompatibility evaluate(Set<String> payloadAbis)` 返回兼容结果。
- JSON 报告字段固定为 `runtimeAbis`、`payloadAbis`、`compatibleAbis` 和 `limitations`。

## Security Constraints

- 所有 ABI 使用相同容器校验、密钥处理和 fail-closed 行为。
- 禁止因某一 ABI 测试困难而关闭完整性、sanitizer 或编译加固。
- 符号归档不得包含密钥、明文 DEX 或真实 APK 内容。
- CPU ABI 不是攻击证据；尤其 `x86`/`x86_64` 不得单独计入环境风险。

## Compatibility Requirements

- Runtime 必须构建 `armeabi-v7a`、`arm64-v8a`、`x86` 和 `x86_64`。
- `armeabi-v7a` 与 `x86` 使用 32 位安全长度运算测试，64 位 ABI 使用对应测试。
- API 29 是最低运行边界；最高受支持 API 由 M0-03 的 toolchain 常量确定。
- Java/Kotlin-only 输入 APK 不受输入 Native ABI 限制。
- Runtime 使用 Java 17 实现不改变输入语言兼容范围，标准 Java/Kotlin APK 均须保持支持。

## Acceptance Criteria

- `./gradlew :runtime:native:assembleRelease :runtime:native:test :runtime:native:connectedCheck :runtime:policy:test` 退出码为 `0`，AAR 中四个 ABI 各且仅有一个 `libah_runtime.so`。
- `llvm-readelf` 检查四个库均具备 RELRO、NOW、不可执行栈，导出符号与 JNI 白名单完全相等。
- 四 ABI 的共享容器向量均得到相同 DEX SHA-256 和错误码。
- Java/Kotlin-only fixture 在四 ABI 设备均启动；ARM-only Native fixture 在 x86 设备被报告为输入兼容性限制，不宣称已转换。
- 单独将设备 ABI 设为 `x86` 或 `x86_64` 时，环境风险分不增加且功能测试通过。

## Required Tests

- 四 ABI 构建、AAR 内容、ELF header、导出符号和安全属性测试。
- 32/64 位边界长度、JNI 句柄和容器解析一致性测试。
- Java/Kotlin-only、四 ABI Native 和 ARM-only fixture 的兼容测试。
- x86/x86_64 零风险贡献回归测试。

## Required Evidence

- Gradle、NDK、CMake、Clang 版本及所有命令和退出码。
- 四个 `.so` 的文件尺寸、ELF 属性、导出符号列表和 SHA-256。
- 设备型号/API/ABI 与 fixture 结果矩阵。
- ARM-only 限制和 x86 零风险贡献的 JSON 报告样例。

## Likely Files

- `runtime/native/build.gradle.kts`
- `runtime/native/src/main/cpp/CMakeLists.txt`
- `runtime/native/src/main/cpp/exports.map`
- `runtime/native/src/main/cpp/abi_compatibility.cpp`
- `runtime/native/src/test/`
- `runtime/native/src/androidTest/`
- `runtime/policy/build.gradle.kts`
- `runtime/policy/src/main/java/ah/runtime/AbiCompatibility.java`
- `runtime/policy/src/main/java/ah/runtime/AbiCompatibilityPolicy.java`
- `runtime/policy/src/test/`

## Dependencies and Blockers

M2-02 的 JNI 接口或 M0-03 的 NDK 版本未冻结时不得发布 ABI 产物。若某 ABI 缺少可执行测试环境，可完成编译但任务状态保持 blocked，直到提供设备级证据；不得删除该 ABI 或将架构本身标为风险。

## Agent Handoff Requirements

使用分支 `feat/m2-04-four-abi-runtime`，只处理 Issue `M2-04` 并仅创建一个对应 PR。交接必须提供四 ABI 清单、ELF/符号/哈希证据、完整设备矩阵、命令与退出码、输入 Native ABI 限制及独立安全复核结果；不得修改相邻 Host 任务。
