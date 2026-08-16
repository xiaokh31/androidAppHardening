# M3-08 remote validation

- Timestamp: `2026-08-16T13:19:32+08:00`
- Repository: `xiaokh31/androidAppHardening`
- Pull request: `#65` (draft)
- Issue: `#64` (OPEN and linked by `Closes #64`)
- Validated head: `add7a839440e24b52ba8cfb3c851212f7d344c7b`
- Branch: `docs/m3-08-startup-performance-stability`

## Required workflows

| Workflow | Run | Job | Platform | Result |
|---|---:|---:|---|---|
| Build | `31928514207` | `95119738168` | Ubuntu 24.04 | PASS |
| Build | `31928514207` | `95119738197` | Windows 2025 | PASS |
| Governance | `31928516421` | `95119743148` | Ubuntu 24.04 | PASS |
| Governance | `31928516421` | `95119743073` | Windows 2025 | PASS |

Both workflows report exact `headSha=add7a839440e24b52ba8cfb3c851212f7d344c7b`. Governance enforced the M3-08 zero-implementation-diff gate and strict pull-request HandOff validation on both platforms.

## Scope note

GitHub also auto-triggered M3-02 Fuzz run `31928515267` and Cross-platform equivalence run `31928514532`. They were cancelled because they are not M3-08 required evidence; no result from those runs is claimed. No KVM, emulator, physical device or benchmark workflow ran.

## Final PR head and merge

- Final PR head: `a5d76806850ecc68cb92e87c4a06e29d9cfe0b1b`
- Replacement Build: run `31928811438`; Ubuntu job `95120468973` PASS; Windows job `95120468972` PASS
- Replacement Governance: run `31928811435`; Ubuntu job `95120469055` PASS; Windows job `95120469036` PASS
- Automatically required Cross-platform equivalence `31928811426`: PASS
- Automatically required M3-02 Fuzz `31928811541`: PASS
- PR state before merge: `CLEAN` / `MERGEABLE`
- Merge: expected-head protected ordinary merge commit `4c3efc1614158a0372eb877fc02fd1db27dcffb3`
- Issue #64: CLOSED

No KVM, emulator, physical device or benchmark workflow ran. Post-merge `main` Build/Governance remain the final M3-08 completion gate.

## Post-merge main

- Coordination head: `e12542db48eac96f17c4a1f4306ec20c62dcfa1f`
- Build: run `31929454365`; Ubuntu job `95122002423` PASS; Windows job `95122002429` PASS
- Governance: run `31929454381`; Ubuntu job `95121973549` PASS; Windows job `95121973480` PASS
- Local M3-08 validator, project governance, strict HandOff and diff check: PASS
- Superseded merge-commit Build/Fuzz runs and the coordination-head non-required Fuzz run were cancelled; they are not acceptance evidence.

M3-08 is complete. M3-05 PR #63 may resume for exactly one ADR 0015 API 36 A/B replacement job.
