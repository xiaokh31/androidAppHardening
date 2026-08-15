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
  - M3-07
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
- M3-07 与 ADR 0014 固定的真实冷启动/隔离 HIGH 增量测量边界。

## Expected Outputs

- 可重复执行的 Host 与 Android benchmark harness。
- 输入/未签名输出/外部签名输出大小、处理耗时、两个冷启动终点、峰值 PSS、稳定 PSS 和 Native heap 峰值的原始样本与统计汇总。
- bootstrap、实际注入 ABI、四 ABI 全集基准、container metadata 和其余 ZIP 结构开销的可复算分项清单。
- 固定预算判定和趋势报告。
- `benchmark-results.json`、环境描述和 SHA-256 manifest。

## In Scope

- 输入 APK、未签名输出 APK 和使用 M3-01 同一张一次性证书外部签名后的测试副本大小，以及三者差值。
- bootstrap DEX、当前设备实际注入 Runtime ABI、四 ABI Runtime 全集基准、container metadata 与其余 ZIP 结构开销的分项增量。
- Windows/Ubuntu Host 处理时间与峰值 RSS。
- Android 从 process start 到 `Application.onCreate`、从 process start 到首个测试 Activity 可交互的 P50/P95。
- 启动窗口峰值 PSS、启动窗口 Native heap 峰值和启动完成 5 秒后的稳定 PSS 的 P50/P95。
- 未修改生产策略的 `LOW` 真实冷启动成本，以及通过测试专用边界测得、单独报告的 `HIGH` profile 增量成本。

## Out of Scope

- 承诺输出 APK 比输入更小。
- 网络下载、外部签名耗时、安装耗时和应用业务网络性能。
- 用 debug build 或开启 profiler 的数值作为发布门禁。
- 为通过预算而关闭完整性、四 ABI 或内存保护。

## Implementation Decisions

- 固定三类样本：`java-single-dex`、`kotlin-multidex`、`jni-four-abi`；输入和加固 APK 均使用 Release 配置，测量对象为未签名字节。
- 每个 Host 场景预热 3 次、测量 10 次；Android 两个启动终点和三项内存指标均预热 5 次、测量 30 次，报告 P50 与 P95。
- 冷启动使用 Macrobenchmark 并在每次样本前 force-stop；instrumentation 记录 `Application.onCreate` 与首个测试 Activity 可交互的单调时钟事件。每次启动期间轮询 `dumpsys meminfo` 得到 peak PSS 与 Native heap peak，并在可交互事件后 5 秒采集稳定 PSS。
- 发布预算固定为：未签名 APK 增量不超过 `max(12 MiB, inputSize × 15%)`；两个启动终点的 P50 增量均不超过 300 ms、P95 增量均不超过 500 ms；peak PSS P50/P95 增量分别不超过 48/64 MiB，Native heap peak P50/P95 增量分别不超过 24/32 MiB，稳定 PSS P50/P95 增量分别不超过 32/48 MiB。
- 外部签名输出只用于测量签名块带来的大小差值和安装测试，必须使用 M3-01 当前 case 的同一张一次性证书；产品不参与签名，签名耗时不进入 Host 性能预算。
- 大小分项字段固定为 `bootstrapDexBytes`、`selectedRuntimeAbiBytes`、`fourAbiRuntimeBaselineBytes`、`containerMetadataBytes`、`encryptedPayloadBytes` 和 `zipStructureDeltaBytes`。对实际未签名输出，适用分项与输入大小必须无遗漏地调和到输出总字节数；四 ABI 全集基准单独报告，不重复计入单 ABI 实际输出。
- Host 对 100 MiB 合成输入的处理 median 不超过 60 秒、峰值 RSS 不超过 1 GiB；输入文件前后 SHA-256 必须相同。
- 高风险策略的额外延迟需单列，M2-06 固定抖动仍必须落在 20–50 ms，不从冷启动总开销中剔除。
- Android 测量模式固定为 `observed_cold_start` 与 `isolated_high_upgrade`。前者只能运行未修改的生产风险引擎，并在启动计时停止后记录 `riskObservationTiming=post_start` 与另行观察到的 level/action；它不声称读取 Guard 私有的早期报告，固定 reference profile 若非 LOW 则按环境不可比失败。后者每个样本先 force-stop 到新进程，只能由 Android-test fixture 对新鲜、已认证、同一 owned handle 调用现有 `MemoryProfile.HIGH`，且必须发生在任何 fixture class/resource lookup 前。
- `isolated_high_upgrade` 单独报告 `highProfileIncrementalMs`、Native jitter、same-handle、lookup 与 cleanup 证据。Native jitter 每个样本必须在 20–50 ms，wall-clock 每个样本不得超过 250 ms；其 P50/P95 不得称为真实 HIGH 冷启动，也不得与 LOW 相加后作为发布门禁。
- 禁止为 benchmark 新增生产 manifest/BuildConfig/system property/intent/file/environment override、风险 setter 或新的 public/package-private Runtime API。测试 bridge 与 keep 规则只能存在于 Android-test/M3-05 fixture source set，且必须证明未进入 Runtime AAR、生产 fixture APK、CLI 或 distribution 产物。

## Public Interfaces

- Gradle 入口 `:benchmarks:host:jmh` 与 `:benchmarks:android:connectedBenchmarkAndroidTest`。
- `benchmark-results.json` 字段为 `fixtureId`、`environmentId`、`measurementMode`、`observedRiskLevel`、`observedRiskAction`、`riskObservationTiming`、`metric`、`samples`、`p50`、`p95`、`baseline`、`delta`、`budget` 和 `pass`；`metric` 固定枚举 `hostProcessMs`、`hostPeakRssBytes`、`processToApplicationOnCreateMs`、`processToInteractiveMs`、`peakPssBytes`、`nativeHeapPeakBytes`、`stablePssBytes` 和 `highProfileIncrementalMs`。HIGH 隔离记录另含 30 个 `nativeJitterMs` 样本、`claimType=incremental_profile`、`freshProcess`、`sameHandle`、`lookupCountBeforeUpgrade`、`lookupCountAfterUpgrade` 与 `cleanupPassed`；不适用字段显式为 `null`，不得省略或伪造。Host 行 `measurementMode=null` 且为 10 个样本，Android 行必须为 30 个有限数值样本。
- 每个 fixture 另有 `artifactSizes`，固定包含 `inputSignedApkBytes`、`outputUnsignedApkBytes`、`outputExternallySignedApkBytes` 和上述六个 `sizeBreakdown` 字段。
- 环境文件 `benchmarks/environment.json` 与汇总 `build/reports/benchmark-summary.md`。
- 失败时进程退出码固定为非零且列出超预算 metric。
- 每个本地/CI `benchmark-results.json` 必须通过 `node tools/governance/verify-m3-07-high-benchmark-contract.mjs --report <file>`；缺字段、额外类型、非有限数值、错误样本数、LOW/DEGRADE 不一致或 mode/metric 专属字段漂移均 fail closed。

## Security Constraints

- benchmark 不关闭签名/容器完整性、AEAD、四 ABI 或内存控制。
- benchmark 不伪造风险报告；测试专用 HIGH bridge 不进入产品接口或分发产物，且不得把隔离 profile 增量表述为真实环境 HIGH 冷启动。
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
- 三类 fixture 的未签名 APK 增量、两个冷启动终点 P50/P95、peak PSS P50/P95、Native heap peak P50/P95 和稳定 PSS P50/P95 均不超过固定预算。
- 三种 APK 大小全部存在；外部签名测试副本与输入使用同一当前 signer。六项大小分解字段齐全，实际输出分项可精确调和，四 ABI 全集基准单列且不重复计数。
- 100 MiB 合成输入在 Windows/Ubuntu 的 Host median 与峰值 RSS 均达标，输入哈希前后相同。
- 每个 metric 具备规定样本数、原始样本、环境描述、基线、增量和 pass 判定，连续两次汇总差异在 10% 内。
- 报告明确写明大小只控制增量、不保证输出更小，且未隐藏高风险策略的额外开销。

## Required Tests

- 统计器、单位换算、预算边界、缺失样本和异常值标注测试。
- 三类 fixture 的 Host 三种 APK 大小、六项大小分解、耗时与 RSS benchmark。
- 两个 reference profile 的两个真实 LOW 启动终点、peak/stable PSS 与 Native heap peak，以及隔离 HIGH profile 的同 handle、零预查找、20–50 ms jitter、250 ms wall bound、后置查找和 exactly-once cleanup benchmark。
- x86/x86_64 不自动进入高风险以及输入只读回归测试。

## Required Evidence

- 所有命令、退出码、commit、toolchain、OS 和设备 profile。
- 原始样本、两个启动终点与三项内存统计、预算判定和两次重复运行差异。
- 输入、未签名输出、同证书外部签名输出 APK、bootstrap、各 Runtime ABI、container metadata、大小分解 manifest 和结果文件 SHA-256。
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
