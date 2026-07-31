---
name: coordinate-project-handoff
description: Create, reconcile, update, accept, or validate the repository root HandOff.md. Use only when coordinating task ownership, accepting worker evidence, recording decisions or blockers, changing milestones, pausing work, resuming work, or preparing a branch or session handoff.
---

# Coordinate Project Handoff

## Authority

Only `/root` may edit root `HandOff.md`. Worker agents must return the packet in `assets/worker-handoff-template.md` instead. A coordinator identity change requires the user to update the repository rule and schema first.

## Workflow

1. Read `references/handoff-schema.md`, root `AGENTS.md`, the current `HandOff.md`, affected task cards, and worker packets.
2. Verify the branch, base commit, worktree, changed files, commands, exit codes, and artifacts directly. Do not treat chat claims as evidence.
3. Reconcile parallel work in merge order. Update ownership, status, decisions, blockers, verification, and ordered next actions without turning the file into a historical log.
4. Use repository-relative paths and artifact hashes. Remove secrets, private paths, customer APK details, and plaintext DEX information.
5. Write `None` for genuinely empty sections. Do not leave placeholders.
6. Validate before accepting the handoff:

```text
node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict
```

Use `--allow-pending-clean` only immediately before committing the HandOff that declares the post-commit worktree clean. Run strict validation again after the commit.
Use `--allow-pending-branch` only when the HandOff intentionally records the post-merge `main` state while it is still on the reviewed source branch. The final strict validation must run on `main` without either allowance.

## Completion Rule

Do not mark work `done` without commands, exit codes, environment, timestamp, and artifact or commit evidence. A blocker must identify its owner, required decision or state change, and smallest next action.
