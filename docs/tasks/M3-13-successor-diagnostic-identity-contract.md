---
id: M3-13
title: "Successor startup diagnostic identity and eligibility contract"
milestone: M3
status: planned
owner_role: qa-governance-agent
depends_on:
  - M3-09
  - M3-11
  - M3-12
required_skills:
  - plan-apk-hardening-change
  - coordinate-project-handoff
security_sensitive: true
---

## Goal

Define the only admissible successor identity after M3-10 consumed its first-and-only execution in repository-provenance preflight without creating an AVD, installing an APK, retaining a sample or uploading an artifact. Preserve the failed history and prevent retries, result shopping or scope expansion.

## Background

M3-10 implementation and workflow publication passed independent review, but run `32554806537` failed before Android execution because its shallow checkout lacked required M3-12 ancestry. Terminal evidence run `32554917303` proved that no diagnostic artifact existed. The original M3-10 identity is consumed and PR #79 remains terminally blocked. The user authorized a separate contract decision rather than an M3-10 retry.

## Inputs

- ADR 0016, the M3-11 product tuple and M3-12 retained profile asset.
- M3-10 reviewed publication head `790ae4579ce3562dc93f3c533ffb786a39517600`.
- Diagnostic run `32554806537`, job `96987186584`, `runAttempt=1` and zero artifacts.
- Terminal evidence run `32554917303`, job `96987454333`.
- PR #79 final documentation head `5fa20550b6f05fec8fa474df6947695f7f5f1937` as historical closure only.
- `docs/evidence/M3-13/predecessor-official-proof.json`, canonical SHA-256 `b3faa34fcee76adb5223c99ccc854fc3000133244cce5a23c8ff2d9432d0d643`.
- `docs/evidence/M3-13/diagnostic-eligibility-lock.json`.

## Expected Outputs

- Proposed ADR 0018 with the narrow zero-observation successor rule.
- Immutable predecessor/contract lock and exact preimage SHA-256.
- Exact future execution-identity serialization and two-phase workflow publication rule.
- Governance validator with positive and named mutation tests.
- README, INDEX, ROADMAP, PROJECT_PLAN, TEST_STRATEGY and HandOff alignment.
- Independent read-only review and Ubuntu/Windows Build/Governance evidence.

## In Scope

- Decide whether the proven pre-device M3-10 failure can qualify exactly one distinct successor.
- Bind official predecessor identity, zero-observation facts and terminal history.
- Require full-history ancestry qualification before workflow publication and before Android setup.
- Define one future task key, workflow pair, run limit and execution identity without adding those workflows.
- Preserve ADR 0016 measurements, budgets, arithmetic and product bytes unchanged.

## Out of Scope

- Adding or executing the successor workflow, runner, verifier or Android profile derivation.
- Running Gradle Android tasks, KVM, emulator, physical device, ARM, API 29, benchmark or M3-05.
- Modifying Runtime, Host, fixtures, profile asset, APK/DEX bytes, product security behavior or public interfaces.
- Retrying M3-10, converting PR #79 to ready, merging PR #79 or creating a third diagnostic entitlement.
- Changing 300/500 ms budgets, 30 ms owner minimum, 10% variation, 50% share, sample counts or campaign order.

## Implementation Decisions

- The exception applies only because official evidence and reviewed workflow bytes prove zero AVD creation, zero installation attempt, zero retained samples and zero artifacts.
- M3-10 remains consumed and visible. The new identity is not a replacement success record.
- The contract identity is SHA-256 `4104670bbe53aaa193740e4e34128051332657bb8dc8c65b57dd133443387faf` of the exact 1033-byte lock preimage, which includes the official-proof canonical SHA-256.
- A future execution identity binds the contract identity, product tuple, profile archive, reviewed implementation freeze, two non-executable workflow-candidate hashes, runner/verifier hashes, environment/toolchain locks and full-history qualification evidence.
- The implementation freeze contains no canonical workflow. Independent review must return `P0=0/P1=0/P2=0` before a direct-child publication copies candidate bytes unchanged into the two canonical workflow paths.
- This contract task keeps workflow presence fail-closed. A later separately authorized implementation task may introduce a successor validator mode only in its independently reviewed workflow-absent freeze, and that mode must bind the exact contract identity, candidate hashes, freeze parent, pre-run ledger and direct-child publication paths.
- The future diagnostic uses `M3-13-SUCCESSOR-DIAGNOSTIC-V1`, exactly one run, `runAttempt=1`, API 36 revision 2 x86_64 and Emulator 37.1.11.
- Full Git history and required ancestry/object checks run before Android SDK setup, AVD creation or device commands.
- Success, `UNATTRIBUTED`, cancellation, failure, missing evidence and cleanup failure all consume the successor. No further renewal is permitted for the same product tuple.

## Public Interfaces

- No product interface changes.
- Governance entry point: `node tools/governance/verify-m3-13-diagnostic-identity-contract.mjs`.
- Self-test: `node tools/governance/verify-m3-13-diagnostic-identity-contract.mjs --self-test`.
- Base-diff gate: `node tools/governance/verify-m3-13-diagnostic-identity-contract.mjs --base-ref <sha>`.

## Security Constraints

- No production or profile security control is skipped, cached, deferred or weakened.
- Caller-authored booleans cannot prove zero observation, ancestry, workflow identity or run uniqueness.
- Raw official predecessor and successor GitHub history remains part of terminal evidence.
- No secret, key, password, full signer digest, device serial, user path, plaintext DEX or customer APK enters the repository or evidence.
- The future implementation cannot create its executable workflow before all-zero independent review.

## Compatibility Requirements

- No minSdk, API, ABI, signer, container, fixture or compatibility claim changes.
- The future successor remains API 36 x86_64 only. The available unlocked ARM device is irrelevant to this contract and must not be used.

## Acceptance Criteria

- ADR 0018 and the task agree on the exact predecessor, zero-observation eligibility and one-successor/no-renewal rule.
- The official proof canonicalizes to exactly 5274 UTF-8 bytes, contains all 17 diagnostic-job and all 10 terminal-job steps, and its SHA-256 is bound into the exact 1033-byte lock preimage, which recomputes to the fixed contract SHA-256.
- The execution identity is not derivable from a workflow/task rename alone.
- The future workflow remains absent from the branch.
- M3-10 and PR #79 remain terminally blocked; M3-05 remains blocked.
- Base-to-HEAD diff contains no Runtime, Host, fixture, benchmark, profile APK, DEX, key, distribution or executable diagnostic workflow change.
- Governance, strict HandOff, UTF-8/link, diff and sensitive checks pass.
- Independent review returns `P0=0/P1=0/P2=0` before push/PR publication.

## Required Tests

- Positive exact contract/lock validation.
- Named mutations for predecessor task/path/key/head/run/job/attempt, terminal run/job, artifact count, AVD/install/sample state, product tuple, successor key/path, run limit/attempt, renewal flag, preimage length/content/hash and missing required document phrases.
- Workflow-presence negative proving the contract cannot add either executable successor workflow.
- Base-diff negatives for Runtime, Host, fixture, benchmark, profile/APK/DEX/key, distribution and workflow paths.
- Project governance and strict HandOff validation.

## Required Evidence

- Base/head commits, branch, Issue #80, changed-file list, commands, exits, OS, Node/Git versions and SHA-256 values.
- Mutation count and exact PASS/FAIL outcome.
- Independent review conclusion and finding closure.
- Exact-head Ubuntu/Windows Build/Governance after publication. Device/KVM/benchmark evidence is forbidden.

## Likely Files

- `docs/adr/0018-successor-diagnostic-execution-identity.md`
- `docs/tasks/M3-13-successor-diagnostic-identity-contract.md`
- `docs/evidence/M3-13/diagnostic-eligibility-lock.json`
- `docs/evidence/M3-13/predecessor-official-proof.json`
- `tools/governance/verify-m3-13-diagnostic-identity-contract.mjs`
- `tools/governance/verify-m3-08-startup-stability-contract.mjs`
- `tools/governance/verify-m3-09-startup-attribution-contract.mjs`
- `tools/governance/validate-project-package.mjs`
- `.github/workflows/governance.yml`
- `docs/tasks/INDEX.md`
- `docs/ROADMAP.md`
- `docs/PROJECT_PLAN.md`
- `docs/TEST_STRATEGY.md`
- `README.md`
- `HandOff.md`

## Dependencies and Blockers

M3-13 depends on completed M3-09, M3-11 and M3-12 and treats terminal M3-10 as an input, not a completed dependency. A later successor implementation task remains blocked until M3-13 is independently reviewed, merged and its exact-head Ubuntu/Windows Build/Governance pass. M3-05 remains blocked until a valid successor result selects one owner and a separate remediation completes.

## Agent Handoff Requirements

Use branch `docs/m3-13-diagnostic-identity-contract`, Issue #80 and one PR. Record the immutable lock, mutation tests, zero-implementation diff and independent review. State explicitly that M3-10 was not retried and no workflow, Android, KVM, emulator, physical device, ARM, API 29, benchmark or M3-05 run occurred.
