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

The evidence-only successor commit must receive replacement exact-head Ubuntu/Windows Build and Governance results before PR #65 may be converted from draft.
