---
id: M3-07
title: "Test-only HIGH benchmark contract"
milestone: M3
status: planned
owner_role: qa-governance-agent
depends_on:
  - M2-05
  - M2-06
  - M3-01
required_skills:
  - plan-apk-hardening-change
  - coordinate-project-handoff
security_sensitive: true
---

## Goal

Define the only authorized way for M3-05 to measure HIGH-profile incremental cost without adding a production risk override or falsely claiming a naturally observed HIGH cold start.

## Background

The fixed M3-05 reference devices cannot deterministically enter HIGH through an ordinary non-root Release/R8 launch. Forcing the risk engine through a production flag, debugger, injected mapping or test setter would change the measured path or create a new attacker-influenced policy surface. ADR 0014 separates real cold-start evidence from a fixture-only, authenticated same-handle HIGH upgrade.

## Inputs

- ADR 0010 environment-risk scoring and its ABI-neutral, non-blocking policy.
- ADR 0011 monotonic `PayloadRuntime.applyMemoryProfile` entry and HIGH 20–50 ms jitter.
- M3-01 synthetic Release fixtures and M3-05 benchmark requirements.

## Expected Outputs

- Accepted ADR 0014.
- Revised M3-05 and TEST_STRATEGY contracts with exact observed and isolated measurement labels.
- A governance validator and mutation negatives that prevent production overrides and false cold-start claims.

## In Scope

- Define `observed_cold_start` and `isolated_high_upgrade` semantics.
- Define the permitted Android-test source-set boundary, fixture-only reflection/keep behavior and artifact exclusion proof.
- Define raw samples, jitter/wall-time bounds, ownership, lookup and cleanup evidence required from the future M3-05 implementation.

## Out of Scope

- Runtime, Host processor, fixture or benchmark implementation.
- Emulator, KVM or physical-device execution.
- A new public/package-private production API, risk input, wire-format field or manifest control.
- Changing M2-05 scoring, M2-06 profiles or M3-05 performance budgets.

## Implementation Decisions

- Ordinary cold starts always run the unmodified production policy and carry `measurementMode=observed_cold_start` plus `riskObservationTiming=post_start` and the separately observed risk level/action. A non-LOW observation on either fixed reference profile fails environment comparability rather than being silently accepted as a LOW sample.
- The isolated bridge lives only in Android-test or a dedicated M3-05 test source set. Every sample starts in a fresh force-stopped process, opens a fresh authenticated session, obtains its owned payload using fixture-only reflection, performs one existing monotonic HIGH upgrade before lookup, verifies post-upgrade usability and closes exactly once.
- `highProfileIncrementalMs` and Native jitter are separate raw samples. Jitter remains 20–50 ms and wall-clock elapsed time is bounded by 250 ms.
- Isolated results are never relabeled or summed into a measured HIGH cold-start release gate. A naturally HIGH real launch may be reported only when the unchanged production engine actually observes it.

## Public Interfaces

- No product interface changes.
- Benchmark report fields add `measurementMode`, `observedRiskLevel`, `observedRiskAction`, `riskObservationTiming`, `highProfileIncrementalMs`, `nativeJitterMs`, `sameHandle`, `lookupCountBeforeUpgrade`, `lookupCountAfterUpgrade` and `cleanupPassed` as applicable to the selected mode. Non-Android and isolated fields that do not apply are explicit `null`, never omitted or fabricated.
- Measurement-mode values are exactly `observed_cold_start` and `isolated_high_upgrade`.

## Security Constraints

- No manifest, BuildConfig, system property, intent, file, environment variable, debugger, injected mapping or production test setter may force HIGH.
- The isolated bridge cannot bypass signer, AEAD, authenticated metadata, ownership, read-only mapping, `DONTDUMP`, lock-budget, dumpability or cleanup checks.
- Bridge classes and keep rules must be absent from Runtime AARs and production/distribution APKs.

## Compatibility Requirements

- No minimum SDK, ABI policy, wire bytes or compatibility claim changes.
- API 29 ARM64 and API 36 x86_64 remain the M3-05 reference profiles; architecture alone never selects HIGH.

## Acceptance Criteria

- ADR 0014, M3-05, TEST_STRATEGY, task index and dependency graph agree on the two measurement modes.
- Governance and mutation tests reject every prohibited production override and any isolated sample labeled as a cold start.
- No production source or public API changes appear in the task diff.
- Independent read-only security review returns P0/P1/P2 all zero before merge.

## Required Tests

- Positive structural validation for both measurement-mode contracts.
- Mutation negatives for manifest metadata, BuildConfig, system property, filesystem marker, production setter and false cold-start labels.
- Governance, strict HandOff, UTF-8/link, diff and sensitive-information checks.

## Required Evidence

- Exact commit, changed-file list, commands, exit codes, OS/tool versions and governance output.
- Mutation-test names and results.
- Independent review conclusion and exact-head Ubuntu/Windows Governance/Build runs.

## Likely Files

- `docs/adr/0014-test-only-high-benchmark-boundary.md`
- `docs/tasks/M3-07-test-only-high-benchmark-contract.md`
- `docs/tasks/M3-05-size-startup-memory-benchmarks.md`
- `docs/tasks/INDEX.md`
- `docs/TEST_STRATEGY.md`
- `tools/governance/verify-m3-07-high-benchmark-contract.mjs`
- `.github/workflows/governance.yml`

## Dependencies and Blockers

M3-05 remains blocked until this contract passes independent review and merges. This task must not implement or execute the benchmark matrix.

## Agent Handoff Requirements

Use branch `docs/m3-07-high-benchmark-contract`, Issue #61 and one documentation/governance PR. Record the exact production-surface exclusion, mutation matrix and review/CI evidence; after merge restore `chore/m3-05-performance-benchmarks` and its saved worktree.
