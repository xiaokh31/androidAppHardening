# HandOff schema

Read this reference before editing root `HandOff.md`.

## Frontmatter

Schema 1 remains valid for existing v0.1 historical HandOff files. Its required keys and order remain unchanged:

```yaml
schema_version: 1
project: androidAppHardening
handoff_id: HO-YYYYMMDD-HHMMSS
updated_at: ISO-8601 with timezone
updated_by: /root
state: active|ready|blocked
source_branch: target resume branch
base_commit: full 40-character SHA|UNBORN
working_tree: clean|dirty
current_milestone: M0|M1|M2|M3|M4
active_task: task ID|NONE
next_owner: owner|unassigned
```

Schema 2 is required for the v0.2 release line. Its required keys, in order, are:

```yaml
schema_version: 2
project: androidAppHardening
release_line: v0.2
product_tuple_sha256: new-nonzero-64-character-lowercase-sha256
handoff_id: HO-YYYYMMDD-HHMMSS
updated_at: ISO-8601 with timezone
updated_by: /root
state: active|ready|blocked
source_branch: target resume branch
base_commit: full 40-character SHA|UNBORN
working_tree: clean|dirty
current_milestone: V2-M0|V2-M1|V2-M2|V2-M3|V2-M4
active_task: V2-Mx-nn|NONE
next_owner: owner|unassigned
```

`base_commit` is the last stable commit against which the handoff was prepared. Once the repository has a seed commit, it must be a full SHA that is an ancestor of the current HEAD. `updated_by` is fixed to `/root`; changing coordinator identity requires a user-approved schema update. Normally `source_branch` equals the checked-out branch. For the final merger-ready root snapshot only, `/root` sets it to the PR base branch and validates the PR with `--allow-pending-branch`; the merged base branch must then pass strict validation without that flag.

For Schema 2, `release_line` is exactly `v0.2`. The tuple field follows a two-state identity machine. While `docs/v0.2/evidence/V2-M3-02/product-tuple-lock.json` is absent, `product_tuple_sha256` must equal the exact non-releasable development tuple. The V2-M0-01 pre-candidate validator rejects every candidate lock, including a canonical and hash-self-consistent `VERIFIED` lock. V2-M3-01 must first freeze the candidate-neutral aggregate verifier and the package/HandOff candidate adapters. Those adapters may accept V2-M3-02's atomic transition only after rebuilding both official post-merge freeze locks, all seven manifest Git closures, component baseline, ancestry, five workflow pairs and the tuple preimage. The candidate tuple has fourteen ordered fields and seven tracked manifest preimages, including `validationManifestSha256` immediately after `validationFreezeSha`; omission, reordering, an arbitrary preimage or a self-certified identity is invalid. V2-M3-02 writes locks, HandOff data and byte-identical live workflows but must not add or modify validator code. The terminal v0.1 tuple, an arbitrary third tuple, a candidate without its locks, an unverified/malformed/false lock, or a lock/HandOff mismatch is invalid. A non-`NONE` Schema 2 `active_task` must have a task card under `docs/v0.2/tasks/`, its `V2-Mx` prefix must exactly match `current_milestone`, and its row must occur inside `Active Workstreams` rather than elsewhere in the document. Schema 1 continues to use `M0` through `M4`, `Mx-nn`, and `docs/tasks/`; it must not represent current v0.2 state.

## Required headings

```text
# Project HandOff
## Objective
## Current State
## Active Workstreams
## Decisions and Invariants
## Changes Since Previous Handoff
## Verification Evidence
## Blockers and Required Approvals
## Ordered Next Actions
## Relevant Files and Artifacts
## Resume Checklist
## Handoff Sign-off
```

Use `planned`, `in_progress`, `blocked`, `review`, or `done` for workstream status. A `done` row requires evidence. A `blocked` row requires an unblock owner and exact next action.

`state: active` requires a concrete `active_task` and matching workstream row. `state: blocked` requires an explicit non-`None` blocker section. `state: ready` requires the blocker section to be exactly `None`.

Each verification record contains `task_id`, `git_commit`, `command`, `exit_code`, `environment`, `timestamp`, `artifact`, `sha256`, and `result`. `task_id` accepts both historical `Mx-nn` and v0.2 `V2-Mx-nn`, so a Schema 2 HandOff may retain clearly identified historical evidence without treating it as completion evidence for a v0.2 task.

## Update triggers

Update on coordinator transfer, owner or scope changes, architecture or security decisions, blocker entry or exit, key verification, merge, milestone change, branch or context switch, pause, and session end. Do not update for a read-only check that produces no new conclusion.

## Sensitive information

Never include tokens, credentials, private keys, signing passwords, keystore locations, customer APK paths, plaintext DEX details, or user-directory absolute paths. Record ignored artifacts by controlled ID, size, and SHA-256.
