---
id: V2-M3-01
title: "v0.2 fresh 性能 Harness"
milestone: V2-M3
status: planned
owner_role: qa-governance-agent
depends_on:
  - V2-M0-01
baseline_inputs:
  - "main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523"
required_skills:
  - plan-apk-hardening-change
  - validate-protected-apk
security_sensitive: true
---

## Goal

从当前 main 独立实现 v0.2 的 Host/Android 性能 Harness、exact-tuple release validation、security/SBOM、packaging 与 release-evidence gates，以及全部严格 schema、validator 和非执行 workflow candidate，使任何发布 PASS 代码都在新 product tuple 前冻结且不依赖旧 M3-05/诊断资产。

## Background

`benchmarks:host` 与 `benchmarks:android` 在接受的 main 上仍是基础模块。旧 M3-05 实现未合并，其 PR #63、APK pair、报告和后续诊断身份被 ADR 0019 终结。v0.2 必须重新实现测量，不 cherry-pick 旧分支，也不下载 M3-12 profile package。

## Inputs

- `baseline_inputs: main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523`。
- V2-M0-01 的产品需求、架构、测试策略、证据复用策略和 development tuple。
- baseline 九 fixture source、Host CLI、Runtime、device runner 和 fixed toolchain。
- 既有已接受 ADR 的安全边界，仅作为设计输入，不作为 V2 PASS。

## Expected Outputs

- Host benchmark runner、Android benchmark instrumentation 和严格 schema/validator。
- 三 fixture 的 fresh build/sign/protect/external-sign/cleanup orchestration。
- `V2_PERFORMANCE_REPORT_V1`、artifact/environment manifests 和 summary renderer。
- 非执行 workflow candidate `tools/validation/workflows/v02-performance.yml`。
- exact-tuple release-validation runner/schema/validator 和唯一非执行 `tools/validation/workflows/v02-release-validation.yml`。
- 读取 V2-M0-02 frozen `securityReviewV02`/CycloneDX/OSV tool lock 与 SBOM canonicalizer 的 raw/canonical schema、component/tool inventory、分层 vulnerability coverage、CVSS/native advisory/tool review validator 和唯一非执行 `tools/validation/workflows/v02-security-review.yml`。
- 冻结的 archive/package verification 与 release-evidence/schema/docs-check validator，以及唯一非执行 `tools/validation/workflows/v02-release-packaging.yml` 和 `tools/validation/workflows/v02-release-evidence.yml`；不在本任务生成 final archive 或 release docs。
- `identity-path-policy-v1.json.canonicalPaths` 固定的四类 lock schema/validator，以及 performance、release-validation、security、release-packaging 与 release-evidence 五类 raw artifact manifest schema。额外冻结 `tools/release-evidence-v02/verify-release-packaging-artifact-manifest.mjs` 与 `verify-release-evidence-artifact-manifest.mjs`；`performance-run-lock.json`、`validation-run-lock.json`、`security-run-lock.json` 与 `release-artifact-lock.json` 分别绑定唯一 official repository/head/run/attempt/artifact、canonical tracked output 与完整 raw member closure。
- Candidate-neutral `tools/governance/verify-v02-product-tuple.mjs`、freeze-acceptance/post-freeze verifier、package/HandOff candidate adapter 及全部负例。它们必须在本任务冻结；V2-M3-02 只写数据和复制 workflow，不实现或升级 gate。
- `tools/validation/schemas/v02-implementation-freeze-lock-v1.schema.json`、`v02-validation-freeze-lock-v1.schema.json` 与各自 validator，供 V2-M3-02 写入两个 tracked freeze locks及固定 ancestry/changed-path reports；锁唯一选择相应任务合并后 `main` official Governance `runAttempt=1` 的精确 `head_sha`，并绑定 PR head、`MERGE_COMMIT`、merge commit、run ID、manifest/component hashes、ancestry和changed-path report。
- tracked `fixture-source-manifest.json`、`validation-manifest.json`、`performance-contract-manifest.json`、`release-gate-contract-manifest.json`。
- 非产品 canary qualification workflow/evidence。
- 合并后由 official Governance `runAttempt=1` 唯一确定的 `validationFreezeSha`；V2-M3-02 仅把已发生的 merge/run 身份写入 `validation-freeze-lock.json`。
- V2-M3-02 后写的 canonical `docs/v0.2/evidence/V2-M3-01/{validation-freeze-lock.json,validation-freeze-ancestry-report.json,validation-freeze-changed-path-report.json}` 的冻结 schema/validator；本任务 PR自身不得预填这些未知 post-merge事实。

## In Scope

- `java-single-dex`、`kotlin-multidex`、`jni-four-abi`。
- Windows/Ubuntu Host 时间、RSS、三种 APK 大小和完整 byte-accounting 分解。
- 从 input/output APK 原始 bytes 独立重建的 entry/offset accounting manifest；报告中的大小字段不作为真值来源。
- 独立的 100 MiB 合成 Host 输入 case、60 秒 median 门限、1 GiB peak RSS 门限和输入 SHA-256 不变证明。
- API 36 x86_64 与 API 29 arm64 两个 reference environment 的两个启动终点、三项内存指标和 isolated HIGH 增量。
- A/B campaign、sample count、percentile、budget、repeatability 和 cleanup 验证。
- 只在 Android-test/benchmark source set 中实现 measurement bridge。
- 九 fixture full-flow/tamper/fuzz/cross-platform/API-ABI aggregate 的 schema、validator、runner 与 workflow candidate，但只做 synthetic canary。
- `SUPPLY_CHAIN_TOOLCHAIN.md` fixed lock 的只读校验、单一离线数据库 snapshot、coverage 和 severity gate 的实现与负例；不得修改 plugin/tool lock/verification metadata。
- 对 V2-M0-02 已冻结 packager/launcher/archive layout 的 exact-byte verifier，以及 V2-M4-03 将使用的 docs/evidence validator。
- 冻结 `tools/release-evidence-v02/schemas/{release-packaging-artifact-manifest-v1.schema.json,release-evidence-artifact-manifest-v1.schema.json}` 与对应 canonical validator；四个路径必须同时被 `validation-manifest.json` 和 `release-gate-contract-manifest.json` 完整枚举，任何 alternate/build-only path 都失败。
- 消费 `identity-path-policy-v1.json` 与 `post-freeze-path-policy-v1.json` 的唯一实现：固定七个 manifest selector/path→role、五组 candidate/live path、四类 lock path、阶段连续性、first-parent ancestry、每阶段 exact changed-path集合与早期 evidence 不可变性；实现不得内置第二套可漂移 allowlist。

## Out of Scope

- 修改任何生产 Host、Runtime、manifest、BuildConfig、风险策略或公共 API。
- 运行 v0.2 canonical performance、release validation、安全审查或形成任何 release PASS。
- 复制/cherry-pick PR #63/#79/#83，使用 predecessor tuple、旧 APK、profile asset、run 或报告。
- 修复性能问题、改变预算或新增保护能力。

## Implementation Decisions

- Harness 从 baseline main 重新实现；Git history/diff 检查拒绝从禁止分支复制文件。
- fixture unsigned build 必须可重复；每轮在 ignored directory 生成一次性证书，signed input 与 externally signed protected copy 使用同一证书，结束后全量清理。
- 四个 tracked manifest 严格消费 `identity-path-policy-v1.json` 的唯一路径、canonical schema、selector/path→role、Git mode/blob/hash/size、排序和 self-reference 禁令；`validationFreezeSha` 必须包含 exact preimage bytes。机器规范 missing/extra/reorder/selector/role drift 均失败，validator不得自行定义集合。
- 报告 metric 固定为 `hostProcessMs`、`hostPeakRssBytes`、`processToApplicationOnCreateMs`、`processToInteractiveMs`、`peakPssBytes`、`nativeHeapPeakBytes`、`stablePssBytes`、`highProfileIncrementalMs`。
- Host 预热 3/保留 10；Android 每 mode 预热 5/保留 30。同一 job/boot 恰好 A/B 两 campaign，顺序互补。
- APK accounting validator 必须直接读取 input/output bytes，把每个文件解析成不重叠、无间隙且覆盖 `0..fileSize` 的 byte intervals，逐段记录 offset、length、SHA-256、ZIP entry/structure role 和 category；ZIP local header/name/extra/data/data descriptor、APK Signing Block、central directory、ZIP64/EOCD/trailer 都由 bytes 独立定位，任何未归属 byte 失败。
- 实际未签名输出必须满足 `outputUnsignedApkBytes = inputApkBytes + bootstrapDexEntryDataDeltaBytes + runtimeEntryDataDeltaBytes + manifestAndCopiedEntryDataDeltaBytes + encryptedContainerMetadataBytes + encryptedPayloadBytes + removedOriginalDexDataDeltaBytes + removedSignatureDataDeltaBytes + signingBlockDeltaBytes + zipStructureDeltaBytes`。`removedOriginalDexDataDeltaBytes < 0`；输入含 v1 signature entries 时 `removedSignatureDataDeltaBytes < 0`，否则等于 0；输入含 v2/v3 APK Signing Block 时 `signingBlockDeltaBytes < 0`，否则等于 0。输出 v1 entries 与 signing block 都必须为 0，输入必须先通过既有 signer policy。`zipStructureDeltaBytes` 只能由已解析结构 interval 的 output-input 求和，禁止设置为 remainder。AHDC container 必须为 STORED，metadata/payload 依容器 offsets 分区；`fourAbiRuntimeBaselineBytes` 只作信息性基准，禁止计入公式。
- A/B 的 90 行对应统计统一计算 `abs(A-B) / max(1, min(abs(A), abs(B))) <= 0.10`；不得改用较大值、平均值或其他分母。
- 100 MiB 合成 Host case 与三个 fixture case 分开建模，仍执行 3 次预热和 10 个 retained samples；validator 必须要求 median `hostProcessMs <= 60000`、peak `hostPeakRssBytes <= 1073741824` 且输入 SHA-256 前后相同。
- Host memory source 固定遵守 `TEST_STRATEGY.md`：Windows `source=win32-job-getprocessmemoryinfo-v1`，使用 suspended root + non-breakaway Job Object/completion port，对 owned process tree 每 `<=50 ms` 聚合 `GetProcessMemoryInfo.WorkingSetSize`；Ubuntu 使用唯一 delegated cgroup v2 的 kernel `memory.peak` 并每 `<=50 ms` 记录 `memory.current`。schema 固定 source/version、tree/cgroup identity、PID/create-time、window、cadence、raw samples、单位 bytes 和 tail cleanup；父进程-only、`>75 ms` gap、PID reuse、尾窗缺样或 cgroup unavailable 都失败。
- Android 时钟统一为 boot-time monotonic；不信任 `am start -W` 的非目标 Activity 文本作为唯一成功信号。
- Android memory source 固定为 `dumpsys meminfo --checkin <pid>` pinned parser：process-start 到 interactive 后 tail window 每 `<=500 ms` 采样，最大 gap `<=750 ms` 且至少 6 条；绑定 boot ID、PID、`/proc/<pid>/stat` starttime并保留 raw output hash。`peakPssBytes`/`nativeHeapPeakBytes` 分别取 TOTAL PSS/Native Heap PSS 最大 bytes；`stablePssBytes` 取 interactive 后 3 秒开始、1 秒间隔的 5 条 TOTAL PSS median。错 PID/boot、稀疏/缺尾样、单位/解析错误或补样都失败。
- Security validators 必须把产品 SBOM 与 toolchain inventory 的并集逐项归类为 `osv-package|native-vendor-advisory|first-party-source|tool-binary`，严格执行 `SUPPLY_CHAIN_TOOLCHAIN.md` 的固定来源、fresh snapshot、reachability 与 UNKNOWN semantics。OSV zero findings 只能覆盖 `osv-package`；commit-only、generic purl 或 unsupported ecosystem 不能伪装成 OSV coverage。
- Candidate verifier 必须从两份 freeze-acceptance lock 的 official post-merge `main` run head重建 SHA、PR/merge/run identity和ancestry，再从对应 Git blobs重建七个 manifest、component baseline、五个 workflow candidate、所有 schema/validator和 compact tuple preimage。仅字段格式、`verificationStatus=VERIFIED` 或自洽 hash 不是信任依据。
- Package 与 HandOff candidate adapter 只能调用上述 exact verifier；伪造但自洽 lock、缺 freeze lock、错误 PR head/merge method/merge commit/run ID/attempt/head/conclusion、manifest/path/role/blob drift均 fail closed。当前 V2-M0-01 pre-candidate validator在 lock 存在时必须继续拒绝，直到本任务的 exact adapter随 validation freeze合并。
- Post-freeze verifier 必须逐字消费 `post-freeze-path-policy-v1.json`，沿 first-parent `main` 验证 completed stages 是连续前缀、每阶段唯一 merged PR/official post-merge run、当前 HEAD只含完成阶段 exact allowlist、先前阶段 path不再变化、七 manifest bytes不变、五组 candidate/live bytes相等；任一未知/额外/build-only/external path失败。
- Validation freeze ancestry report 从 Git objects与GitHub PR API双源绑定 baseline、implementation freeze、PR base/head、merge commit/parents、official main ref与validation freeze；changed-path report按UTF-8 path顺序记录完整 status/mode/blob/role并严格命中本任务allowlist。两报告的path/size/hash进入validation-freeze-lock。
- HIGH bridge 只操作 fresh authenticated owned handle，并证明 lookup 前后、same-handle、20–50 ms jitter、250 ms wall bound 和 exactly-once cleanup；不进入 production artifacts。
- workflow candidate 在 V2-M3-02 前不可执行。canary 可验证 tool/SDK/KVM/emulator/fixture plumbing，但不得构建或测量 v0.2 protected candidate。
- 五个 candidate `v02-performance.yml`、`v02-release-validation.yml`、`v02-security-review.yml`、`v02-release-packaging.yml`、`v02-release-evidence.yml` 在 V2-M3-02 前都不可执行；V2-M3-02 只允许把审查后的 candidate byte-for-byte复制到 `.github/workflows/` 同名 live path。V2-M3-03/V2-M3-04/V2-M4 后续只能执行，任何 validator/workflow/schema/tool lock变化使 tuple 失效。

## Public Interfaces

- Gradle `:benchmarks:host:v02Benchmark`。
- Gradle `:benchmarks:android:v02BenchmarkAndroidTest`。
- `node tools/validation/verify-v02-performance-report.mjs --report <path>`。
- `node tools/validation/verify-v02-apk-byte-accounting.mjs --input <path> --output <path> --manifest <path>`。
- `node tools/validation/verify-v02-release-validation.mjs --tuple <hash> --artifact-root <path>` 与 `tools/validation/workflows/v02-release-validation.yml`。
- 只验证 V2-M0-02 frozen Gradle `securityReviewV02` 与 `tools/validation/v02-supply-chain-tools.json`；本任务不得修改二者。
- `node tools/validation/verify-v02-release-package.mjs` 与 `node tools/release-evidence-v02/verify-v02-release-evidence.mjs`。
- `node tools/release-evidence-v02/verify-release-packaging-artifact-manifest.mjs --manifest <path>` 与 `node tools/release-evidence-v02/verify-release-evidence-artifact-manifest.mjs --manifest <path>`；schema/validator path不得替换。
- `node tools/release-evidence-v02/verify-release-artifact-lock.mjs --lock docs/v0.2/evidence/V2-M4-02/release-artifact-lock.json`。
- `node tools/security-review-v02/verify-security-run-lock.mjs --lock docs/v0.2/evidence/V2-M4-01/security-run-lock.json`。
- `node tools/validation/verify-v02-performance-run-lock.mjs --lock docs/v0.2/evidence/V2-M3-03/performance-run-lock.json` 与 `verify-v02-validation-run-lock.mjs --lock docs/v0.2/evidence/V2-M3-04/validation-run-lock.json`。
- `node tools/validation/verify-v02-implementation-freeze-lock.mjs --lock docs/v0.2/evidence/V2-M0-02/implementation-freeze-lock.json` 与 `verify-v02-validation-freeze-lock.mjs --lock docs/v0.2/evidence/V2-M3-01/validation-freeze-lock.json`。
- `node tools/governance/verify-v02-product-tuple.mjs --lock docs/v0.2/evidence/V2-M3-02/product-tuple-lock.json`。
- `node tools/governance/verify-v02-post-freeze-head.mjs --head <sha> --stage <V2-task> --phase <pre-run|evidence-pr|post-merge> --policy docs/v0.2/post-freeze-path-policy-v1.json`；stage与phase均必填。
- `v02-performance-results.json`、`v02-performance-artifact-manifest.json`、`v02-environment.json`。
- `tools/validation/workflows/{v02-performance.yml,v02-release-validation.yml,v02-security-review.yml,v02-release-packaging.yml,v02-release-evidence.yml}`，五个非执行 candidate；不得改名或创建替代路径。

## Security Constraints

- 产品模块和 production dependency graph 不得出现签名、measurement override 或 test bridge。
- 报告不含 keystore/password、device serial、用户绝对路径、明文 DEX 或完整 signer digest。
- benchmark 不关闭 signer、AEAD、integrity、四 ABI 或内存控制。
- 任何无效样本、缺失 cleanup、环境漂移或 schema 漂移 fail closed。

## Compatibility Requirements

- Host runner 支持 Windows/Ubuntu x86_64。
- Android protocol 只声明 API 36 x86_64 与 API 29 arm64 reference measurement，不外推其他设备。
- x86/x86_64 不因 ABI 自动提升风险级别。
- fixture 输入保持 `minSdk >= 29` 且使用 Release/R8 产品路径。

## Acceptance Criteria

- Host/Android runner、schema、validator、artifact/environment manifest 和 workflow candidate 完整实现。
- Release-validation/security/package/release-evidence schema、validator、tool lock、五类 raw artifact manifest、四类 run/artifact lock 和五个 workflow candidate 完整实现并纳入 `validationManifestSha256`/`releaseGateContractSha256`；release-packaging/release-evidence 的 schema 与独立 validator使用 `identity-path-policy-v1.json` 固定的四个 canonical path。
- Candidate-neutral aggregate verifier、freeze acceptance validator、post-freeze HEAD closure、package/HandOff candidate adapter及其 schema/self-tests全部在 candidate前实现并进入两个 manifest；V2-M3-02 不再拥有可写 gate代码。
- 本地纯 JVM/schema/runner 测试与 Windows/Ubuntu Build/Governance 退出码为 0。
- canary qualification 证明 fixed SDK/emulator/KVM、fixture build、test signer、report plumbing 和 cleanup 可执行，但没有 v0.2 product measurement。
- 所有预算、sample counts、A/B 顺序、repeatability 和 HIGH 字段由原始样本复算，不能信任自报 `pass`。
- Validator 从 APK bytes 和 interval manifest 精确执行完整调和公式，拒绝 remainder/未归属 bytes并排除 `fourAbiRuntimeBaselineBytes`；精确执行 A/B 公式，并把独立 100 MiB Host case 作为 mandatory schema/gate 输入；canary 只验证 orchestration，不形成产品 PASS。
- 四个 tracked manifest 从 exact validation freeze Git blobs 重建且集合/schema/order/mode/hash 完全一致；release gate 代码在 freeze 后没有可写实现任务。
- `validationFreezeSha` 唯一等于 V2-M3-01 `MERGE_COMMIT` 后 `main` official Governance `runAttempt=1` 的 exact `head_sha`；PR head、merge commit、run ID/head/conclusion、四 manifest hashes、implementation ancestry与changed paths可由 freeze-acceptance schema/validator复核。
- `identity-path-policy-v1.json` 的所有 canonical path、selector、role与 `post-freeze-path-policy-v1.json` 的阶段/allowlist均被 validator消费；每个后继 main HEAD只能出现连续阶段的 exact paths，早期 evidence与全部 frozen identity bytes保持不变。
- production APK/AAR/CLI/distribution 扫描确认不存在 benchmark bridge、signing capability 或 override。
- old branch/workflow/artifact/profile/predecessor tuple 引用扫描为零。
- 独立只读复核返回 `P0=0/P1=0/P2=0` 并冻结 validation bytes。

## Required Tests

- 统计器、nearest-rank P50/P95、单位、预算等号边界和实际输出调和公式；interval overlap/gap/out-of-range/hash drift、DEX delta 非负、v1-only/v2-v3-only/mixed 签名分项错误、unsigned input、输出残留 v1/signing block、AHDC 非 STORED、把 `fourAbiRuntimeBaselineBytes` 计入、遗漏分项或用 remainder 伪造 `zipStructureDeltaBytes` 必须失败。
- 4/6 warmup、9/11 Host samples、29/31 Android samples、NaN/Infinity、missing/null/extra field、wrong enum 负例。
- campaign 同序、第三 campaign、不同 SHA/job/boot、补样、删除 outlier、A/B 错误分母、报告复用和算术伪造负例。
- 缺少 100 MiB case、字节数不精确、median 超过 60000 ms、peak RSS 超过 1073741824 bytes 或输入哈希变化负例。
- test signer 同证书、输入只读、未签名输出、证书/临时 APK cleanup 正负例。
- HIGH handle ownership、lookup order、jitter、wall bound、cleanup 和 production exclusion。
- canary 不得接受 protected candidate 或生成 release claim 的负例。
- 七类 identity manifest 的 missing/extra/reorder/path/mode/blob/byte/self-reference/wrong-preimage，以及 machine policy missing/extra/reorder/selector/path→role/第二份allowlist负例；release validator/workflow/schema 在 freeze 后漂移负例。
- 格式和 hash 自洽但 freeze/manifests虚假的 `VERIFIED` lock、错误 PR head、非 `MERGE_COMMIT`、错误 merge commit、缺/第二 post-merge run、wrong attempt/head/conclusion、ancestry/changed-path drift、component baseline drift负例。
- Post-freeze stage skip/reorder、unknown/extra path、把 build-only/external evidence列入、先前 stage byte drift、七 manifest entry drift、candidate/live workflow mismatch、第二 workflow path负例。
- SBOM tool/version/hash/schema、raw timestamp 差异与 canonical equality、serial/build-system/额外 normalization path、single DB snapshot、offline/coverage/severity 负例；native commit-only、generic purl、unsupported ecosystem、vendor snapshot缺失、false-zero、缺 reachability 和 tool-binary误归类负例，以及 package/release-evidence mutation 负例。
- Host memory 的父进程-only、稀疏/错PID/单位、Job breakaway、cgroup缺失、尾窗缺样，以及 Android memory 的错 boot/PID/starttime、`>750 ms` gap、少于 6 条、只取结束 sample、缺 interactive/tail 与 stable-window补样负例。
- Release artifact lock 的 missing/extra/order、wrong repo/run/attempt/head/name/numeric ID/size/digest/member hash、expired/deleted/second artifact、caller input/URL/cache fallback 负例。
- Security run lock、tracked security evidence manifest 与其完整 raw member closure的 missing/extra/build-only/alternate path/order/run/head/attempt/size/hash/byte-drift 负例。
- Validation run lock、tracked release-validation evidence manifest 与其完整 raw member closure的同类负例。
- Release-packaging/final-evidence raw artifact manifest 的 schema path、validator path、字段/role/order/source/member closure、missing/extra/rename/alternate/build-only/hash/byte-drift，以及从 validation/release-gate manifest删去四个 frozen path的负例。

## Required Evidence

- runner/schema/全部 workflow candidate hashes、四个 tracked preimage hashes、candidate/freeze/post-freeze verifier hashes、validation freeze official merge/run identity 和 changed paths。
- 所有命令、退出码、OS/toolchain、时间戳和 canary environment。
- positive/negative test count、production exclusion scans 和 cleanup report hashes。
- 独立审查报告与发现处置。

## Likely Files

- `benchmarks/host/`
- `benchmarks/android/`
- `tools/validation/verify-v02-performance-report.mjs`
- `tools/validation/workflows/v02-performance.yml`
- `tools/validation/workflows/v02-release-validation.yml`
- `tools/validation/workflows/v02-security-review.yml`
- `tools/validation/workflows/v02-release-packaging.yml`
- `tools/validation/workflows/v02-release-evidence.yml`
- `tools/governance/verify-v02-product-tuple.mjs`
- `tools/validation/verify-v02-implementation-freeze-lock.mjs`
- `tools/validation/verify-v02-validation-freeze-lock.mjs`
- `tools/governance/verify-v02-post-freeze-head.mjs`
- `tools/security-review-v02/`
- `tools/release-evidence-v02/`
- `.github/workflows/v02-performance-canary.yml`
- `docs/v0.2/evidence/V2-M3-01/`

## Dependencies and Blockers

V2-M0-01 未完成、需要修改 production interface、依赖旧 artifact/profile、任一 release gate 未在候选前实现、无法在 test-only source set 测量、canary 失败或独立审查存在发现时保持 blocked。可以与 V2-M0-02 并行开发，但 V2-M0-02 未先合并并完成 post-merge PASS 时，本任务不得合并。不得通过放宽预算、延后 validator 或复制旧未合并实现解决。

## Agent Handoff Requirements

使用分支 `chore/v2-m3-01-fresh-performance-harness`，只处理 Issue #88 并创建一个对应 PR，且合并方式固定 `MERGE_COMMIT`。V2-M0-02 post-merge PASS 后，必须把本分支更新到包含唯一 `implementationFreezeSha` 的最新 `main`，重跑 changed-path、production-exclusion、全部 gate canary、独立复核和 CI，再合并；随后唯一成功的 `main` official Governance `runAttempt=1` exact `head_sha` 才成为 `validationFreezeSha`。V2-M3-02 只把该既成 PR/merge/run身份写入 freeze lock。交接必须包含两个 freeze SHA 的 ancestry、四个 manifest、全部接口/schema/workflow candidate、测试矩阵、canary、所有 hashes、production exclusion 和 cleanup；不得修改根 HandOff。
