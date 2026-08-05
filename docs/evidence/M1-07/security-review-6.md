# M1-07 independent security review 6

## Target and result

- Frozen commit: `bb2e744fce0f64c7f0effd59c99f5bb2882b834c`
- Reviewer mode: sixth independent, offline, read-only, full review
- Result: **FAIL**
- Findings: P0 `0`, P1 `2`, P2 `0`
- Worktree before and after: clean

## P1 findings and remediation

1. M2-02 authenticated ConfigV2/`SPV1` fields had no mechanical handoff to M2-03. Remediation adds same-handle, immutable, non-secret `AuthenticatedPayloadMetadata` carried by `LoadedPayload`; M2-03 can construct `VerifiedStartupConfiguration` only from it, never untrusted pre-read or a ConfigV2 re-read.
2. Ownership ended at `LoadedPayload` return, but Guard still had to build identity/config/session. Remediation extends local `committed=false`/`finally` ownership through `RuntimeStartupGuard.openVerifiedPayload` returning a complete `VerifiedPayloadSession`, with exactly-once close and injection tests at each construction/return point.

M3-02 now records session publication and partial Guard-reference cleanup in addition to Native/LoadedPayload state.

## Confirmed controls

The reviewer reconfirmed the complete container wire/crypto/bounds model, Native and cross-JNI ownership windows, temporary-secret cleanup, handle lifecycle, M3 pre-session fields, evidence timeline and acyclic governance graph.

## Verification

Governance, strict HandOff, validator syntax, diff checks, UTF-8 scan, independent dependency/boundary arithmetic and final clean status passed on Windows 10.0.19045 with Node v24.12.0 and Git 2.52.0. No network, device, emulator or file modification was used.

The target is invalidated and requires a new clean freeze and full independent review.
