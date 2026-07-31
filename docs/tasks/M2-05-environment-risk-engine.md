---
id: M2-05
title: "环境风险信号引擎"
milestone: M2
status: planned
owner_role: runtime-security-agent
depends_on:
  - M2-01
  - M2-03
  - M2-04
required_skills:
  - implement-runtime-protection
security_sensitive: true
---

## Goal

实现可测试、可审计的本地环境风险信号收集与评分引擎，为 M2-06 提供分级防护输入，同时避免把正常设备特征误当成篡改证据。

## Background

反调试、模拟环境和注入检测只能提高动态分析成本，不能形成绝对防御。签名或容器完整性失败由 M2-03 独立 fail closed；环境评分不得替代密码学完整性判断。

## Inputs

- M2-01 的进程启动生命周期。
- M2-03 的独立完整性结果。
- `docs/THREAT_MODEL.md` 中允许采集的本地信号。
- M2-04 的 ABI 兼容规则。

## Expected Outputs

- `:runtime:policy` 中的 Java 17 纯函数评分器、Android/Native 信号采集器和版本化 `RiskReportV1`。
- 固定信号权重、上限、阈值和去重规则。
- 无敏感路径的诊断摘要。
- 真机、模拟器、调试器和注入测试 fixture。

## In Scope

- `TracerPid`、JDWP/debugger 连接、应用 debuggable 标志和已加载映射中的已知注入框架家族信号。
- 模拟环境的多信号组合，但不以 CPU ABI 单独计分。
- 信号采集超时、权限失败和不可用状态。
- `LOW`、`MEDIUM`、`HIGH` 三档结果和 M2-06 消费接口。

## Out of Scope

- 远程风控、设备指纹、用户身份画像和持久化跨设备跟踪。
- 仅因 root、模拟器或 x86 设备而拒绝合法应用。
- 把环境风险写成签名/容器损坏结论。
- 宣称无法绕过或永久阻止调试。

## Implementation Decisions

- 策略模块路径固定为 `runtime/policy`，Java 源码位于 `src/main/java`；Native 信号采集位于 `runtime/native/src/main/cpp`。Android Runtime 模块不得应用 Kotlin Android plugin。
- 固定计分：`TracerPid > 0` 为 60；JDWP 已连接为 50；应用 debuggable 为 20；每个已知注入框架家族为 40，映射类信号累计上限 80；模拟环境组合信号累计上限 30。
- `x86`、`x86_64`、`armeabi-v7a` 或 `arm64-v8a` 单独出现均为 0 分；ABI 不得作为注入框架或模拟器信号的替代项。
- 总分封顶 100；`LOW` 为 0–39，`MEDIUM` 为 40–79，`HIGH` 为 80–100。相同家族的重复证据只计一次。
- 策略动作固定映射为 `LOW -> ALLOW`、`MEDIUM -> DEGRADE`、`HIGH -> DEGRADE`；环境信号不得产生 `DENY`，不得跳过或降低 signer、容器和完整性校验。
- 信号读取必须在 50 ms 总预算内完成；超时或权限不足记录为 `UNAVAILABLE` 且计 0 分，不能假装安全或直接拒绝。
- v0.1 的环境结果只驱动 M2-06 的分级成本控制和审计事件，不单独终止应用；签名/容器失败仍由 M2-03/M2-02 fail closed。
- 报告仅保存信号 ID、可用性、布尔命中和分值，不保存 `/proc` 原文、进程列表、完整映射名或设备唯一标识。

## Public Interfaces

- `public enum RiskLevel { LOW, MEDIUM, HIGH }`
- `public enum RiskAction { ALLOW, DEGRADE }`
- `public enum RiskSignalId { TRACER, JDWP, DEBUGGABLE, INSTRUMENTATION_MAPPING, EMULATOR_COMPOSITE }`
- `public final class RiskSignal`，不可变字段为 `RiskSignalId id`、`SignalState state` 和 `int score`。
- `public final class RiskReportV1`，构造时复制不可变的 `List<RiskSignal>`，并公开 `version`、`level`、`action` 与 `totalScore` 只读访问器；`action` 必须由固定等级映射计算，调用方不得注入任意动作。
- `public final class EnvironmentRiskEngine`，通过静态方法 `public static RiskReportV1 evaluate(ApplicationInfo applicationInfo)` 评估风险并禁止实例化；其余信号只读取当前进程 `/proc`、`Debug`/`Build` 公开信息和已加载映射，不要求 `Context`。

## Security Constraints

- 权重、阈值和信号 ID 必须集中定义并纳入完整性保护，不接受未认证 Manifest 覆盖。
- 解析 `/proc` 和映射信息时执行长度上限、字符过滤和异常隔离。
- 日志不得包含设备标识、完整进程/路径列表、密钥、DEX 或证书。
- 文档和 API 注释必须说明这些检测可被具有进程控制能力的攻击者绕过，只用于提高成本。

## Compatibility Requirements

- API 29 及以上；不可用信号必须安全降级为 `UNAVAILABLE`。
- 在真机、Android 官方模拟器、x86/x86_64 与 ARM 设备上都可运行。
- 仅使用公开 Android API、Framework 传入的 `ApplicationInfo` 和受限只读的本进程信息；不得通过 hidden API 获取早期 `Context`。
- 非 debuggable 的正常 x86/x86_64 模拟器不得因 ABI 单项进入 `MEDIUM` 或 `HIGH`。
- Runtime 使用 Java 17 实现不改变输入语言兼容范围，标准 Java/Kotlin APK 均须保持支持。

## Acceptance Criteria

- `./gradlew :runtime:native:test :runtime:policy:test :runtime:policy:connectedCheck` 退出码为 `0`。
- 表驱动测试覆盖所有分值边界、累计上限、去重和 `UNAVAILABLE`，结果与固定规则完全一致。
- 表驱动测试证明所有 `LOW` 报告的动作为 `ALLOW`，所有 `MEDIUM`/`HIGH` 报告的动作为 `DEGRADE`，且没有环境信号路径能生成拒绝动作。
- 仅切换 ABI 为 x86/x86_64 的样本总分变化为 `0`；模拟组合信号即使全部命中也不超过 30。
- 注入映射加调试器场景达到 `HIGH` 并输出 `DEGRADE`，供后继 M2-06 消费；单个 debuggable 信号保持 `LOW/ALLOW`。本任务不声称已执行 M2-06 的内存控制。
- 1000 次评估的单次墙钟时间均不超过 50 ms，报告中不存在原始 `/proc` 内容或完整敏感路径。

## Required Tests

- 纯评分函数的表驱动、边界、去重、封顶和顺序无关测试。
- 信号采集超时、文件不可读、格式损坏和超长输入测试。
- 真机、官方模拟器、四 ABI、debuggable 和注入测试进程的 instrumentation 测试。
- x86/x86_64 零分贡献与环境风险不触发完整性失败的回归测试。

## Required Evidence

- 命令、退出码、设备/API/ABI、测试进程配置和性能统计。
- 每种信号组合的输入、得分、等级、`RiskAction` 和预期 M2-06 策略表。
- 报告脱敏扫描与 50 ms 预算结果。
- AAR、测试报告及样例 `RiskReportV1` 的 SHA-256。

## Likely Files

- `runtime/policy/build.gradle.kts`
- `runtime/policy/src/main/java/ah/runtime/risk/EnvironmentRiskEngine.java`
- `runtime/policy/src/main/java/ah/runtime/risk/RiskLevel.java`
- `runtime/policy/src/main/java/ah/runtime/risk/RiskAction.java`
- `runtime/policy/src/main/java/ah/runtime/risk/RiskSignal.java`
- `runtime/policy/src/main/java/ah/runtime/risk/RiskReportV1.java`
- `runtime/native/src/main/cpp/risk_signals.cpp`
- `runtime/policy/src/test/`
- `runtime/policy/src/androidTest/`

## Dependencies and Blockers

M2-03 的完整性结果边界未明确时不得把风险信号接入启动决策。若某信号需要隐藏 API、额外敏感权限或跨应用扫描，必须移除该信号或提交安全评审，不得扩大权限。

## Agent Handoff Requirements

使用分支 `feat/m2-05-environment-risk-engine`，只处理 Issue `M2-05` 并仅创建一个对应 PR。交接必须附权重表、误报边界、性能数据、四 ABI 结果、全部命令与退出码、产物 SHA-256 和绕过残余风险；明确证明 x86/x86_64 单独不计分。
