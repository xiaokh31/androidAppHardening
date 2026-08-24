---
schema_version: 2
project: androidAppHardening
release_line: v0.2
product_tuple_sha256: 5fb0205d9fc0c2523cd33734145bf23a901303f4f563eef866e5883ca81fd4c2
handoff_id: HO-20260824-113127
updated_at: 2026-08-24T11:31:27+08:00
updated_by: /root
state: active
source_branch: main
base_commit: 7c838d7051e8eedb1607e57b6e81e7a6f3db4523
working_tree: clean
current_milestone: V2-M0
active_task: V2-M0-01
next_owner: /root
---

# Project HandOff

## Objective

Complete the governance-only V2-M0-01 transition: preserve the terminal v0.1 release line as immutable history while establishing a distinct, non-releasable v0.2 development baseline, task graph and validation entry point.

## Current State

- The user authorized v0.2 planning on 2026-08-24. V2-M0-01 is tracked by Issue #86 and branch `docs/v2-m0-01-versioned-baseline`; it does not authorize Host, Runtime, benchmark, distribution or release execution.
- The v0.2 source baseline is `main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523`. Development tuple `5fb0205d9fc0c2523cd33734145bf23a901303f4f563eef866e5883ca81fd4c2` has `tuple_kind=development_baseline` and `releasable=false`; it is not a Release Candidate.
- ADR 0019 remains binding with `STOP_CURRENT_V0_1_RELEASE_LINE`. Its terminal product tuple is `883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd`; M3-05 is terminally blocked, and the v0.1 M4 path is not startable.
- M3-13 is complete on `main`; Issue #80 is closed and PR #81 merged. No device, KVM, emulator, ARM, API 29 or benchmark was run by V2-M0-01.
- M3-09 is complete on `main`. At that checkpoint PR #63 remained blocked. M3-05 PR #63 remains blocked in the historical record, and ADR 0019 has since closed it unmerged.
- PR #63 and PR #83 remain closed and unmerged. The old diagnostic cannot be retried, rerun, replaced, renewed or subjected to platform substitution, and no old run, APK, profile or artifact can satisfy a v0.2 PASS gate.

## Active Workstreams

| Task | Owner | Branch | Status | Depends on | Next action |
| --- | --- | --- | --- | --- | --- |
| V2-M0-01 | `/root` | `docs/v2-m0-01-versioned-baseline` | in_progress | None | Complete independent review, exact diff validation and dual-platform Governance before merge. |
| M3-13 | `/root` | `main` | done | M3-09, M3-11, M3-12 | Preserve Issue #80, PR #81 and the no-further-successor evidence as historical facts. |
| M3-15 | `/root` | `main` | done | M3-13 | Preserve ADR 0019, terminal lock and the stopped v0.1 route without byte rewriting. |

## Decisions and Invariants

- ADR 0020 creates a new versioned release line; it does not resume, rename or replace v0.1 M3-05, M3-10, M3-14 or M4.
- v0.2 continues to accept only one standalone APK, keeps input bytes read-only and produces a new unsigned APK. Product code never receives or uses signing secrets.
- The input contract remains `minSdk >= 29`. Runtime builds `armeabi-v7a`, `arm64-v8a`, `x86` and `x86_64`, but does not convert an ARM-only application into an x86 application.
- AAB, APKS, split APK, dynamic features, Flutter, Unity, React Native, hotfix, plugin frameworks and existing protection shells remain outside scope.
- DEX-memory, anti-debug, environment and offline-key controls only increase attack cost; they do not provide absolute prevention.
- Only merged source and test assets may be inherited. Every v0.2 release PASS requires fresh evidence bound to one new exact candidate tuple and commit.
- Schema 2 HandOff uses the exact development tuple until V2-M3-02 atomically commits a verified RC lock and switches the frontmatter to that lock's exact candidate tuple. V2-M3-01 must first freeze the candidate-neutral aggregate verifier and candidate-state adapters; V2-M3-02 may execute them but may not modify them. Old, arbitrary, missing-lock, self-certified and mismatched identities fail closed.
- V2 dependencies name only V2 tasks. The v0.1 baseline appears only as an immutable `baseline_inputs` source.

## Changes Since Previous Handoff

- Replaced the cumulative schema 1 blocked snapshot with schema 2 release-line state while retaining the exact v0.1 terminal decision, old tuple and prohibited actions.
- Added ADR 0020, the versioned v0.2 product/architecture/threat/test/compatibility package, a canonical development tuple and nine implementation-ready task cards under `docs/v0.2/`.
- Added GitHub Milestones for v0.2 M0 through M4 and Issues #86 through #94, one per task card; V2-M1 and V2-M2 remain intentionally empty unless a separately authorized product repair is required.
- Migrated M3-15 validation to verify terminal bytes from the fixed historical Git object and to keep current terminal semantics fail closed.
- Added schema 2 HandOff validation and a dedicated v0.2 task/package validator with negative mutations and governance-only diff enforcement.
- Added machine-readable identity/path and post-freeze policies that fix every manifest selector and role, candidate/live workflow path, evidence-stage order, explicit `pre-run|evidence-pr|post-merge` phase and per-stage changed-path closure.
- Closed the release supply-chain contract around pinned source profiles, pagination/ETag freshness, a zero-byte OSV configuration, an exact cross-platform environment allowlist and manifest-bound raw evidence.

## Verification Evidence

### M3-13 terminal contract

- task_id: M3-13
- git_commit: 621117dc5639bf4c9c9e8696c554bbd2ab821d8c
- command: `node tools/governance/verify-m3-13-diagnostic-identity-contract.mjs --self-test`
- exit_code: 0
- environment: `Windows 10 Pro 10.0.19045 x86_64; Node.js 24.12.0; repository main history`
- timestamp: 2026-08-24T11:31:27+08:00
- artifact: `docs/evidence/M3-13/diagnostic-eligibility-lock.json`
- sha256: d394b99d5060fcbb1e0ad93c179648cd0932128bfdb8030635f6299b2a0f9352
- result: historical terminal contract remains valid; it is not v0.2 release evidence

### M3-15 terminal disposition

- task_id: M3-15
- git_commit: c48bfdd7dbad659db38c5c8c34befe7235f25a62
- command: `node tools/governance/verify-m3-15-terminal-disposition-contract.mjs --self-test`
- exit_code: 0
- environment: `Windows 10 Pro 10.0.19045 x86_64; Node.js 24.12.0; repository main history`
- timestamp: 2026-08-24T11:31:27+08:00
- artifact: `docs/evidence/M3-15/terminal-disposition-lock.json`
- sha256: 5a46e8aeaa2ad45b7f58dc60f5557581f1972bbdfc7ea7099b6a5a3bc67bb3a2
- result: STOP_CURRENT_V0_1_RELEASE_LINE remains immutable and cannot satisfy a v0.2 gate

## Blockers and Required Approvals

None

## Ordered Next Actions

1. V2-M0-01 `/root`: finish local governance, schema, link, secret and zero-product-diff checks; retain command output and hashes.
2. V2-M0-01 independent reviewer: audit historical-lock migration, tuple separation, dependency ordering, task-card semantics and negative tests; require `P0=0/P1=0/P2=0` before merge.
3. V2-M0-01 `/root`: publish the single PR for Issue #86, require Ubuntu and Windows Governance on the exact head, merge only after all gates pass, then update the main HandOff.
4. V2-M0-02 and V2-M3-01 `unassigned`: after V2-M0-01 post-merge PASS, develop in parallel with non-overlapping ownership; merge V2-M0-02 first and finish its post-merge PASS, then refresh V2-M3-01 on that main, rerun all gates and independent review, and merge it second.
5. V2-M3-02 `/root`: start only after the ordered freezes prove `implementationFreezeSha` is an ancestor of `validationFreezeSha`; run the V2-M3-01-frozen aggregate verifier and adapters, atomically create both freeze locks and the verified RC lock, switch HandOff from development to the exact candidate tuple, and publish—but do not dispatch—the no-input, main-only canonical `workflow_dispatch` workflows. Do not modify validators, schemas or policy.

## Relevant Files and Artifacts

- `docs/adr/0019-terminal-diagnostic-disposition.md`
- `docs/adr/0020-v0-2-product-baseline-and-version-isolation.md`
- `docs/evidence/M3-15/terminal-disposition-lock.json`
- `docs/v0.2/README_FIRST.md`
- `docs/v0.2/development-product-tuple.json`
- `docs/v0.2/tasks/INDEX.md`
- `tools/governance/verify-m3-15-terminal-disposition-contract.mjs`
- `tools/governance/validate-v0-2-package.mjs`
- `.agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs`
- `.github/workflows/governance.yml`

## Resume Checklist

1. Confirm `git status --short --branch`, HEAD, origin and the V2-M0-01 Issue/PR identity.
2. Verify the development tuple canonical hash and that it differs from the terminal v0.1 tuple.
3. Run M3-13 and M3-15 positive/self-tests, then `node tools/governance/validate-v0-2-package.mjs --self-test`.
4. Run `node tools/governance/validate-project-package.mjs` and the strict HandOff validator for the actual branch context.
5. Confirm the diff contains no Host, Runtime, fixture, integration-test, benchmark, distribution, Gradle product or diagnostic workflow changes.
6. Confirm all nine V2 Issue links, dependencies, independent-review status and exact-head CI before changing task state.

## Handoff Sign-off

- coordinator: `/root`
- prepared_at: `2026-08-24T11:31:27+08:00`
- validation: `V2-M0-01 local and independent evidence must be recorded before merge; no product or Android execution is authorized by this snapshot.`
