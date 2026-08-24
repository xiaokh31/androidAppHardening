---
id: M3-15
title: "Terminal diagnostic and v0.1 release disposition"
milestone: M3
status: planned
owner_role: /root
depends_on:
  - M3-13
required_skills:
  - plan-apk-hardening-change
  - coordinate-project-handoff
security_sensitive: true
---

## Goal

Record the consumed ADR 0018 successor as terminal, stop the current v0.1 release line without weakening any gate, and define the auditable closure of the blocked M3-14 and M3-05 drafts.

## Background

M3-14 exists only on Issue #82 and Draft PR #83 because it did not satisfy its acceptance criteria. Its sole canonical run and terminal evidence run both failed with zero artifacts, and ADR 0018 permits no retry or further renewal. The task is an immutable external decision input, not a completed dependency imported into `main`. Issue #84 and branch `docs/m3-15-terminal-disposition-contract` are the only M3-15 workstream.

## Inputs

- ADR 0018 and its no-further-renewal rule.
- Draft PR #83 exact reviewed head `a112e4469699125a32d80e3c652cda7d4b6b7cf1`.
- Canonical run `32611656930` / job `97125597267` / attempt `1` and terminal run `32612414400` / job `97127412040` / attempt `1`.
- M3-14 terminal proof identities and six official-page SHA-256 values fixed by `terminal-disposition-lock.json`.
- M3-05 and M4 task contracts, product requirements and release gates.

## Expected Outputs

- ADR 0019 with decision `STOP_CURRENT_V0_1_RELEASE_LINE`.
- An immutable machine-readable terminal disposition lock.
- Updated task graph, roadmap, test strategy, M3-05/M4 blocking text, README and HandOff.
- A fail-closed validator with named mutation and governance-only diff tests.
- Independent all-zero read-only review before any push or PR.

## In Scope

- Preserve exact M3-14 terminal identities without claiming a narrower failure cause.
- Prohibit retry, replacement, identity renewal and platform substitution.
- Mark the current M3-05 and M4 path as not startable.
- Define post-merge closure of PR #83/Issue #82 and PR #63/Issue #22 without merging either draft.
- Define the minimum new-version/new-tuple governance boundary for any future restart.

## Out of Scope

- Running or editing canonical, terminal, benchmark, device, KVM, ARM or API 29 workflows.
- Starting an emulator, installing an APK, downloading the retained profile package or creating new measurement evidence.
- Modifying Host, Runtime, fixtures, benchmarks, distribution, signer, container, compatibility or performance budgets.
- Merging PR #83 or PR #63, or deleting their evidence.
- Designing or authorizing a future release implementation.

## Implementation Decisions

- `main@960eb9f406eb1a7b7c9b324598fb59936aa1c5b5` is the M3-15 base. The M3-14 branch is not merged or copied into this task.
- The M3-14 result is bound as an external immutable terminal input by exact Git/run/job/page identities.
- The current release line has no waiver path. Missing performance evidence blocks release rather than becoming `UNVERIFIED` compatibility evidence.
- Draft closure occurs only after ADR 0019 is merged, so the closure comments can link the accepted decision.
- Any future effort requires a new versioned product tuple and separately authorized ADR/task graph; it is not a successor renewal.

## Public Interfaces

- No product interface changes.
- Governance decision code: `STOP_CURRENT_V0_1_RELEASE_LINE`.
- Validator entry point: `node tools/governance/verify-m3-15-terminal-disposition-contract.mjs`.

## Security Constraints

- No caller-authored statement can convert a failed or zero-artifact run into valid performance evidence.
- The current product tuple, execution identity, run/job IDs, attempt, reviewed head and official-page hashes are immutable.
- No private key, token, APK, DEX, raw unrestricted log, device serial or user path enters M3-15 evidence.
- Closing a PR must not delete its GitHub record or rewrite the branch history.
- No release-ready, complete-v0.1 or M4-startable claim is permitted.

## Compatibility Requirements

- Existing M3-04 cell states remain unchanged.
- No unverified platform is promoted and no verified platform substitutes for M3-05 performance evidence.
- No minSdk, ABI, Runtime, signer, container or supported-input change is made.

## Acceptance Criteria

- Issue #84 is the unique task issue and the branch contains only M3-15 governance scope.
- ADR, task, lock, dependency graph, README, test strategy and HandOff consistently state the terminal decision.
- Validator positive and all named mutations pass; base-to-HEAD check rejects production, workflow, fixture, benchmark and distribution changes.
- PR #83 and PR #63 remain Draft/open while M3-15 is under review and are not merged.
- Independent read-only review reports `P0=0/P1=0/P2=0` on the exact frozen commit before push or PR.
- No Android, device, KVM, benchmark, diagnostic or terminal evidence workflow runs during the task.

## Required Tests

- Positive exact-lock and document consistency validation.
- Mutation tests for every fixed SHA, run, job, attempt, step, artifact count, action and permission flag.
- Negative tests enabling retry, replacement, release, M3-05 resume, M4 start or draft merge.
- Base-to-HEAD changed-path allowlist test.
- Project Governance, strict HandOff, UTF-8/link and sensitive-data scans.

## Required Evidence

- Windows OS, Node/Git versions, timestamp, base commit, frozen commit and changed-file list.
- Validator positive/self-test counts and exit codes.
- Governance, strict HandOff and diff-check exit codes.
- Independent review result and exact reviewed commit.
- Confirmation that no workflow, Android, KVM, emulator, device, benchmark or artifact download ran.

## Likely Files

- `docs/adr/0019-terminal-diagnostic-disposition.md`
- `docs/tasks/M3-15-terminal-diagnostic-disposition.md`
- `docs/evidence/M3-15/terminal-disposition-lock.json`
- `tools/governance/verify-m3-15-terminal-disposition-contract.mjs`
- `tools/governance/validate-project-package.mjs`
- `docs/tasks/INDEX.md`
- `docs/PROJECT_PLAN.md`
- `docs/ROADMAP.md`
- `docs/TEST_STRATEGY.md`
- `docs/PRODUCT_REQUIREMENTS.md`
- `README.md`
- `HandOff.md`

## Dependencies and Blockers

- M3-13/ADR 0018 is merged and complete.
- M3-14 is a terminal external input, not a satisfied implementation dependency.
- Push and draft PR creation require a separately requested action after the all-zero review.
- PR/Issue closure requires ADR 0019 merge; no ready/merge or closure is part of the local contract freeze.

## Agent Handoff Requirements

Return the exact base/freeze commits, lock hash, changed paths, validator commands/results, independent finding counts and explicit confirmation of zero workflow/device execution. State that M3-05/M4 remain blocked and list the four post-merge close-without-merge actions.
