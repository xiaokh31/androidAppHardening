---
id: V2-M3-03
title: "v0.2 大小、启动与内存门禁"
milestone: V2-M3
status: planned
owner_role: qa-governance-agent
depends_on:
  - V2-M3-02
baseline_inputs:
  - "main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523"
required_skills:
  - validate-protected-apk
  - coordinate-project-handoff
security_sensitive: true
---

## Goal

对唯一 `v0.2.0-rc.1` product tuple 执行一次 canonical Host/Android campaign，形成大小、处理时间、启动和内存的 fresh release gate，结果只有 `PASS` 或 terminal `BLOCKED`。

## Background

旧 M3-05 和后续诊断已终结，不能被重试或替换。本任务使用 V2-M3-01 从 main 独立实现的 Harness 和 V2-M3-02 freeze；它不是旧任务的 resume，也不读取旧 APK pair、profile、workflow 或报告。

## Inputs

- `baseline_inputs: main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523`。
- V2-M3-02 product tuple lock、canonical workflow 和 pre-run ledger。
- V2-M0-02 frozen product components。
- V2-M3-01 Harness、schema、runner 和 fixture source。
- fixed Windows/Ubuntu Host、API 36 x86_64 KVM 和授权 API 29 arm64 environment。

## Expected Outputs

- 每个平台/campaign 的 raw `v02-performance-results.json`。
- Canonical tracked `docs/v0.2/evidence/V2-M3-03/v02-performance-gate.json`、`v02-performance-artifact-manifest.json` 与 `performance-run-lock.json`。
- Per-platform environment files、raw report hashes 和 aggregate evidence。
- 每个 fixture 的 `v02-apk-byte-accounting.json` 与 input/output full-byte coverage hashes。
- 输入/输出/报告/Runtime/fixture hashes 和 test-signing cleanup evidence。
- `PASS` 或 `BLOCKED` 的不可变 canonical conclusion。

## In Scope

- 三个固定 fixture 的 Host size/time/RSS。
- 独立 100 MiB 合成 Host 输入的 3 次预热、10 个 retained samples、处理时间/RSS 和输入 SHA-256 门禁。
- API 36 x86_64 和 API 29 arm64 的 A/B 两 campaign、两个启动终点、三项内存指标。
- isolated HIGH incremental profile、Native jitter 和 cleanup。
- 原始样本、nearest-rank P50/P95、budget、repeatability 和从 APK bytes 独立得到的 size accounting 重算。
- 记录所有 invalid/failure 状态，不丢弃样本。

## Out of Scope

- 修改 Host、Runtime、fixture、Harness、workflow、budget 或 product tuple。
- 运行 ARM32/x86/device compatibility matrix、fuzz、安全审查或打包。
- 运行第二个 canonical result、third campaign、补样或平台替代。
- 根据结果现场优化生产代码。

## Implementation Decisions

- 只有 `/root` 可以在确认 V2-M3-02 exact-head 与 post-merge PASS 后执行一次 `gh workflow run v02-performance.yml --ref main`；不得提供 `-f` input、caller artifact 或替代 ref。
- Official uniqueness 必须确认 exact workflow/run name/task/branch 只有一个 run 且 `runAttempt=1`。
- 每个 fixture case：build reproducible unsigned fixture、生成本轮证书、签 input、保护、确认 input hash、确认 output unsigned、同证书签测试副本、执行、清理。
- Host 3 warmup/10 retained；Android 每 mode 5 warmup/30 retained，A/B 顺序互补。
- Frozen accounting validator 直接解析 input/output APK，为两者建立覆盖 `0..fileSize` 的 disjoint byte intervals；offset/length/hash/ZIP role/category 必须从原始 bytes 复算，报告自报值不能作为真值。
- 实际未签名输出只按 `outputUnsignedApkBytes = inputApkBytes + bootstrapDexEntryDataDeltaBytes + runtimeEntryDataDeltaBytes + manifestAndCopiedEntryDataDeltaBytes + encryptedContainerMetadataBytes + encryptedPayloadBytes + removedOriginalDexDataDeltaBytes + removedSignatureDataDeltaBytes + signingBlockDeltaBytes + zipStructureDeltaBytes` 调和。原 DEX delta 必须小于 0；输入有 v1 signature entries 时 `removedSignatureDataDeltaBytes < 0`、否则为 0；输入有 v2/v3 APK Signing Block 时 `signingBlockDeltaBytes < 0`、否则为 0；输出 v1 entries 与 signing block 都为 0。AHDC container 为 STORED 且按 authenticated offsets 分区；`zipStructureDeltaBytes` 是 header/descriptor/central/ZIP64/EOCD/trailer intervals 的独立 output-input 求和，不能是 remainder。`fourAbiRuntimeBaselineBytes` 仅单独报告，绝不计入实际输出。
- 90 行 A/B 对应统计全部使用 `abs(A-B) / max(1, min(abs(A), abs(B))) <= 0.10`，不得替换分母或先聚合后比较。
- 独立 100 MiB Host case 必须满足 median `hostProcessMs <= 60000`、peak `hostPeakRssBytes <= 1073741824`，且输入 SHA-256 前后相同。
- Host/Android memory source、owned process scope、采样窗口、cadence、单位和缺样语义逐字使用 frozen `TEST_STRATEGY.md` 与 `validationManifestSha256`：Windows Job aggregate `<=50 ms`、Ubuntu cgroup v2 `memory.peak`、Android `dumpsys meminfo --checkin` `<=500 ms`/至少 6 条及固定 stable window。任何 fallback、gap、错 PID/boot/tree 或缺 tail sample 都使 gate `BLOCKED`。
- `performance-run-lock.json` 字段顺序/schema由 V2-M3-01 冻结，绑定 repository、candidate head SHA、official run ID、`runAttempt=1`、workflow SHA-256、artifact numeric ID/name/size/GitHub digest/retention，以及同目录 gate/artifact-manifest 的 Git blob/size/SHA-256。只接受上述三个 tracked path；build-only、alternate path、聊天 hash 或 copy drift 均失败。
- 在任何 setup前运行 frozen `verify-v02-post-freeze-head.mjs --stage V2-M3-03 --phase pre-run`，要求本任务outputs尚不存在；evidence PR运行`--phase evidence-pr`，合并后main运行`--phase post-merge`。V2-M3-03 changed paths只能是 `post-freeze-path-policy-v1.json` 本阶段 exact allowlist，V2-M3-02路径、七 manifest blobs与五组 candidate/live bytes必须保持不变。
- 未签名输出增量上限为 `max(12 MiB, input × 15%)`；启动/内存预算与 V2 test strategy 完全一致。
- 任何 invalid sample、schema failure、cleanup failure、missing artifact、timeout、cancel 或 over-budget 都使 rc.1 `BLOCKED`，不得 rerun。

## Public Interfaces

- Canonical `.github/workflows/v02-performance.yml`。
- `node tools/validation/verify-v02-performance-report.mjs --report <path>`。
- `node tools/validation/aggregate-v02-performance.mjs --tuple <hash> --artifact-root <path>`。
- `v02-performance-gate.json`，decision 仅 `PASS|BLOCKED`。
- `node tools/validation/verify-v02-performance-run-lock.mjs --lock docs/v0.2/evidence/V2-M3-03/performance-run-lock.json`。

## Security Constraints

- benchmark 不关闭任何 production guard 或内存控制。
- 一次性证书仅在 ignored build directory，产品不接触 signer secrets。
- Artifact/log 不包含 password、private key、device serial、绝对路径或明文 DEX。
- 不接受 caller 自报 pass、历史 artifact 或不同 tuple 报告。

## Compatibility Requirements

- Performance claim 仅限 exact API 36 x86_64/API 29 arm64 reference environments。
- Host claim 仅限 fixed Windows/Ubuntu x86_64。
- x86_64 自身不触发风险；ARM-only 限制保持原样。
- 三 fixture 与 product components 都来自 frozen tuple。

## Acceptance Criteria

- Official canonical run 唯一、`runAttempt=1`、exact tuple/head/workflow/toolchain checks 全部通过。
- GitHub event 必须是 `/root` 在 `main` 上唯一一次无输入 `workflow_dispatch`；concurrency group 为 `v02-performance-v0.2.0-rc.1`、`cancel-in-progress=false`，且没有 caller/external artifact。
- 三 fixture 未签名 size delta 均在预算内；input/output interval 无 gap/overlap/unattributed byte，全部 category 从 APK bytes 复算并按排除 `fourAbiRuntimeBaselineBytes` 的完整公式精确调和；Host fixture 输入哈希不变。
- 独立 100 MiB Host case 具有 10 个 retained samples，median 不超过 60000 ms、peak RSS 不超过 1073741824 bytes，且输入哈希不变。
- 两个 Android reference environments 的两个 campaign 分别通过启动和内存预算；全部 90 行以 `abs(A-B) / max(1, min(abs(A), abs(B))) <= 0.10` 通过。
- HIGH 每个样本满足 20–50 ms jitter、250 ms wall、fresh process、same handle、zero pre-lookup 和 exactly-once cleanup。
- 每个 case 的 input signer 与 externally signed protected copy signer 相同，product output 未签名。
- 所有 raw samples、manifests 和报告 hashes 可复算，证书/临时 APK/device package 清理通过。
- 三个 canonical tracked outputs 存在且 bytes 与 official artifact一致；run lock 的 repository/head/run/attempt/artifact/workflow/gate/manifest identities 全部复算通过。
- Post-freeze verifier证明 completed stages恰为 V2-M3-02、V2-M3-03连续前缀，本阶段外零新路径且先前阶段 bytes无漂移。
- 任何一项失败时决定固定 `BLOCKED`，V2-M3-04 不可启动。

## Required Tests

- Workflow preflight identity 和 official uniqueness。
- Host/Android raw report schema、arithmetic、sample count、budget 和 repeatability validator。
- actual-output reconciliation 的 interval gap/overlap/hash/offset 伪造、DEX delta非负、v1-only/v2-v3-only/mixed 签名分项错误、unsigned input、输出残留签名、AHDC 非 STORED、remainder `zipStructureDeltaBytes`、重复计入 `fourAbiRuntimeBaselineBytes`、A/B 错误分母、缺少或超限的 100 MiB Host case 负例。
- Host/Android memory source/cadence/tree/PID/boot/unit/tail-window负例；canonical tracked output missing、build-only、alternate path、byte/hash drift、wrong run/head/attempt/artifact/workflow 和 malformed lock 负例。
- Post-freeze stage skip、unknown/extra path、修改M3-02 lock/workflow、七 manifest或candidate/live mismatch负例。
- Input read-only、unsigned output、same signer、artifact hash 和 cleanup。
- Missing/duplicate/changed artifact、third campaign、wrong order、different boot/job/head/tuple、非 `/root` dispatch、额外 input、caller/external artifact、错误 concurrency、rerun 和 platform substitution 负例。
- Sensitive log/path/DEX/key scan。

## Required Evidence

- Run/job/attempt/event/head/tuple/workflow/runner/toolchain identity。
- 所有 raw samples、reports、APK byte-accounting、artifact/environment manifests 和 SHA-256。
- 命令、退出码、OS、设备事实、时间戳、预算与 decision。
- test signer 和 package cleanup、独立 evidence review。

## Likely Files

- `docs/v0.2/evidence/V2-M3-03/`
- ignored `benchmarks/**/build/reports/v0.2/`
- root `HandOff.md`，仅 `/root` 记录 decision

## Dependencies and Blockers

V2-M3-02 未完成、canonical workflow 已出现 run、任一 fixed environment 不可用、tuple/head/toolchain 漂移或产品/Harness需要修改时不得开始。运行后的任何失败都是 rc.1 的 terminal blocker，不能通过新 run 替换。

## Agent Handoff Requirements

使用分支 `chore/v2-m3-03-performance-gate`，只处理 Issue #90 并创建一个对应 PR。开始前必须由 `/root` 返回 signed pre-run authorization，并由 `/root` 在 `main` 上仅 dispatch 一次无输入 canonical workflow；工作 Agent 不自行 dispatch。交接必须包含唯一 run identity、全部 raw/result hashes、预算、cleanups、PASS/BLOCKED 和不可重试声明；不得修改根 HandOff。
