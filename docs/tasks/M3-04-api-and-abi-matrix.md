---
id: M3-04
title: "Android API 与 ABI 兼容矩阵"
milestone: M3
status: planned
owner_role: qa-governance-agent
depends_on:
  - M0-03
  - M2-04
  - M2-09
  - M3-01
  - M3-02
  - M3-06
required_skills:
  - validate-protected-apk
security_sensitive: false
---

## Goal

建立从 API 29 到项目锁定最高 API、覆盖四个 Runtime ABI 的完整清单，在可获得的真实进程上执行设备级兼容验收，并把无法提供环境的组合明确标记为 `UNVERIFIED`，以可追溯证据限定 v0.1 的实际兼容声明。

## Background

`minSdk >= 29` 是输入门槛，四 ABI Runtime 是构建能力；两者都不等于每个 API/ABI 组合已经通过设备验证。ADR 0012 规定每个格子必须分类为 `VERIFIED`、`FAILED` 或 `UNVERIFIED`。缺少设备不能被构建成功、其他 ABI 或 runner 标签替代，也不能从最终清单中消失。

## Inputs

- M0-03 在 version catalog 中锁定的最低/最高 Android API 与系统镜像来源。
- M2-04 的四 ABI Runtime、API 29 ARM 双 ABI 设备能力和输入 Native ABI 规则。
- M3-01 fixture catalog。
- M3-02 的关键篡改回归集。
- M3-06/ADR 0012 的验证声明边界。

## Expected Outputs

- 自动生成的完整 API×ABI 清单、设备 inventory 和 selector。
- 每个 `VERIFIED` 格子的安装、启动、组件事件、完整性与负向结果。
- 每个 `UNVERIFIED` 格子的稳定原因与“不得形成正向声明”标记。
- 人类可读兼容表和机器可读 `compatibility-matrix.json`。
- 失败重试、设备真实性、敏感日志和清理规则。

## In Scope

- API 29 到锁定最高 API 的每一个整数 API level 与四个 ABI 的完整枚举。
- `armeabi-v7a`、`arm64-v8a`、`x86`、`x86_64` 真实进程 ABI。
- 当前强制可获得基线：API 29 ARM32/ARM64 真机，以及 API 29/36 x86_64 固定 KVM。
- 单 DEX、多 DEX、自定义工厂和 JNI fixture 的分层验收。
- 同证书成功、异证书失败、容器 tag 篡改失败和 x86 零风险贡献。
- 不可获得组合的显式 `UNVERIFIED` 记录。

## Out of Scope

- API 28 及以下、未发布预览 API 和未来未枚举版本。
- 把 ARM-only 输入转换为 x86 应用。
- 仅靠构建成功、其他 API/ABI、runner 标签或模拟报告代替真实进程证据。
- 为填满矩阵下载未固定系统镜像或使用未授权设备。
- 把 `UNVERIFIED` 解释为兼容失败、已支持或已验证。
- 把 CPU ABI 单独视为风险或篡改信号。

## Implementation Decisions

- 从 `gradle/libs.versions.toml` 读取 `minSdk=29` 与 `compileSdk`，生成闭区间与四 ABI 的全笛卡尔清单；每格只能出现一次。
- 状态仅允许 `VERIFIED`、`FAILED`、`UNVERIFIED`。缺格、重复、未知状态或状态与证据矛盾均失败。
- `VERIFIED` 必须由 `Build.VERSION.SDK_INT`、实际进程 ABI 与 `Build.SUPPORTED_ABIS` 回报并验证设备事实，不接受 workflow matrix 标签代替。
- `UNVERIFIED` 必须有稳定 `reasonCode`、设备事实为 `null`、无正向 fixture 结果，并在 Markdown 中显示“未验证/不作兼容承诺”。
- `FAILED` 必须保留首轮失败证据；最多重试一次。重试通过仍记录 flaky，连续两次结果不一致或最终失败都会阻止任务完成。
- 当前强制 campaign 为 API 29 `armeabi-v7a`/`arm64-v8a` 与 API 29/36 `x86_64`。其他格子默认 `UNVERIFIED`，只有取得固定来源的真实环境并运行同一合同后才能提升为 `VERIFIED`。
- 每个 `VERIFIED` 格子运行 `java-single-dex` 与 `kotlin-multidex`；x86_64 格子额外运行 `custom-factory`；四个强制基线格子均运行适用的 `jni-four-abi`。
- `jni-arm-only` 在已验证 ARM 格子运行成功测试，在已验证 x86/x86_64 格子验证 Host 限制。x86/x86_64 正常设备风险增量必须为 `0`。
- 每个 `VERIFIED` 格子执行异 signer 与认证 tag 篡改负例，证明 payload lookup 为零且无 session 发布。
- 不重复与本任务输入完全相同的长矩阵；可继承既有 artifact 的前提是记录祖先提交、行为输入零差异、证据哈希和适用边界，否则重新执行该格。

## Public Interfaces

- Gradle 入口 `:integration-tests:runApiAbiMatrix`。
- `compatibility-matrix.json` 每格字段为 `apiLevel`、`processAbi`、`status`、`reasonCode`、`deviceFacts`、`fixtureResults`、`retryCount`、`artifactSha256` 和 `evidence`。
- `status` 枚举为 `VERIFIED|FAILED|UNVERIFIED`；`deviceFacts` 对 `UNVERIFIED` 必须为 `null`。
- Markdown 生成器输出 `docs/generated/COMPATIBILITY_RESULTS.md`，并与 JSON 逐格语义一致。
- 设备能力探针 `tools/device-capability-probe`。

## Security Constraints

- 安装测试沿用 M3-01 的外部一次性非生产证书；产品永不签名，测试私钥不提交。
- 设备标识只保存 salted hash，不保存序列号、账号或用户数据。
- 负向用例必须证明 payload 未加载和 session 未发布；日志不得包含密钥、明文 DEX 或完整设备路径。
- `UNVERIFIED` 不得淡化内存防护残余风险，也不得绕过 signer、AEAD 或认证完整性门禁。

## Compatibility Requirements

- 最低输入 API 固定为 29，枚举最高 API 取 M0-03 锁定值。
- Runtime 构建 ABI 固定为 `armeabi-v7a`、`arm64-v8a`、`x86` 和 `x86_64`。
- 发布兼容声明只能引用本矩阵中精确的 `VERIFIED` API/ABI 格子。
- 标准 Java/Kotlin、单/多 DEX、自定义 `Application/AppComponentFactory` 与 JNI 在强制已验证基线有对应覆盖。
- 输入应用 Native ABI 限制与设备验证状态分别呈现。

## Acceptance Criteria

- `./gradlew :integration-tests:runApiAbiMatrix` 退出码为 `0`。
- 从 API 29 到锁定最高 API 的每个 API×四 ABI 格子恰有一条合法记录，不存在缺失或重复格子。
- API 29 `armeabi-v7a`/`arm64-v8a`、API 29 `x86_64` 与 API 36 `x86_64` 四个强制格子均为 `VERIFIED`；任何 `FAILED` 格子都会阻止完成。
- 所有 `VERIFIED` 正向 fixture 完成预期组件事件；异证书与 tag 篡改均在 payload 加载前失败。
- 所有 `UNVERIFIED` 格子给出稳定原因，不带伪造设备事实/产物，并且人类可读报告不作正向兼容声明。
- ARM-only fixture 在已验证 x86/x86_64 格子报告明确限制；正常 x86/x86_64 格子的 ABI 风险增量为 `0`。
- `compatibility-matrix.json` 通过 schema 校验，生成 Markdown 与 JSON 逐格一致，所有引用产物 SHA-256 可复算。
- 测试后设备包、远端临时文件和一次性签名材料均确认不存在。

## Required Tests

- 矩阵生成器的范围、去重、缺失格子、最高 API 读取和 32 格当前锁定范围测试。
- 三状态 schema/语义测试：缺 reason、伪造 deviceFacts、把 `UNVERIFIED` 渲染为支持、未知/重复状态均拒绝。
- 每个 `VERIFIED` 格子的安装、启动、组件事件、签名负向和容器篡改测试。
- 设备 API/进程 ABI 事实校验与 flaky 重试行为测试。
- ARM-only 限制和 x86/x86_64 零风险贡献测试。
- JSON/Markdown 一致性、敏感扫描、临时材料与设备清理负例。

## Required Evidence

- API×ABI 全部格子的状态表；`VERIFIED` 格子的设备事实、命令、退出码和 fixture 结果。
- `UNVERIFIED` 格子的稳定原因与最终“不作兼容承诺”声明。
- 每个已执行格子的日志摘要、测试 APK/报告 SHA-256 和 payload 未加载证据。
- 一次性测试证书、设备 package、远端文件清理与敏感材料扫描结果。
- 缺陷/重试清单、继承证据边界和最终兼容声明。

## Likely Files

- `integration-tests/src/main/kotlin/ApiAbiMatrix.kt`
- `fixtures/android/src/androidTest/`
- `integration-tests/schemas/compatibility-matrix.schema.json`
- `tools/device-capability-probe/`
- `docs/generated/COMPATIBILITY_RESULTS.md`
- `.github/workflows/android-matrix.yml`

## Dependencies and Blockers

M3-06 或 M2-09 未合并、四个强制基线格子缺少真实进程证据、M2-04 缺少 ABI 产物、M3-02 存在未修复认证绕过、出现任一 `FAILED` 格子或 `UNVERIFIED` 被表述为支持时，任务保持 blocked。不得用模拟报告、构建成功或其他 API/ABI 的结果填补证据。

## Agent Handoff Requirements

使用分支 `chore/m3-04-api-abi-matrix`，只处理 Issue #21 并仅创建一个对应 PR。交接必须提交完整格子清单、四个强制基线的设备事实、命令与退出码、重试/缺陷清单、全部哈希、临时证书与设备清理证据，以及逐格兼容声明；所有未验证组合必须明确列出且不得宣传为支持。
