# HandOff schema

Read this reference before editing root `HandOff.md`.

## Frontmatter

Required keys, in order:

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

`base_commit` is the last stable commit against which the handoff was prepared. Once the repository has a seed commit, it must be a full SHA that is an ancestor of the current HEAD. `updated_by` is fixed to `/root`; changing coordinator identity requires a user-approved schema update. Normally `source_branch` equals the checked-out branch. For the final merger-ready root snapshot only, `/root` sets it to the PR base branch and validates the PR with `--allow-pending-branch`; the merged base branch must then pass strict validation without that flag.

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

Each verification record contains `task_id`, `git_commit`, `command`, `exit_code`, `environment`, `timestamp`, `artifact`, `sha256`, and `result`.

## Update triggers

Update on coordinator transfer, owner or scope changes, architecture or security decisions, blocker entry or exit, key verification, merge, milestone change, branch or context switch, pause, and session end. Do not update for a read-only check that produces no new conclusion.

## Sensitive information

Never include tokens, credentials, private keys, signing passwords, keystore locations, customer APK paths, plaintext DEX details, or user-directory absolute paths. Record ignored artifacts by controlled ID, size, and SHA-256.
