---
id: M2-10
title: "Runtime startup critical-path optimization"
milestone: M2
status: planned
owner_role: runtime-security-agent
depends_on:
  - M2-01
  - M2-02
  - M2-03
  - M2-05
  - M2-06
  - M3-08
required_skills:
  - plan-apk-hardening-change
  - implement-runtime-protection
  - validate-protected-apk
  - coordinate-project-handoff
security_sensitive: true
---

## Goal

Reduce the protected Runtime startup critical-path cost demonstrated by the final ADR 0015 API 36 A/B failure without weakening any security control, compatibility claim, release budget or evidence rule.

## Background

M3-05 PR #63 produced one complete, identity-bound A/B pair. The `java-single-dex` process-to-`Application.onCreate` P50 delta failed the unchanged 300 ms budget in both campaign orders at 331 ms and 432 ms. Campaign B also failed process-to-interactive P50, and 25/90 repeatability comparisons failed. ADR 0016 defines a bounded diagnostic and implementation boundary; this task does not re-run or reinterpret the failed pair.

## Inputs

- ADR 0016 and the retained M3-05 run `31931428130` evidence.
- M2-01 Shell, M2-02 Native loader, M2-03 signer/Guard, M2-05 risk engine and M2-06 memory controls.
- M3-01 `java-single-dex` Release/R8 fixture.
- M3-08/ADR 0015 fixed budgets, sample counts and evidence identity rules.
- Pinned API 29 revision 8 and API 36 revision 2 x86_64 KVM images and Emulator 37.1.11.

## Expected Outputs

- Accepted ADR 0016.
- Exact-head `runtime-startup-stages.json` attribution evidence and task-specific fail-closed validator.
- One measured production Runtime optimization with byte/behavior regression coverage.
- Release-surface proof showing no timing observer or benchmark control in Runtime artifacts.
- Ubuntu/Windows Build/Governance, API 29/36 x86_64 KVM and independent security-review evidence.

## In Scope

- Test-only attribution of the contiguous `signer_source`, `binding_precheck`, `payload_open`, `metadata_policy`, `session_commit` and `bootstrap_factory` stages on the real first protected startup transaction.
- One production optimization inside the single stage that meets ADR 0016 eligibility.
- Internal Java/JNI/C++ changes required by that optimization.
- Targeted positive, tamper, failure, OOM, ownership, cleanup, zeroization and Release/R8 regressions.
- Four-ABI compilation and exported-symbol/API surface comparison.

## Out of Scope

- Changing M3-05 budgets, warmups, samples, percentiles or the 10% repeatability limit.
- Running the M3-05 API 36 A/B benchmark or any ARM campaign.
- Persistent/cross-process trust caches, remote state, profile-guided downloads or device-specific production branches.
- Skipping, deferring or sampling signer, AEAD, authenticated metadata, Guard, risk or memory controls.
- Container-format, CLI, Host processor, fixture behavior, manifest or public API changes.
- Starting M4 or merging PR #63.

## Implementation Decisions

- Branch is `fix/m2-10-runtime-startup-performance`; Issue #66 and one PR own the task.
- The initial diagnostic is the first and only run for the frozen pre-optimization product head: one API 36 x86_64 GitHub run, one job, `runAttempt=1`, one emulator boot, five warmups and fifteen retained `java-single-dex` protected-start measurements. It is diagnostic only and cannot satisfy M3-05.
- The report, artifact manifest and raw-sample file bind exact `headSha`, `runId`, `jobId`, `runAttempt=1`, `environmentId`, `bootIdHashPrefix`, baseline/profiling APK hashes, report hash, raw-sample hash and cleanup. Invalid/incomplete/timeout/no-eligible-stage results block M2-10 and cannot be replaced by another run, attempt, job, boot or cosmetic product-head change.
- A dedicated test-only profiling variant records the real first `AppComponentFactory` startup transaction using one in-process `SystemClock.elapsedRealtimeNanos()` clock. It fixes `t0` before `RuntimeSignerVerifier.verify`, `t1` after signer/package hashing, `t2` after binding/pre-read signer verification, `t3` after `PayloadRuntime.openVerified`, `t4` after metadata/risk/memory controls, `t5` before successful Guard return after session construction and `t6` after committed `BootstrapResult.ready` before bootstrap return.
- Stage durations are exactly the six adjacent differences: `signer_source=t1-t0`, `binding_precheck=t2-t1`, `payload_open=t3-t2`, `metadata_policy=t4-t3`, `session_commit=t5-t4`, and `bootstrap_factory=t6-t5`. They are non-negative, gap-free and non-overlapping, and their exact sum equals `t6-t0` for the same startup. Manual second opens, post-launch timing, Host/Native-only microbenchmarks and cross-process fragments are rejected.
- Retained sample IDs are the acquisition order `1..15`. Partition `early` is `1..7`; partition `late` is `8..15`; no sample may be omitted, duplicated, reordered or reassigned. Each partition P50 uses nearest-rank one-based index `ceil(0.50*n)`, selecting the fourth sorted value for both partitions.
- Production changes begin only if one stage contributes at least 30 ms P50 in both fixed partitions. The evidence names that stage and the selected redundant work; otherwise the task becomes blocked.
- Only one stage is optimized. Removing duplicate parsing/allocation/copying/synchronization is permitted only when authenticated inputs, output bytes, failure categories and cleanup remain identical.
- Every cold process performs full signer/source verification. No disk, preference, Binder, static-file or cross-process cache may turn a prior verification into current trust.
- No observer is added to `src/main` product APIs, JNI exports, manifest, environment inputs or Runtime logs. It cannot change production control flow or return values; test source sets and ignored evidence directories own measurement controls, and Release AAR/JAR/ELF/package scans prove complete absence.
- API 29/36 KVM runs the existing Runtime/fixture/tamper/cleanup matrix once on the frozen implementation head. ARM and the M3-05 A/B job remain forbidden in this task.
- README is updated at task completion as required by repository policy; during implementation it reports M2-10 as active and M3-05 as blocked.

## Outcome

The first and only diagnostic ran on exact head `977b0585b5a0b3c5f1270ffb39be8e4e1ef6a03f` as GitHub run `32099991400`, job `95598521722`, attempt `1`, on one API 36 x86_64 boot. All 5 warmups and 15 retained samples, identity fields, hashes and cleanup were valid, but `eligibleStages` was empty: no stage reached the fixed 30 ms P50 threshold in both the `1..7` and `8..15` partitions. M2-10 is therefore blocked by its accepted contract. No production Runtime optimization, replacement diagnostic, ARM run or M3-05 A/B run is permitted without a new independently reviewed decision.

Immutable results are archived in `docs/evidence/M2-10/remote-diagnostic.md`.

## Public Interfaces

- No new or changed public Java/Kotlin API.
- No new or changed JNI export.
- No new CLI/report field outside the test-only `runtime-startup-stages.json` evidence.
- No container, ConfigV2, signer-policy, risk-policy or manifest change.

## Security Constraints

- Signer/source verification precedes payload plaintext publication in every process.
- Every AHDC chunk is authenticated before inflate consumption; authenticated metadata and same-handle ownership remain mandatory.
- Guard failure occurs before class/resource lookup and retains stable error categories.
- Anonymous direct DEX mappings remain read-only and `DONTDUMP`; key/compressed temporary clearing and lock-page budgets remain intact.
- Failure and OOM paths close the Native handle and mappings exactly once without publishing a session.
- Reports and logs exclude device serials, user paths, certificate/private material, keys and plaintext DEX.

## Compatibility Requirements

- `minSdk >= 29` and public `AppComponentFactory.instantiateClassLoader()` behavior remain unchanged.
- Standard/no-Factory/custom-Factory, Java/Kotlin, single/multidex and JNI fixtures retain their lifecycle and class-loader semantics.
- Runtime continues to build `armeabi-v7a`, `arm64-v8a`, `x86` and `x86_64` without claiming x86 compatibility for ARM-only apps.
- API 29 and API 36 x86_64 regression matrices must pass before review; no ARM evidence is generated by this task.

## Acceptance Criteria

- ADR 0016, M2-10, M3-05, INDEX, ROADMAP and TEST_STRATEGY agree on scope and ordering.
- The attribution validator accepts exactly one canonical report and rejects run/job/attempt/boot, report/manifest/raw hash, stage-boundary/reconciliation, sample partition/P50, eligibility and sensitive-data mutations.
- A measured eligible stage and exactly one corresponding production optimization are documented.
- Protected output behavior, tamper rejection, stable failures, ownership, cleanup, zeroization and no-plaintext-on-disk checks remain unchanged.
- Release AAR/JAR and four ABI ELF scans find no diagnostic API/control or new JNI export.
- Exact-head Ubuntu/Windows Build/Governance and API 29/36 x86_64 KVM pass.
- Independent review returns P0=0, P1=0 and P2=0.

## Required Tests

- Diagnostic positive plus second report/run, historical job, different attempt/boot, wrong head/environment/APK/report/raw hash, missing/duplicate/reordered stage or sample ID, non-monotonic `t0..t6`, gap/overlap/incorrect sum, manual second-open/cross-process source, 4/6 warmups, 14/16 measurements, altered `1..7`/`8..15` partition, omitted/duplicated/reassigned sample, alternative P50 algorithm and below-threshold eligibility negatives.
- Targeted unit/Native equivalence tests around the optimized stage.
- Signer mismatch/rotation, authenticated-config/container tamper, AEAD/tag failure and package-binding negatives.
- Failure injection and OOM at every changed ownership window, with no lookup/session publication and exactly-once cleanup.
- Release/R8 API 29/36 x86_64 lifecycle, cross-DEX, JNI, metadata, memory-control and no-plaintext-DEX checks.
- Four-ABI build, JNI export list, Release API surface and sensitive-log/artifact scans.

## Required Evidence

- Exact commit, parent, changed-file list, commands, exit codes, OS/JDK/Node/NDK/CMake/Android image versions and timestamps.
- Diagnostic report, artifact manifest, raw retained samples, exact run/job/attempt/boot identity, APK/report/raw-sample SHA-256 values, fixed partition/P50 calculation and selected-stage rationale.
- Unit/Native/Release/R8/four-ABI artifact hashes.
- Exact-head Build/Governance and API 29/36 KVM run/job/artifact identities.
- Independent review conclusion and final P0/P1/P2 counts.

## Likely Files

- `docs/adr/0016-runtime-startup-critical-path-optimization.md`
- `docs/tasks/M2-10-runtime-startup-performance.md`
- `runtime/policy/**`
- `runtime/native/**`
- `runtime/bootstrap/**`
- `tools/validation/verify-m2-10-runtime-startup-performance.mjs`
- `.github/workflows/m0-05-linux-kvm.yml`
- `docs/evidence/M2-10/**`
- `README.md`
- `HandOff.md`

## Dependencies and Blockers

M3-05 PR #63, ARM and M4 remain blocked until M2-10 passes independent review and merges. If the diagnostic identifies no eligible stage, or the optimization would require weakening a security or compatibility invariant, M2-10 stops blocked and returns to `/root` for a new decision.

## Agent Handoff Requirements

Use Issue #66, branch `fix/m2-10-runtime-startup-performance` and one PR. Freeze diagnostic evidence before production optimization, freeze implementation before independent review, and clearly separate inherited evidence from exact-head evidence. Do not run ARM or M3-05 A/B in this task.
