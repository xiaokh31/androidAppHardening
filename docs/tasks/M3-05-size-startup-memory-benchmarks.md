---
id: M3-05
title: "大小、启动与内存基准"
milestone: M3
status: planned
owner_role: qa-governance-agent
depends_on:
  - M1-06
  - M2-04
  - M2-06
  - M3-01
required_skills:
  - validate-protected-apk
security_sensitive: false
---

## Goal

量化加固带来的 APK 增量、Host 处理耗时、冷启动延迟和进程内存开销，并以固定预算形成 v0.1 发布门禁。

## Background

“APK 大小优化”只表示控制加固增量，不保证输出小于输入。基准必须同时报告原始值、加固值、绝对增量和百分比，且不得用单次测量或不同设备结果作结论。

## Inputs

- M1-06 可发布 CLI。
- M2-04 四 ABI Runtime Release 产物。
- M3-01 的 `java-single-dex`、`kotlin-multidex` 和 `jni-four-abi` fixture。
- 锁定的 benchmark 环境与测试工具版本。

## Expected Outputs

- 可重复执行的 Host 与 Android benchmark harness。
- 原始/加固大小、处理耗时、冷启动和 PSS 的原始样本与统计汇总。
- 固定预算判定和趋势报告。
- `benchmark-results.json`、环境描述和 SHA-256 manifest。

## In Scope

- 未签名输出 APK 与输入 APK 的文件大小差值。
- Windows/Ubuntu Host 处理时间与峰值 RSS。
- Android 冷启动 median/p95 与启动后稳定 PSS median。
- `LOW` 和 `HIGH` 风险策略的额外启动成本。

## Out of Scope

- 承诺输出 APK 比输入更小。
- 网络下载、外部签名耗时、安装耗时和应用业务网络性能。
- 用 debug build 或开启 profiler 的数值作为发布门禁。
- 为通过预算而关闭完整性、四 ABI 或内存保护。

## Implementation Decisions

- 固定三类样本：`java-single-dex`、`kotlin-multidex`、`jni-four-abi`；输入和加固 APK 均使用 Release 配置，测量对象为未签名字节。
- 每个 Host 场景预热 3 次、测量 10 次；Android 冷启动和 PSS 预热 5 次、测量 30 次，报告 median 与 p95。
- 冷启动使用 Macrobenchmark 并在每次样本前 force-stop；PSS 在启动事件完成后 5 秒采集 `dumpsys meminfo`。
- 发布预算固定为：APK 增量不超过 `max(12 MiB, inputSize × 15%)`；冷启动 median 增量不超过 300 ms、p95 增量不超过 500 ms；稳定 PSS median 增量不超过 32 MiB。
- Host 对 100 MiB 合成输入的处理 median 不超过 60 秒、峰值 RSS 不超过 1 GiB；输入文件前后 SHA-256 必须相同。
- 高风险策略的额外延迟需单列，M2-06 固定抖动仍必须落在 20–50 ms，不从冷启动总开销中剔除。

## Public Interfaces

- Gradle 入口 `:benchmarks:host:jmh` 与 `:benchmarks:android:connectedBenchmarkAndroidTest`。
- `benchmark-results.json` 字段为 `fixtureId`、`environmentId`、`metric`、`samples`、`median`、`p95`、`baseline`、`delta`、`budget` 和 `pass`。
- 环境文件 `benchmarks/environment.json` 与汇总 `build/reports/benchmark-summary.md`。
- 失败时进程退出码固定为非零且列出超预算 metric。

## Security Constraints

- benchmark 不关闭签名/容器完整性、AEAD、四 ABI 或内存控制。
- Android 安装仅使用 M3-01 的外部一次性非生产证书；产品永不签名，证书私钥不提交。
- 报告不记录设备序列号、用户绝对路径、密钥或明文 DEX。
- 结果必须同时披露成本与残余安全边界，不把性能数据表述为绝对防御能力。

## Compatibility Requirements

- Host benchmark 分别在声明支持的 Windows 和 Ubuntu 环境执行。
- Android benchmark 至少在 API 29 ARM64 与最高受支持 API x86_64 的固定 reference device profile 执行。
- 四 ABI 库尺寸分别报告；x86/x86_64 不因架构本身触发高风险策略。
- 三类 fixture 的基线与加固版本来自同一 commit 和 toolchain。

## Acceptance Criteria

- `./gradlew :benchmarks:host:jmh :benchmarks:android:connectedBenchmarkAndroidTest` 退出码为 `0`。
- 三类 fixture 的 APK 增量、冷启动 median/p95 和稳定 PSS median 均不超过固定预算。
- 100 MiB 合成输入在 Windows/Ubuntu 的 Host median 与峰值 RSS 均达标，输入哈希前后相同。
- 每个 metric 具备规定样本数、原始样本、环境描述、基线、增量和 pass 判定，连续两次汇总差异在 10% 内。
- 报告明确写明大小只控制增量、不保证输出更小，且未隐藏高风险策略的额外开销。

## Required Tests

- 统计器、单位换算、预算边界、缺失样本和异常值标注测试。
- 三类 fixture 的 Host 大小/耗时/RSS benchmark。
- 两个 reference profile 的冷启动、PSS、LOW/HIGH 策略 benchmark。
- x86/x86_64 不自动进入高风险以及输入只读回归测试。

## Required Evidence

- 所有命令、退出码、commit、toolchain、OS 和设备 profile。
- 原始样本、统计结果、预算判定和两次重复运行差异。
- 输入/输出 APK、Runtime ABI 库和结果文件 SHA-256。
- 临时测试证书清理、敏感信息扫描与已知测量限制。

## Likely Files

- `benchmarks/host/build.gradle.kts`
- `benchmarks/host/src/jmh/`
- `benchmarks/android/build.gradle.kts`
- `benchmarks/android/src/main/java/`
- `benchmarks/environment.json`
- `benchmarks/host/src/main/resources/benchmark-results.schema.json`
- `.github/workflows/benchmarks.yml`

## Dependencies and Blockers

M1-06 或 M2-04 尚未形成 Release 候选产物时不得建立发布基线。任一安全控制导致预算失败时任务保持 blocked，并提交可量化优化任务；不得删除安全控制或放宽预算而不经 ADR 与安全评审。

## Agent Handoff Requirements

使用分支 `chore/m3-05-performance-benchmarks`，只处理 Issue `M3-05` 并仅创建一个对应 PR。交接必须包含环境、原始样本、统计方法、命令与退出码、预算结果、全部 SHA-256、波动说明和临时证书清理证据；明确 APK 大小不保证小于输入。
