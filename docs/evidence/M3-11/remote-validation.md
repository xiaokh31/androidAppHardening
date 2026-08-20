# M3-11 remote validation

- PR: [#72](https://github.com/xiaokh31/androidAppHardening/pull/72), merged; Issue #71 closed
- Validated head: `b29c8c50a99ae1b4ea35926bd12337563c0dfe49`
- Timestamp checked: `2026-08-20T10:05:55+08:00`
- Result: required M3-11 Ubuntu/Windows Build and Governance all passed
- Merge protection: expected head `b29c8c50a99ae1b4ea35926bd12337563c0dfe49`; merge commit `98e652b3017df0255ba8be4869513698c18c9ce6`; merged at `2026-08-20T02:05:55Z`

## Exact-head workflows

| Workflow | Run | Job | Platform | Result |
|---|---:|---:|---|---|
| Build | `32263748298` | `96103039861` | `ubuntu-24.04` | PASS |
| Build | `32263748298` | `96103040272` | `windows-2025` | PASS |
| Governance | `32263748308` | `96103038996` | `ubuntu-24.04` | PASS |
| Governance | `32263748308` | `96103039254` | `windows-2025` | PASS |

Both workflow API responses bind directly to head `b29c8c50a99ae1b4ea35926bd12337563c0dfe49`, event `pull_request`, terminal status `completed`, and conclusion `success`. Governance ran the M3-11 zero-implementation-diff gate and strict PR HandOff validation on both platforms. Build completed the existing pinned Host/Native/ABI checks on both platforms.

## Scope control

Cross-platform equivalence run `32263748356` and M3-02 fuzz run `32263748332` were automatically triggered by the pull request but are outside M3-11. Cancellation was requested immediately. The equivalence run completed as cancelled. The fuzz run contains one cancellation-race failure and otherwise cancelled jobs; it is not a required check and is not used as M3-11 evidence. No rerun is authorized or needed.

No benchmark, KVM, emulator, physical device, ARM campaign or canonical ADR 0016 diagnostic ran. M3-11 is merged, but M3-10 still requires a separately reviewed installable-profile strategy for the exact locked pair; M3-05 remains blocked.
