---
schema_version: 2
project: androidAppHardening
release_line: v0.2
product_tuple_sha256: 5fb0205d9fc0c2523cd33734145bf23a901303f4f563eef866e5883ca81fd4c2
handoff_id: HO-20260825-114330
updated_at: 2026-08-25T11:43:30+08:00
updated_by: /root
state: ready
source_branch: main
base_commit: 9ac8d6328c7a01c5645428e6b59b8c4db19d78ff
working_tree: clean
current_milestone: V2-M0
active_task: V2-M0-02
next_owner: unassigned
---

# Project HandOff

## Objective

Continue the isolated v0.2 release line from the non-releasable development baseline: assign V2-M0-02, allow V2-M3-01 parallel development, and preserve the fixed merge/freeze order before creating any Release Candidate.

## Current State

- V2-M0-01 is complete. Reviewed content commit `102cb775b205e1b592934e74a6d5d19e73ad2b3f` retained `P0=0/P1=0/P2=0`; final PR head `73b76c7b06222eac621227a8c8866c8cf6fac3e9` passed Governance run `32695937803` attempt 1 on Ubuntu and Windows, and PR #95 merged with a two-parent merge commit as `9ac8d6328c7a01c5645428e6b59b8c4db19d78ff`. Issue #86 is closed. The unique post-merge `main` push Governance run `32696330686` attempt 1 passed Ubuntu job `97338981876` and Windows job `97338982010`; strict local project, v0.2, HandOff, diff and Git-object gates also pass.
- The v0.2 source baseline is `main@7c838d7051e8eedb1607e57b6e81e7a6f3db4523`. Development tuple `5fb0205d9fc0c2523cd33734145bf23a901303f4f563eef866e5883ca81fd4c2` has `tuple_kind=development_baseline` and `releasable=false`; it is not a Release Candidate.
- ADR 0019 remains binding with `STOP_CURRENT_V0_1_RELEASE_LINE`. Its terminal product tuple is `883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd`; M3-05 is terminally blocked, and the v0.1 M4 path is not startable.
- M3-13 is complete on `main`; Issue #80 is closed and PR #81 merged. No device, KVM, emulator, ARM, API 29 or benchmark was run by V2-M0-01.
- M3-09 is complete on `main`. At that checkpoint PR #63 remained blocked. M3-05 PR #63 remains blocked in the historical record, and ADR 0019 has since closed it unmerged.
- PR #63 and PR #83 remain closed and unmerged. The old diagnostic cannot be retried, rerun, replaced, renewed or subjected to platform substitution, and no old run, APK, profile or artifact can satisfy a v0.2 PASS gate.

## Active Workstreams

| Task | Owner | Branch | Status | Depends on | Next action |
| --- | --- | --- | --- | --- | --- |
| V2-M0-01 | `/root` | `main` | done | None | Preserve PR #95, Issue #86, merge `9ac8d6328c7a01c5645428e6b59b8c4db19d78ff` and exact-head/post-merge Governance identities as the accepted baseline transition. |
| V2-M0-02 | `unassigned` | `not_created` | planned | V2-M0-01 | Assign `host-pipeline-agent` and create `chore/v2-m0-02-candidate-baseline`; do not create or claim an `implementationFreezeSha` before its required merge and post-merge acceptance. |
| V2-M3-01 | `unassigned` | `not_created` | planned | V2-M0-01 | Assign `qa-governance-agent`; keep ownership disjoint from V2-M0-02 and merge only after V2-M0-02. |
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
- Added ADR 0020, the versioned v0.2 product/architecture/threat/test/compatibility package, a canonical development tuple and nine implementation-ready task cards under `docs/v0.2/`; reviewed content head is `102cb775b205e1b592934e74a6d5d19e73ad2b3f`, final PR head is `73b76c7b06222eac621227a8c8866c8cf6fac3e9`, and PR #95 is merged as `9ac8d6328c7a01c5645428e6b59b8c4db19d78ff`.
- Added GitHub Milestones for v0.2 M0 through M4 and Issues #86 through #94, one per task card; V2-M1 and V2-M2 remain intentionally empty unless a separately authorized product repair is required.
- Migrated M3-15 validation to verify terminal bytes from the fixed historical Git object and to keep current terminal semantics fail closed.
- Added schema 2 HandOff validation and a dedicated v0.2 task/package validator with negative mutations and governance-only diff enforcement.
- Added machine-readable identity/path and post-freeze policies that fix every manifest selector and role, candidate/live workflow path, evidence-stage order, explicit `pre-run|evidence-pr|post-merge` phase and per-stage changed-path closure.
- Closed the release supply-chain contract around pinned source profiles, pagination/ETag freshness, a zero-byte OSV configuration, an exact cross-platform environment allowlist and manifest-bound raw evidence.
- Remediated every independent-review finding and recorded final local and read-only audit evidence under `docs/v0.2/evidence/V2-M0-01/`; the stable review result is `P0=0/P1=0/P2=0`.
- Accepted the exact-head and post-merge lifecycle after PR Governance run `32695937803`, merge commit `9ac8d6328c7a01c5645428e6b59b8c4db19d78ff`, post-merge Governance run `32696330686`, Issue #86 closure and strict local post-merge validation all passed.

## Verification Evidence

### V2-M0-01 exact-head merge and post-merge Governance

- task_id: V2-M0-01
- git_commit: 9ac8d6328c7a01c5645428e6b59b8c4db19d78ff
- command: `gh pr view 95 --repo xiaokh31/androidAppHardening`; `gh run view 32695937803 --repo xiaokh31/androidAppHardening`; `gh run view 32696330686 --repo xiaokh31/androidAppHardening`; `gh issue view 86 --repo xiaokh31/androidAppHardening`
- exit_code: 0
- environment: `GitHub Actions Governance; pull_request final head 73b76c7b06222eac621227a8c8866c8cf6fac3e9; main push head 9ac8d6328c7a01c5645428e6b59b8c4db19d78ff`
- timestamp: 2026-08-24T14:13:39+08:00
- artifact: `.github/workflows/governance.yml`; PR #95; runs `32695937803` and `32696330686`
- sha256: 91af283d0780d77e37fddf1fd7c4809d736129052e0fb76ae692db0b0d71929e
- result: PASS; PR and post-merge runs are attempt 1 and success, with Ubuntu/Windows jobs `97337882906`/`97337882698` and `97338981876`/`97338982010`; Issue #86 is closed

### V2-M0-01 strict local post-merge validation

- task_id: V2-M0-01
- git_commit: 9ac8d6328c7a01c5645428e6b59b8c4db19d78ff
- command: `node tools/governance/validate-project-package.mjs`; `node tools/governance/validate-v0-2-package.mjs --self-test`; `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict`; `git diff --check`; `git fsck --full`
- exit_code: 0
- environment: `Microsoft Windows 10.0.19045 x64; Node.js 24.12.0; Git 2.52.0.windows.1; no Android, device, emulator, KVM, benchmark or APK execution`
- timestamp: 2026-08-25T11:43:30+08:00
- artifact: `tools/governance/validate-v0-2-package.mjs`
- sha256: fffaf621d3b7b59a27f7b1e32732bb0ce70a3573ade003547ce7c6681f433276
- result: PASS; 39 task cards, 11 core docs and 19 ADRs valid; nine V2 tasks valid; 146 mutations rejected; strict HandOff and diff checks passed; Git object database has no integrity error

### V2-M0-01 local governance package

- task_id: V2-M0-01
- git_commit: 102cb775b205e1b592934e74a6d5d19e73ad2b3f
- command: `node tools/governance/validate-v0-2-package.mjs --self-test --base-ref origin/main`
- exit_code: 0
- environment: `Windows 10 Pro 10.0.19045 x86_64; Node.js 24.12.0; Git 2.52.0.windows.1; Eclipse Temurin 17.0.19+10`
- timestamp: 2026-08-24T14:03:25+08:00
- artifact: `docs/v0.2/evidence/V2-M0-01/local-validation.md`
- sha256: 8ae642144c28e4622bec3c85943888e5273df98e819834e7dfd4b6955655989f
- result: nine V2 tasks valid; 146 mutations rejected; zero product-path diff; no Android or benchmark execution

### V2-M0-01 independent semantic audit

- task_id: V2-M0-01
- git_commit: 102cb775b205e1b592934e74a6d5d19e73ad2b3f
- command: `independent read-only semantic audit of V2-M0-01 content tree 8c378094f721edd7b9b09beb4bc8fa22a787b93a`
- exit_code: 0
- environment: `Read-only repository review; reviewer did not modify files`
- timestamp: 2026-08-24T14:03:25+08:00
- artifact: `docs/v0.2/evidence/V2-M0-01/independent-review.md`
- sha256: 046b0d1de4f6ff2835fa3f72a3b3b8d77ebd0dba8de90d4d63bcd78f474b2915
- result: P0=0, P1=0, P2=0; all earlier findings closed

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

1. Assign and complete V2-M0-02 (`host-pipeline-agent`) on `chore/v2-m0-02-candidate-baseline`; it owns product/version/distribution and implementation manifest bytes but does not create a freeze lock.
2. In parallel, assign V2-M3-01 (`qa-governance-agent`) for fresh validation tooling and workflow candidates; keep file ownership disjoint from V2-M0-02.
3. Merge V2-M0-02 first and finish its post-merge PASS; refresh V2-M3-01 onto that exact `main`, rerun all gates and independent review, then merge V2-M3-01 second.
4. Start V2-M3-02 only after `implementationFreezeSha` is proven to be an ancestor of `validationFreezeSha`; execute the V2-M3-01-frozen verifier/adapters, create both freeze locks and the RC lock atomically, switch HandOff to the exact candidate tuple, and publish without dispatching the five canonical workflows.

## Relevant Files and Artifacts

- `docs/adr/0019-terminal-diagnostic-disposition.md`
- `docs/adr/0020-v0-2-product-baseline-and-version-isolation.md`
- `docs/evidence/M3-15/terminal-disposition-lock.json`
- `docs/v0.2/README_FIRST.md`
- `docs/v0.2/development-product-tuple.json`
- `docs/v0.2/identity-path-policy-v1.json`
- `docs/v0.2/post-freeze-path-policy-v1.json`
- `docs/v0.2/tasks/INDEX.md`
- `docs/v0.2/evidence/V2-M0-01/local-validation.md`
- `docs/v0.2/evidence/V2-M0-01/independent-review.md`
- `tools/governance/verify-m3-15-terminal-disposition-contract.mjs`
- `tools/governance/validate-v0-2-package.mjs`
- `.agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs`
- `.github/workflows/governance.yml`

## Resume Checklist

1. Confirm `git status --short --branch` and that local `main` is synchronized with `origin/main` before assignment.
2. Read `AGENTS.md`, this HandOff, `docs/v0.2/README_FIRST.md`, the selected task card and both machine policy JSON files.
3. Verify V2-M0-01 remains accepted at PR head `73b76c7b06222eac621227a8c8866c8cf6fac3e9`, merge `9ac8d6328c7a01c5645428e6b59b8c4db19d78ff` and post-merge Governance run `32696330686`.
4. Run `node tools/governance/validate-project-package.mjs`, `node tools/governance/validate-v0-2-package.mjs --self-test` and the strict HandOff validator before changing ownership.
5. Keep V2-M0-02 and V2-M3-01 ownership disjoint, and do not call either branch head a freeze SHA before its required merge/post-merge evidence exists.

## Handoff Sign-off

- coordinator: `/root`
- prepared_at: `2026-08-25T11:43:30+08:00`
- validation: `V2-M0-01 is merged and accepted: P0=0/P1=0/P2=0, exact-head and post-merge Ubuntu/Windows Governance passed, strict local post-merge gates passed, and V2-M0-02/V2-M3-01 may now be assigned under the fixed ownership and merge order.`
