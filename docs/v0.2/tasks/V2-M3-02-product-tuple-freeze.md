---
id: V2-M3-02
title: "v0.2 product tuple 冻结"
milestone: V2-M3
status: planned
owner_role: /root
depends_on:
  - V2-M0-02
  - V2-M3-01
baseline_inputs:
  - "main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523"
required_skills:
  - plan-apk-hardening-change
  - coordinate-project-handoff
  - validate-protected-apk
security_sensitive: true
---

## Goal

把已独立复核的产品实现 freeze 与 validation freeze 组合为唯一 `v0.2.0-rc.1` product tuple，发布字节一致的 canonical performance workflow，并在运行 Android 前建立 fail-closed identity gate。

## Background

Development tuple 不可发布。V2-M0-02 与 V2-M3-01 分别产生互不重叠的实现和验证 freeze；二者可并行开发，但必须按“V2-M0-02 先 merge/post-merge PASS，V2-M3-01 再基于该 main 全量复核并 merge/post-merge PASS”的顺序形成可验证 ancestry。只有 `/root` 复算 manifests 并完成独立审查后，才能生成 release-candidate tuple。此任务不执行性能测量。

## Inputs

- `baseline_inputs: main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523`。
- V2-M0-02 合并后 `main` official Governance `runAttempt=1` 的 exact head、reviewed PR/merge/run证据、implementation/toolchain/product-contract manifests 和唯一 tracked `docs/v0.2/evidence/V2-M0-02/v02-component-baseline.json`。
- V2-M3-01 合并后 `main` official Governance `runAttempt=1` 的 exact head、reviewed PR/merge/run证据、fixture-source/validation/performance-contract/release-gate-contract manifests 和全部 workflow candidate。
- ADR 0020 的固定 tuple preimage 字段与顺序。
- development tuple 与 V2 release-gate contract。

## Expected Outputs

- `docs/v0.2/evidence/V2-M3-02/product-tuple-lock.json`。
- `docs/v0.2/evidence/V2-M0-02/implementation-freeze-lock.json` 与 `docs/v0.2/evidence/V2-M3-01/validation-freeze-lock.json`；仅写入已经发生且由 official API复核的固定 schema 数据。
- tuple preimage、`productTupleSha256` 和各 manifest hash 的复算证据。
- 根 `HandOff.md` 从 exact development tuple 原子切换到 verified `v0.2.0-rc.1` tuple 的状态快照。
- 调用 V2-M3-01 已冻结的 candidate-neutral tuple/freeze/post-freeze verifier和 package/HandOff adapter；本任务不新增、升级或修复任何 validator/schema/spec。
- `.github/workflows/v02-performance.yml`，与 candidate 字节一致。
- `.github/workflows/v02-release-validation.yml`、`v02-security-review.yml`、`v02-release-packaging.yml` 与 `v02-release-evidence.yml`，分别与其非执行 candidate 字节一致。
- pre-run ledger、workflow identity 和 mutation-test evidence。
- canonical run 尚未执行、且 changed paths严格等于 `post-freeze-path-policy-v1.json` 中 V2-M3-02 allowlist子集的协调 commit。

## In Scope

- 验证两 freeze commit 的 ancestry、product-path separation、toolchain 和 manifest。
- 以固定 freeze-acceptance schema记录两个任务唯一 `MERGE_COMMIT`、reviewed PR head、merge commit、post-merge main Governance run ID/attempt/head/conclusion、manifest hashes和changed-path report；`implementationFreezeSha`/`validationFreezeSha` 分别唯一等于这些 official run 的 exact `head_sha`。
- 计算 candidate tuple 并对其全部 bytes fail closed 验证。
- 将所有非执行 candidate 直接、字节一致地发布到固定 live workflow path。
- workflow 在 Android setup 前验证 HEAD、tuple、candidate bytes、toolchain、runner 和 official run uniqueness。
- 为 V2-M3-03 设置唯一 `runAttempt=1` 授权。
- 执行已冻结 post-freeze verifier，证明 V2-M3-02 是 validation freeze之后的首个连续阶段，七 manifest blobs不变且除本阶段 exact allowlist外零变化。

## Out of Scope

- 运行 workflow、启动 emulator/device、生成 APK 或测量性能。
- 修改产品、Harness、预算、fixture、toolchain 或 validation contract。
- 创建、编辑、替换或延后 tuple/freeze/post-freeze/package/HandOff validator、schema、machine policy 或测试。
- 引用旧 M3-05/M3-10/M3-14 execution identity 或 artifact。
- 授权 rerun、retry、替代平台或第二个 rc.1 结果。

## Implementation Decisions

- Tuple exact compact JSON field order由 ADR 0020 固定；任何字段缺失、重排、额外字段或非小写 hash 都失败。
- `implementationFreezeSha` 与 `validationFreezeSha` 分别唯一等于对应 task合并后 `main` official Governance `runAttempt=1` 的 exact `head_sha`；两份 tracked freeze-acceptance lock必须绑定唯一 Issue/merged PR、reviewed head、`MERGE_COMMIT`、merge commit、run ID/attempt/head/conclusion、manifest hashes与changed-path报告。前者必须是后者祖先，其间 changed paths只能是 V2-M3-01授权 validation paths，不得含生产路径。
- product tuple lock 在 freeze 的后继协调 commit 中创建，避免 self-reference。
- HandOff tuple 状态机固定为：lock 不存在时只接受 development tuple；V2-M3-01 frozen exact verifier验证两 freeze locks、七 manifests、component baseline、ancestry和tuple之后，lock 存在时只接受其 exact `productTupleSha256`。本任务必须在同一 PR原子提交三份 lock、根 HandOff和报告；两类 adapter bytes不得变化。
- 每个 workflow live bytes 必须等于 V2-M3-01 对应 candidate；仅 path 从非执行区复制到 `.github/workflows/`。性能、release-validation、security、packaging 和 release-evidence workflow/source/schema/validator 都由 `validationManifestSha256` 和 `releaseGateContractSha256` 绑定。
- canonical workflow 的唯一事件是无 `inputs` 的 `workflow_dispatch`；禁止 `push`、`pull_request`、`schedule`、`repository_dispatch`、`workflow_call` 和任何 caller input。
- canonical workflow 只接受 `refs/heads/main`，不下载 caller 或外部 artifact；固定 `concurrency.group=v02-performance-v0.2.0-rc.1` 且 `cancel-in-progress=false`。
- canonical workflow 还固定 task key、candidate ID、run name、job name、runner/toolchain 和 `runAttempt=1`。V2-M3-02 只发布并验证 workflow，不 dispatch。
- 其他四个 live workflow 同样只有无 inputs 的 `workflow_dispatch`、只接受 `refs/heads/main`，并各自固定 candidate ID、concurrency group、`cancel-in-progress=false` 和 `runAttempt=1`；只能由对应下游任务的 `/root` 在依赖 PASS 后执行一次。禁止 caller URL/input、Actions cache、任意 external/alternate run artifact。唯一例外是 `v02-release-evidence.yml` 可从已合并 tracked `docs/v0.2/evidence/V2-M4-02/release-artifact-lock.json` 读取 same-repository V2-M4-02 唯一 official `runAttempt=1` 的 numeric run/artifact ID，在任何 smoke/claim 前核对 repository、head、artifact name、size、GitHub digest、retention 和 member hashes；该 ID 不是 workflow input。V2-M3-02 不 dispatch 任一 workflow。
- product measurement 开始后任何失败都终结 rc.1；pre-run validator 失败也不允许以改名 workflow 重试。
- V2-M3-02 的 allowed changed paths 逐字来自 `post-freeze-path-policy-v1.json`；current HEAD 中七 manifest覆盖集合/blob、五组 candidate/live equality和 validation freeze ancestry由 frozen verifier重算。未知路径、额外 workflow、build-only证据或任何已冻结 byte变化均失败。

## Public Interfaces

- `node tools/governance/verify-v02-product-tuple.mjs --lock docs/v0.2/evidence/V2-M3-02/product-tuple-lock.json`。
- `node tools/validation/verify-v02-implementation-freeze-lock.mjs --lock docs/v0.2/evidence/V2-M0-02/implementation-freeze-lock.json` 与 `node tools/validation/verify-v02-validation-freeze-lock.mjs --lock docs/v0.2/evidence/V2-M3-01/validation-freeze-lock.json`。
- `node tools/governance/verify-v02-post-freeze-head.mjs --head HEAD --stage V2-M3-02 --phase <pre-run|evidence-pr|post-merge> --policy docs/v0.2/post-freeze-path-policy-v1.json`；写lock前先跑pre-run，PR bytes跑evidence-pr，合并后main跑post-merge。
- `docs/v0.2/evidence/V2-M3-02/product-tuple-lock.json`。
- `.github/workflows/v02-performance.yml`。
- `.github/workflows/v02-release-validation.yml`、`.github/workflows/v02-security-review.yml`、`.github/workflows/v02-release-packaging.yml`、`.github/workflows/v02-release-evidence.yml`。
- `docs/v0.2/evidence/V2-M3-02/pre-run-ledger.json`。

## Security Constraints

- Product tuple 不包含私钥、password、用户路径、设备 serial 或明文 DEX。
- Old predecessor tuple、PR/run/artifact/profile identity 不得出现在授权字段。
- workflow 权限最小化，不接受任意 caller-provided artifact 或 branch。
- 当前 candidate hash 不得由 workflow 自报；必须从 tracked exact bytes 重算。

## Compatibility Requirements

- Tuple contracts 固定 AHDC 2、ConfigV2 2、SPV1、REPORT_V1、minSdk 29 和四 ABI。
- Fixture source 与 V2-M3-01 精确一致。
- Candidate 不增加任何 API/ABI 或 input-format claim。

## Acceptance Criteria

- Candidate tuple 的 compact JSON 和 SHA-256 可从 exact manifests 重建并与 lock 一致。
- Tuple 中 `implementationManifestSha256`、`toolchainManifestSha256`、`productContractManifestSha256`、`fixtureSourceManifestSha256`、`validationManifestSha256`、`performanceContractSha256` 和 `releaseGateContractSha256` 逐一等于 `IDENTITY_MANIFESTS.md` 唯一 tracked preimage 的 full-file SHA-256；所有 preimage schema、完整集合、mode/blob/size/hash 与 freeze commit均复算通过。
- 合并前根 HandOff 的 `product_tuple_sha256` 已从 development tuple切换为 exact candidate，frozen strict adapter调用 exact verifier重算绑定；旧 v0.1 tuple、development tuple、任意第三 tuple、缺 freeze/product lock、自洽但虚假 lock 和 lock mismatch均失败。
- 旧 predecessor product tuple 与 fresh candidate hash 不同，且无旧 PASS evidence 依赖。
- implementation→validation diff 仅含批准路径；product path bytes 与 V2-M0-02 freeze 相同。
- 两个 freeze SHA 都可由唯一 merged PR与 official post-merge main Governance `runAttempt=1` 重新选择，freeze lock字段和 GitHub API证据完全一致；不存在第二成功 run、head歧义或 worker选择。
- 五个 live workflow 分别与 candidate byte-for-byte 相同，在任何下载、SDK/emulator/device/product/scan/package step 前执行全部 identity checks。
- live workflow 只有无输入 `workflow_dispatch`，拒绝非 `main` ref、caller/external artifact，且 concurrency group 精确为 `v02-performance-v0.2.0-rc.1`、`cancel-in-progress=false`。
- frozen lock/validator 对每个 field、order、manifest byte、component baseline、PR head/merge method/merge commit/post-merge run、ancestry、workflow path、post-freeze allowlist、runAttempt 和 retry flag 的 mutation均失败；本任务 validator/schema diff为零。
- 本任务 changed paths 不含产品/Harness 逻辑，未执行 Android/KVM/benchmark。
- 独立只读复核 `P0=0/P1=0/P2=0`，Ubuntu/Windows Build/Governance 通过。

## Required Tests

- tuple canonical serialization/hash、field order、extra/missing/null/case/hash length 负例。
- HandOff development→candidate 正例，以及 old tuple、development-after-lock、arbitrary third tuple、candidate-without-lock、malformed/unverified lock、格式/hash自洽但伪造 freeze/manifests和 lock/hash mismatch负例。
- source/validation ancestry、wrong PR head/merge mode/merge commit/post-merge run/attempt/head/conclusion、unauthorized product/post-freeze path，以及七个 manifest 的 wrong preimage、空/遗漏/额外/重复/path sort/NFC/mode/symlink/gitlink/blob/hash/size/self-reference/byte drift 负例。
- 五组 candidate/live workflow byte equality、rename、duplicate/unknown workflow、wrong task/run/job/event/branch/attempt、额外 event/input、`workflow_call`、错误 concurrency/cancel 负例；release-evidence 额外拒绝 missing/expired/deleted/second/mismatched predecessor artifact、caller URL/input/cache/alternate run，其他 workflow 拒绝全部 artifact 注入。
- predecessor tuple、old run/artifact/profile 和 retry/replacement flag 注入负例。
- no-device/no-build-output changed-path assertion。

## Required Evidence

- 两 freeze commit、ancestry、changed-path manifest 和所有 input manifest hashes。
- tuple preimage bytes、byte length、SHA-256、lock 和 mutation report。
- 五组 workflow candidate/live hashes、pre-run ledger 和 permission scan。
- 两份 freeze-acceptance lock、official API snapshot/hash、唯一 SHA选择、post-freeze HEAD closure和 frozen adapter/validator零diff报告。
- 命令、退出码、OS/toolchain、时间戳、CI 和独立审查。

## Likely Files

- `docs/v0.2/evidence/V2-M3-02/`
- `docs/v0.2/evidence/V2-M0-02/implementation-freeze-lock.json`
- `docs/v0.2/evidence/V2-M3-01/validation-freeze-lock.json`
- `.github/workflows/v02-performance.yml`
- `HandOff.md`，仅 `/root` 协调更新

## Dependencies and Blockers

任一 freeze未完成、V2-M0-02未先 merge/post-merge PASS、V2-M3-01未在其后全量复核并 merge/post-merge PASS、任一 official run不是唯一 attempt 1或 head不等于merge commit、产品路径在两 freeze间变化、manifest/component/hash/ancestry不一致、workflow candidate未独立复核、frozen validator无法拒绝虚假自洽 lock或本任务需要修改 validator/schema时保持 blocked。不得生成“临时 tuple”或先运行后补 lock。

## Agent Handoff Requirements

使用分支 `docs/v2-m3-02-product-tuple-freeze`，只处理 Issue #89并创建一个对应 PR。交接必须包含 exact tuple、两份 freeze lock与官方 PR/merge/run身份、全部 input hashes、workflow equality、post-freeze closure、development→candidate状态迁移、frozen validator正负例、validator/schema零diff、mutation count、no-device证明、CI与独立审查；根 HandOff由 `/root`在同一 PR原子同步，不得在 lock之前或之后单独切换 tuple。
