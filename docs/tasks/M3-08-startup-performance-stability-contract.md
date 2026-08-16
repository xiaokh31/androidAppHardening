---
id: M3-08
title: "Startup performance and measurement-stability contract"
milestone: M3
status: planned
owner_role: qa-governance-agent
depends_on:
  - M3-01
  - M3-07
required_skills:
  - plan-apk-hardening-change
  - coordinate-project-handoff
security_sensitive: true
---

## Goal

Define a bounded, drift-resistant API 36 startup comparison that can distinguish measurement instability from a real protected-startup budget failure without changing M3-05 budgets, samples or security controls.

## Background

M3-05 PR #63 produced two complete API 36 reports around a validator-only change. One passed every budget; the other exceeded the fixed `processToApplicationOnCreateMs` P50 budget by 31 ms, while six summaries varied by more than 10%. The existing baseline-first/protected-second full batches and cross-run absolute comparison do not isolate order or environment drift. ADR 0015 fixes the only replacement protocol that M3-05 may use.

## Inputs

- M3-05 task, PR #63 and its retained API 36 failure evidence.
- M3-07 and ADR 0014 observed LOW versus isolated HIGH boundary.
- The fixed API 36 revision 2 x86_64 image and Emulator 37.1.11.
- Existing 5-warmup/30-measurement counts, 300/500 ms budgets and 10% repeatability limit.

## Expected Outputs

- Accepted ADR 0015.
- Revised M3-05 and TEST_STRATEGY contracts plus task-index dependency.
- Formal `benchmark-repeatability.json` schema semantics bound to both retained campaign reports and the executing job identity.
- A governance validator with positive and mutation self-tests.
- Independent read-only review and exact-head Ubuntu/Windows Build/Governance evidence.

## In Scope

- Fix the exact two-campaign order, identity, arithmetic, fail-closed behavior and evidence fields.
- Define when a measurement-only correction may resume M3-05.
- Define when a stable remaining budget failure requires a separate Runtime optimization task.
- Prove this prerequisite changes no production or benchmark implementation.

## Out of Scope

- Runtime, Host, fixture, benchmark or workflow implementation of the future M3-05 campaign.
- Changing any budget, repeatability limit, warmup count, measured sample count or security control.
- KVM, emulator or physical-device execution.
- A production timing hook, benchmark switch, environment input, manifest field or public/package-private API.
- Starting M4 or merging PR #63.

## Implementation Decisions

- Campaign `A` is forward fixture order with `baseline_then_protected`; campaign `B` is reverse fixture order with `protected_then_baseline`.
- A and B run in one exact-head API 36 KVM job and emulator boot with identical artifact and environment fingerprints.
- Each mode in each campaign retains five warmups and thirty measured cold starts. Invalid samples fail the campaign and are never replaced.
- Both campaign reports must independently pass every unchanged M3-05 budget.
- For all three fixtures and five observed Android metrics, repeatability covers six statistics: `baselineP50`, `baselineP95`, `protectedP50`, `protectedP95`, `deltaP50`, `deltaP95`. This yields exactly ninety unique comparison rows.
- Variation uses `abs(A-B) / max(1, min(abs(A), abs(B)))` and must be at most `0.10` for every row.
- Exactly two campaigns are accepted. No third run, cross-head report, cross-job report or cross-boot result may replace a failure.
- M3-08 itself is governance-only. M3-05 may later change only its test orchestration to implement the accepted protocol. Stable remaining product budget failure requires a separate Runtime task.

## Public Interfaces

- No product interface changes.
- Formal aggregate entry requires `--report`, `--campaign-a`, `--campaign-b`, `--expected-head`, `--expected-run-id`, `--expected-job-id`, `--expected-run-attempt`, `--expected-environment`, `--expected-boot-hash`, `--artifact-manifest` and `--artifact-root`; omitting any input fails closed.
- `benchmark-repeatability.json` includes `schemaVersion`, `headSha`, `environmentId`, `runId`, `jobId`, `runAttempt`, `bootIdHashPrefix`, `artifactManifestSha256`, two campaign objects, ninety comparisons, `allBudgetsPass`, `repeatabilityPass` and `cleanupPassed`.
- Each campaign includes exact ID/order, `headSha`, `environmentFingerprint`, run/job/attempt identity, `bootIdHashPrefix`, `artifactManifestSha256`, `reportSha256`, `warmups=5`, `measurements=30`, `allBudgetsPass=true` and `cleanupPassed=true`.
- `benchmark-artifact-manifest.json` has exact head/run/job/attempt/environment/boot fields, exact A/B campaign IDs/orders/report hashes and exactly six unique baseline/protected APK IDs, canonical file names and SHA-256 values. The validator hashes the six actual files under `--artifact-root`, and rejects mismatched APK bytes, identical report bytes even under different paths, and any manifest from another job or boot.
- The validator computes both campaign report hashes, parses and validates the canonical artifact manifest before hashing it, invokes the M3-07 validator on both source reports, and independently recomputes raw-sample percentiles, deltas, budgets and all ninety aggregate rows. Declared aggregate hashes, summaries and booleans are not trusted.

## Security Constraints

- Signer, AEAD, authenticated metadata, Guard order, memory profiles, mapping protections, ABI behavior and cleanup remain enabled.
- Reports contain no device serial, user path, key, certificate private material or plaintext DEX.
- Sensitive scanning recursively inspects every report string/key and rejects device-serial fields, Windows drive/UNC paths, macOS user-home paths and Unix absolute/user paths.
- No test control enters Runtime AARs, protected production APKs, CLI or distribution artifacts.
- A production optimization is not authorized by this contract.

## Compatibility Requirements

- No minSdk, API, ABI, wire bytes or supported-fixture claim changes.
- The protocol applies only to the already fixed API 36 x86_64 M3-05 reference job.
- API 29 ARM64 remains deferred and runs once only after the replacement API 36 job passes.

## Acceptance Criteria

- ADR 0015, M3-08, M3-05, TEST_STRATEGY and INDEX agree on the exact A/B protocol and dependency.
- The formal validator accepts a complete synthetic report and rejects every required mutation.
- Base-to-HEAD validation proves zero production, fixture and benchmark implementation changes.
- Governance, strict HandOff, UTF-8/link, diff and sensitive-information checks pass.
- Independent review reports P0=0, P1=0 and P2=0 before merge.

## Required Tests

- Positive contract and aggregate-report validation.
- Negative mutations for a third campaign, wrong campaign/mode/fixture order, changed head/run/job/attempt/environment/boot/artifact/report identity, a historical-job manifest, different paths containing identical campaign bytes, missing/duplicate/renamed APK bindings, tampered APK bytes, 4/6 warmups, 29/31 samples, missing or duplicate comparison, wrong statistic, relaxed 10% limit, incorrect variation/pass/delta/budget, failed cleanup, source-report tampering and Windows/UNC/macOS/Unix path leakage.
- Positive arithmetic cases include a valid negative delta and the exact 10% boundary.
- Base-diff mutation proving a production or benchmark implementation file is rejected.

## Required Evidence

- Exact commit, changed-file list, commands, exit codes, OS, Node version and SHA-256.
- Mutation names/count and results.
- Independent review conclusion.
- Exact-head Ubuntu/Windows Build and Governance runs; no KVM/device evidence is required or allowed.

## Likely Files

- `docs/adr/0015-startup-performance-measurement-stability.md`
- `docs/tasks/M3-08-startup-performance-stability-contract.md`
- `docs/tasks/M3-05-size-startup-memory-benchmarks.md`
- `docs/tasks/INDEX.md`
- `docs/TEST_STRATEGY.md`
- `tools/governance/verify-m3-08-startup-stability-contract.mjs`
- `.github/workflows/governance.yml`
- `HandOff.md`
- `README.md`

## Dependencies and Blockers

M3-05 remains blocked until M3-08 passes independent review and merges. M3-08 must not run the benchmark matrix or alter PR #63 implementation.

## Agent Handoff Requirements

Use branch `docs/m3-08-startup-performance-stability`, Issue #64 and one PR. Record the zero-production-diff proof, mutation matrix, independent review and exact-head Build/Governance. After merge, return to PR #63 and run only the single authorized API 36 replacement job.
