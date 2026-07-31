---
id: M3-04
title: "Android API 与 ABI 兼容矩阵"
milestone: M3
status: planned
owner_role: qa-governance-agent
depends_on:
  - M0-03
  - M2-04
  - M3-01
  - M3-02
required_skills:
  - validate-protected-apk
security_sensitive: false
---

## Goal

建立从 API 29 到项目锁定最高 API、覆盖四个 Runtime ABI 的设备级兼容矩阵，并以可追溯证据限定 v0.1 的实际验证范围。

## Background

`minSdk >= 29` 是输入门槛，不等于未来 Android 版本自动受保证。每次发布只声明已执行矩阵内的 API/ABI；缺少某个格子的设备证据即不能宣称该组合已验证。

## Inputs

- M0-03 在 version catalog 中锁定的最高 Android API。
- M2-04 的四 ABI Runtime 和输入 Native ABI 规则。
- M3-01 fixture catalog。
- M3-02 的关键篡改回归集。

## Expected Outputs

- 自动生成的 API×ABI 测试清单和 device selector。
- 每个矩阵格子的安装、启动、组件事件、完整性与负向结果。
- 人类可读兼容表和机器可读 `compatibility-matrix.json`。
- 失败重试、设备真实性和日志归档规则。

## In Scope

- API 29 到锁定最高 API 的每一个整数 API level。
- `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` 四种真实进程 ABI。
- 单 DEX、多 DEX、自定义工厂和 JNI fixture 的分层矩阵。
- 同证书成功、异证书失败、容器 tag 篡改失败和 x86 零风险贡献。

## Out of Scope

- API 28 及以下、未发布预览 API 和未来未验证版本。
- 把 ARM-only 输入转换为 x86 应用。
- 仅靠构建成功代替设备启动证据。
- 把 CPU ABI 单独视为风险或篡改信号。

## Implementation Decisions

- 从 `gradle/libs.versions.toml` 读取 `minSdk=29` 与 `compileSdk`，生成闭区间内全部 API level；禁止手工跳过中间版本。
- 全笛卡尔矩阵在每个 API×ABI 格子运行 `java-single-dex` 与 `kotlin-multidex`；每个 API 的 x86_64 格子额外运行 `custom-factory`，每个 ABI 在 API 29 和最高 API 额外运行 `jni-four-abi`。
- 每个格子必须由 `Build.VERSION.SDK_INT` 与 `Build.SUPPORTED_ABIS` 回报并验证实际值，不接受 runner 标签代替设备事实。
- 每个格子失败最多重试一次；首次失败仍保留并标为 flaky，连续两次结果不同则矩阵失败。
- `jni-arm-only` 只在 ARM 格子运行成功测试，在 x86/x86_64 格子验证 Host 报告限制。
- x86/x86_64 正常设备风险增量必须为 0；只有真实调试/篡改信号可改变风险结果。

## Public Interfaces

- Gradle 入口 `:integration-tests:runApiAbiMatrix`。
- `compatibility-matrix.json` 字段为 `apiLevel`、`processAbi`、`fixtureId`、`deviceIdHash`、`result`、`retryCount`、`artifactSha256` 和 `evidence`。
- Markdown 生成器输出 `docs/generated/COMPATIBILITY_RESULTS.md`。
- 设备能力探针 `tools/device-capability-probe`。

## Security Constraints

- 安装测试沿用 M3-01 的外部一次性非生产证书；产品永不签名，测试私钥不提交。
- 设备标识只保存 salted hash，不保存序列号、账号或用户数据。
- 负向用例必须证明 payload 未加载，日志不得包含密钥、明文 DEX 或完整设备路径。
- 兼容矩阵不能淡化内存防护可绕过的残余风险。

## Compatibility Requirements

- 最低 API 固定为 29，最高 API 取 M0-03 锁定值。
- Runtime ABI 固定为 `armeabi-v7a`、`arm64-v8a`、`x86` 和 `x86_64`。
- 标准 Java/Kotlin、单/多 DEX、自定义 `Application/AppComponentFactory` 与 JNI 均有对应覆盖。
- 输入应用 Native ABI 限制在结果中独立呈现。

## Acceptance Criteria

- `./gradlew :integration-tests:runApiAbiMatrix` 退出码为 `0`。
- 从 API 29 到最高 API 的每个 API×四 ABI 格子都有设备事实、fixture 结果和证据链接，不存在缺失格子。
- 所有正向 fixture 完成预期组件事件；异证书与 tag 篡改均在 payload 加载前失败。
- ARM-only fixture 在 x86/x86_64 报告明确限制；正常 x86/x86_64 格子的 ABI 风险增量为 `0`。
- `compatibility-matrix.json` 通过 schema 校验，生成 Markdown 与 JSON 数据一致，所有引用产物 SHA-256 可复算。

## Required Tests

- 矩阵生成器的范围、去重、缺失格子和最高 API 读取测试。
- 每个格子的安装、启动、事件、签名负向和容器篡改测试。
- 设备 API/ABI 事实校验与 flaky 重试行为测试。
- ARM-only 限制和 x86/x86_64 零风险贡献测试。

## Required Evidence

- API×ABI×fixture 完整结果表、设备事实、命令与退出码。
- 每格日志摘要、测试 APK/报告 SHA-256 和 payload 未加载证据。
- 一次性测试证书清理与敏感材料扫描结果。
- 缺陷/重试清单和最终兼容声明。

## Likely Files

- `integration-tests/src/main/kotlin/ApiAbiMatrix.kt`
- `fixtures/android/src/androidTest/`
- `integration-tests/schemas/compatibility-matrix.schema.json`
- `tools/device-capability-probe/`
- `docs/generated/COMPATIBILITY_RESULTS.md`
- `.github/workflows/android-matrix.yml`

## Dependencies and Blockers

缺少任一 API×ABI 设备、M2-04 缺少 ABI 产物或 M3-02 存在未修复认证绕过时，任务保持 blocked。不得用模拟报告、构建成功或其他 ABI 的结果填补缺失格子。

## Agent Handoff Requirements

使用分支 `chore/m3-04-api-abi-matrix`，只处理 Issue `M3-04` 并仅创建一个对应 PR。交接必须提交完整矩阵、设备事实、命令与退出码、重试/缺陷清单、全部哈希、临时证书清理证据和兼容声明；明确记录任何未验证组合。
