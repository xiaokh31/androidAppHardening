---
id: M3-09
title: "End-to-end startup attribution boundary contract"
milestone: M3
status: planned
owner_role: qa-governance-agent
depends_on:
  - M3-08
required_skills:
  - plan-apk-hardening-change
  - coordinate-project-handoff
security_sensitive: true
---

## Goal

Replace the incomplete Runtime-only performance attribution boundary with a reconcilable end-to-end startup model that distinguishes product Runtime, platform residual, application lifecycle and rendering cost without rerunning M2-10 or weakening M3-05.

## Background

M3-05 retained a stable protected-startup failure of 331 ms to `Application.onCreate` and 432 ms to interactivity. M2-10 run `32099991400` validly measured only the nested signer-to-bootstrap `t0..t6` interval and selected no eligible stage. The missing time was never measured by that contract, so neither the old result nor another identical run can decide the owner of the end-to-end delta. ADR 0016 defines the replacement attribution boundary.

## Inputs

- M3-05 PR #63 retained API 36 failure evidence and unchanged 300/500 ms budgets.
- M2-10 PR #67, its first-and-only run `32099991400`, and its no-eligible-stage conclusion.
- ADR 0014/0015 and M3-07/M3-08 test-only and stability boundaries.
- Production Shell/Guard lifecycle ordering and the existing `kotlin-multidex` Release/R8 fixture.
- Fixed API 36 revision 2 x86_64 image and Emulator 37.1.11 as future diagnostic inputs only.

## Expected Outputs

- Proposed ADR 0016 with exact common outer and protected inner timelines.
- M3-09 task/dependency, M3-05, TEST_STRATEGY, ROADMAP, PROJECT_PLAN and README alignment.
- `verify-m3-09-startup-attribution-contract.mjs` with positive and mutation self-tests.
- A zero-production/fixture/benchmark/diagnostic-workflow-diff proof for this governance task.
- Independent read-only review and exact-head Ubuntu/Windows Build/Governance evidence.

## In Scope

- Decide exact timestamps, adjacency, reconciliation, owner labels and eligibility arithmetic.
- Preserve the completed M2-10 run as valid inner-span evidence while preventing a retry.
- Specify one bounded future API 36 diagnostic and its immutable identity/evidence rules.
- Define how a future result selects Runtime, platform/artifact, lifecycle/rendering or `UNATTRIBUTED` ownership.
- Update governance and coordination documents only.

## Out of Scope

- Runtime, Host, fixture, benchmark, diagnostic workflow or profile-observer implementation.
- Running Gradle device tasks, KVM, emulator, ARM, M3-05, Macrobenchmark or another M2-10 diagnostic.
- Changing production security controls, budgets, limits, samples, public APIs, wire format, minSdk or ABI claims.
- Resuming or merging M3-05 PR #63, or implementing the later owner-specific optimization.

## Implementation Decisions

- Common checkpoints are exactly `p0..p15` from process start through first interactive; each stage is one adjacent difference and all stages reconcile to the same outer interval.
- The protected `p0..p1` interval is exactly decomposed by `p0,h0,h1..h7,h8,p1`; `h1..h7` are the existing M2-10 `t0..t6` timestamps.
- Runtime owns only `h0..h8`. `p0..h0` and `h8..p1` remain explicit platform residuals.
- Baseline retains its real default startup path; no synthetic or no-op `AppComponentFactory` may be inserted.
- A future diagnostic uses only `kotlin-multidex`, campaigns A/B with reversed mode order, five warmups and fifteen retained samples per mode, in one exact-head API 36 job and boot.
- The P50 is nearest-rank element eight of fifteen sorted retained values. Missing, duplicate, reordered or replacement samples fail closed.
- Eligibility requires both campaigns to show a positive P50 contribution of at least 30 ms, variation at most 10%, and at least 50% of the positive process-to-Application P50 delta.
- The only future run is immutable by workflow path/head/run/job/attempt/boot and raw artifact hashes. Failure or `UNATTRIBUTED` blocks; it never authorizes a substitute run on identical product bytes.

## Public Interfaces

- No product interface changes.
- Future diagnostic report semantics are contractual only; this task does not add a CLI or workflow.
- Canonical report identity includes `schemaVersion`, `headSha`, `workflowPath`, `runId`, `jobId`, `runAttempt`, `bootIdHashPrefix`, `fixtureId`, campaign and mode order, APK hashes, trace hashes, raw-sample hashes, cleanup, all timestamps, adjacent stages, reconciled totals, owner summaries and selected owner.
- Reports contain no device serial, user path, full signer digest, key material, plaintext DEX, raw logcat or unrestricted stack trace.

## Security Constraints

- The real Release/R8 signer, AEAD, Guard, metadata, ClassLoader publication, memory control and cleanup path remains enabled.
- A future observer is profile/test-only and must be absent from Runtime AARs, production fixture APKs, Host/CLI and distribution outputs.
- No production manifest, BuildConfig, system property, environment variable, intent, file toggle or public/package-private Runtime timing API is permitted.
- Performance work may not cache, skip, defer or weaken a trust decision.
- M2-10 run `32099991400` cannot be replaced, reclassified or omitted from the decision record.

## Compatibility Requirements

- No minSdk, API, ABI, wire bytes, signer policy or supported-fixture claim changes.
- The future diagnostic is API 36 x86_64 only and makes no device/ABI compatibility claim.
- M3-05 and ARM stay blocked until a separately authorized implementation task completes and any owner-specific remediation passes review.

## Acceptance Criteria

- ADR 0016 defines the exact `p0..p15` and `p0,h0,h1..h7,h8,p1` sequences without gaps or overlaps.
- M3-09, M3-05, TEST_STRATEGY, ROADMAP, PROJECT_PLAN and INDEX agree on dependency and blocked state.
- The governance validator accepts the frozen contract and rejects every required mutation.
- Base-to-HEAD diff contains no production, fixture, benchmark or diagnostic workflow implementation.
- Governance, strict HandOff, UTF-8/link, diff and sensitive-information checks pass.
- Independent review returns P0=0/P1=0/P2=0 before merge.

## Required Tests

- Positive static contract validation and base-diff validation.
- Mutations for missing/reordered/duplicate outer or inner checkpoint, non-adjacent stage, sum mismatch, cross-clock timestamp, synthetic baseline Factory, changed fixture/API/image/emulator, changed campaign order, 4/6 warmups, 14/16 samples, non-nearest-rank P50, sample deletion/replacement, changed 30 ms/10%/50% thresholds, missing run/job/attempt/boot/raw hash, replacement-run wording, Release timing surface and weakened M3-05 budget.
- Dependency mutation proving M3-05 cannot bypass M3-09.

## Required Evidence

- Exact commit/base, changed-file list, commands, exit codes, OS, Node version and file SHA-256 values.
- Governance validator mutation count and result.
- Zero-production-diff proof.
- Independent review conclusion.
- Exact-head Ubuntu/Windows Build and Governance runs. KVM, emulator, ARM and benchmark evidence are forbidden for M3-09.

## Likely Files

- `docs/adr/0016-end-to-end-startup-attribution-boundary.md`
- `docs/tasks/M3-09-startup-attribution-boundary-contract.md`
- `docs/tasks/M3-05-size-startup-memory-benchmarks.md`
- `docs/tasks/INDEX.md`
- `docs/TEST_STRATEGY.md`
- `docs/ROADMAP.md`
- `docs/PROJECT_PLAN.md`
- `tools/governance/verify-m3-09-startup-attribution-contract.mjs`
- `tools/governance/validate-project-package.mjs`
- `.github/workflows/governance.yml`
- `HandOff.md`
- `README.md`

## Dependencies and Blockers

M3-09 depends on completed M3-08. M2-10 remains blocked and its first-and-only diagnostic remains final for the old boundary. M3-05 remains blocked after M3-09 until the coordinator creates and completes a separate implementation task for the ADR 0016 diagnostic and any resulting owner-specific remediation.

## Agent Handoff Requirements

Use branch `docs/m3-09-startup-attribution-boundary`, Issue #68 and one PR. Record the accepted ADR, validator mutations, zero-production diff, independent review and exact-head Ubuntu/Windows Build/Governance. State explicitly that no KVM, emulator, ARM, benchmark, M2-10 retry or production optimization ran.
