---
id: V2-M3-04
title: "v0.2 exact-tuple 发布验证"
milestone: V2-M3
status: planned
owner_role: qa-governance-agent
depends_on:
  - V2-M3-03
baseline_inputs:
  - "main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523"
required_skills:
  - validate-protected-apk
  - plan-apk-hardening-change
security_sensitive: true
---

## Goal

在 V2-M3-03 PASS 后，对同一 candidate tuple 重新执行九 fixture full-flow、tamper/fuzz、Windows/Ubuntu 等价性和完整 API/ABI 清单，形成 fresh release validation gate。

## Background

基线的 M3-01 至 M3-04 实现和工具可以复用，但其历史报告不能作为 v0.2 PASS。本任务必须在 exact tuple 上重新生成 evidence，并保持 unsupported/unverified 边界。

## Inputs

- `baseline_inputs: main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523`。
- V2-M3-02 tuple lock 和 frozen product/validation manifests。
- V2-M3-02 byte-identical live `.github/workflows/v02-release-validation.yml` 与 frozen schema/runner/validator。
- V2-M3-03 exact tracked `docs/v0.2/evidence/V2-M3-03/{v02-performance-gate.json,v02-performance-artifact-manifest.json,performance-run-lock.json}`；其 PASS 与 official identities 必须先复算。
- 九 fixture source、tamper catalog、Jazzer/native fuzz、cross-platform corpus 和 device capability probe。
- fixed Windows/Ubuntu、API 29/36 KVM 和授权 API 29 ARM device。

## Expected Outputs

- Windows/Ubuntu full-flow/equivalence fresh reports。
- tamper catalog、Jazzer、native fuzz/ASan/UBSan fresh reports。
- Canonical tracked `docs/v0.2/evidence/V2-M3-04/v02-release-validation-gate.json`、`v02-compatibility-matrix.json`、`v02-release-validation-artifact-manifest.json` 与 `validation-run-lock.json`。
- Markdown render、32-cell manifest、V2 validation aggregate、cleanups 和 PASS/BLOCKED raw evidence；每个 raw artifact member均由 tracked artifact manifest枚举并由 run lock闭包绑定。

## In Scope

- 九个公开 fixture 的 build/sign/protect/external-sign/install/start/assert/cleanup。
- 输入只读、output unsigned、same signer、稳定 error/report schema 和 unsupported rejection。
- 69 项 tamper、APK/AXML fuzz、Native container/parser fuzz 和 regression corpus。
- Windows/Ubuntu 两轮语义等价与 nondeterministic field 独立认证。
- API 29 ARM32/ARM64 physical、API 29/36 x86_64 KVM mandatory cells。

## Out of Scope

- 修改或新增产品、Harness、validator、schema、workflow、tuple、性能结果、fuzz contract 或兼容声明规则。
- 把 API 30–35 或 x86 build capability表述为 fresh device validation。
- 运行旧 M3 workflow/artifact，新增设备格子或重试 product failure。
- 安全/SBOM 审查、distribution 或用户文档。

## Implementation Decisions

- 每份报告在处理前验证 exact tuple、source commit、component hashes 和 toolchain。
- 本任务只执行 `validationManifestSha256` 已覆盖的 runner/schema/validator 和 live workflow；任何 byte/path/hash变化先使 tuple 失效，不能在本任务修补。
- V2-M3-03 PASS 后只有 `/root` 可在 `main` 执行一次无 input 的 `gh workflow run v02-release-validation.yml --ref main`；要求 fixed concurrency、`runAttempt=1`、无 caller/external artifact，失败不得 rerun。
- Windows/Ubuntu 各运行两轮九 fixture；密码学随机字段不得相等，但规范语义、错误码和受保护内容验证结果必须一致。
- 32-cell 状态只允许 `VERIFIED|FAILED|UNVERIFIED`。mandatory 四格必须 fresh VERIFIED；任何 FAILED 阻塞。
- 每个 VERIFIED 格运行正向 fixture、different-signer、authenticated-tag tamper、payload lookup zero 和 session unpublished assertions。
- Fuzz 复用已锁定 corpus/toolchain 但重新执行；crash、timeout、OOM 或 sanitizer finding 不得从报告中删除。
- 产品缺陷需要单独修复任务和新 candidate tuple；本任务只验证。
- 开始前 frozen validator 必须从 exact tracked paths复算 M3-03 gate/artifact-manifest/run-lock；lock 要绑定 same repository、candidate head、official numeric run/artifact、`runAttempt=1`、workflow/gate/manifest hashes。build-only、alternate path、缺文件或 byte/hash drift 都在任何 device/fuzz 前失败。
- `v02-release-validation-artifact-manifest.json` 的 schema由 V2-M3-01冻结；它按 UTF-8 path顺序完整枚举 official artifact内每份 full-flow、equivalence、tamper/fuzz、matrix、device、environment、aggregate、cleanup与raw log member的 `path,role,sizeBytes,sha256,productTupleSha256,toolId,toolVersion,sourceRunId,sourceArtifactId`。Missing/extra/重复/alternate member或未归类 role失败。
- `validation-run-lock.json` 字段顺序/schema由 V2-M3-01冻结，绑定 repository、candidate head SHA、official release-validation run ID、`runAttempt=1`、workflow SHA-256、artifact numeric ID/name/size/GitHub digest/retention，以及同目录 validation gate、compatibility matrix与release-validation artifact manifest三个文件的 Git blob/size/SHA-256。Validator 从 official artifact重新枚举成员并复算 manifest；下游只接受这四个 canonical tracked path，build-only、alternate或自报摘要失败。
- 在任何执行前运行 frozen post-freeze verifier `--stage V2-M3-04 --phase pre-run`并要求本任务outputs缺失；evidence PR运行`--phase evidence-pr`，合并后main运行`--phase post-merge`。V2-M3-04 changed paths只能是 `post-freeze-path-policy-v1.json` 本阶段 exact allowlist，七 manifest覆盖 blobs、五组 workflow equality和M3-02/M3-03路径必须保持不变。

## Public Interfaces

- Gradle `./gradlew v02ReleaseValidation`。
- `node tools/validation/verify-v02-release-validation.mjs --tuple <hash> --artifact-root <path>`。
- `v02-compatibility-matrix.json` 及既有三状态 schema 的 v0.2 version。
- `v02-release-validation-gate.json`，decision `PASS|BLOCKED`。
- `node tools/validation/verify-v02-validation-run-lock.mjs --lock docs/v0.2/evidence/V2-M3-04/validation-run-lock.json`。
- `docs/v0.2/evidence/V2-M3-04/v02-release-validation-artifact-manifest.json`，schema固定为 `tools/validation/schemas/v02-release-validation-artifact-manifest-v1.schema.json`。
- Canonical `.github/workflows/v02-release-validation.yml`；本任务无权编辑。

## Security Constraints

- 测试签名只在 ignored directory，结束后删除。
- 不记录私钥、password、device serial、用户路径、明文 DEX 或真实客户 APK。
- signer/AEAD/integrity 负例必须在 payload lookup 前 fail closed。
- fuzz 日志必须脱敏但保留复现所需的 seed/hash/target/toolchain。

## Compatibility Requirements

- Mandatory cells：API 29 `armeabi-v7a`、API 29 `arm64-v8a`、API 29 `x86_64`、API 36 `x86_64`。
- 其他 28 格默认 `UNVERIFIED`，具有稳定 reason、null device facts 和 no positive claim。
- 四 ABI Runtime build 全部存在；ARM-only fixture 在 x86 明确受限。
- x86/x86_64 risk contribution 为 0。

## Acceptance Criteria

- Windows/Ubuntu 九 fixture 各两轮 full-flow PASS，输入哈希不变、产品输出未签名、same-signer test copy 正常启动。
- 跨平台规范语义一致，随机 nonce/key/container bytes 不被错误复用，报告无绝对路径。
- 69 tamper cases、Jazzer targets、Native fuzz + ASan/UBSan 无未解决 crash/finding。
- 32 格唯一完整，mandatory 四格 fresh VERIFIED、其余 28 格准确 UNVERIFIED、无 FAILED。
- VERIFIED 格正向组件事件和 signer/tag 负例完整，cleanup PASS。
- 所有报告、artifact、environment 和 aggregate hashes 绑定同 tuple；tracked artifact manifest从 official artifact members完整重建且canonical bytes可复算。
- Live workflow、runner、schema 和 validator hashes 与冻结的 `validationManifestSha256`/`releaseGateContractSha256` 一致，official run 唯一且 `runAttempt=1`。
- M3-03 三个 canonical inputs与本任务四个 canonical outputs均通过 exact path/blob/hash/official-run lock校验；validation-run-lock必须绑定 artifact manifest Git blob，manifest必须闭包覆盖每个 raw member，不得以 build artifact或聊天摘要代替。
- Post-freeze verifier在本任务 current HEAD通过：completed stages连续、当前 diff仅含本阶段 exact allowlist、先前 evidence未变、七 manifest和五组 candidate/live bytes仍与freeze一致。
- 独立只读复核 `P0=0/P1=0/P2=0`。

## Required Tests

- 九 fixture full-flow、unsupported inputs、wrong signer、tamper 和 cleanup。
- Cross-platform field classification、canonical semantics、random-field non-reuse 和 absolute-path scan。
- Tamper catalog completeness、fuzz corpus/target/toolchain、sanitizer 和 regression replay。
- Compatibility matrix missing/duplicate/unknown state/fake facts/claim expansion 负例。
- Tuple/commit/toolchain/artifact mismatch 和历史 evidence injection 负例。
- Live/candidate workflow、validator/schema hash drift、second run、extra input、non-main ref、caller artifact 和 post-freeze implementation path 负例。
- M3-03 input 与 M3-04 output 的 missing、build-only、alternate path、copy drift、wrong repository/head/run/attempt/artifact/hash、malformed/second lock负例；artifact manifest missing/extra/reorder/duplicate/wrong role/tool/tuple/member size/hash/source ID和 official artifact未枚举成员负例。
- Post-freeze stage skip/reorder、额外 path、修改旧 evidence/七 manifest/live workflow、build-only path注入负例。

## Required Evidence

- 每个平台/run/fixture/cell/target 的命令、退出码、environment、toolchain、时间和 hashes。
- Full-flow、equivalence、tamper/fuzz、matrix、device facts 和 cleanups。
- Exact tuple aggregate、independent review 和 residual unverified cells。
- Tracked release-validation artifact manifest bytes/Git blob/hash、official artifact member listing与 validation-run-lock完整闭包复算报告。

## Likely Files

- `docs/v0.2/evidence/V2-M3-04/`
- ignored `integration-tests/**/build/v0.2/` 与 CI artifact；不提交 runner/schema/workflow 变更

## Dependencies and Blockers

V2-M3-03 非 PASS、live/frozen validation bytes 不同、canonical run 已存在、mandatory environment 缺失、任一 product/fuzz/compat failure、tuple 漂移或需要产品/validator修改时保持 blocked。不得用 build success、旧 device report、其他 API/ABI 或后冻结补丁替代 mandatory fresh evidence。

## Agent Handoff Requirements

使用分支 `chore/v2-m3-04-exact-tuple-validation`，只处理 Issue #91 并创建一个对应 PR。交接必须包含九 fixture、fuzz/tamper、32-cell matrix、commands/exits/hashes、cleanups、independent review 和 PASS/BLOCKED；不得修改根 HandOff。
