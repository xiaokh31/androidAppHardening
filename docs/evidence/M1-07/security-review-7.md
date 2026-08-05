# M1-07 independent security review 7

## Target and result

- Frozen commit: `3bea66ad1aa89a6cbc97ba093b71235561481d38`
- Reviewer mode: seventh independent, offline, read-only, full review
- Result: **FAIL**
- Findings: P0 `0`, P1 `1`, P2 `2`
- Worktree before and after: clean
- Completed: `2026-08-05T14:35:32+08:00`

## Findings and remediation

1. The authoritative architecture sequence exposed authenticated Factory/policy and built the provisional loader before the same-handle metadata handoff. Remediation removes the early exposure and orders the flow as complete chunk/DEX verification, Native handle plus authenticated metadata, `LoadedPayload`, Guard identity/config/session, atomic session return, then exposure through that complete session.
2. Shared Test Strategy and the M2-02 acceptance summary omitted failure injection while fetching, parsing and constructing authenticated metadata. Remediation adds those bytes/object stages to the cross-JNI exception/OOM matrix.
3. M1-07 acceptance described only Native handle to `LoadedPayload`. Remediation makes `LoadedPayload` an internal module handoff and adds the second Guard-to-`VerifiedPayloadSession` publication window.

## Confirmed controls

The reviewer reconfirmed wire/layout arithmetic, one-shot per-chunk GCM and Provider semantics, manifest/KDF/nonce/AAD/config/signer/package binding, streaming tables, 1 MiB temporary-buffer feasibility, 512 MiB DEX feasibility, Native transaction cleanup, successful temporary-secret cleanup, both ownership-window designs and the acyclic dependency graph.

## Verification

Governance, strict HandOff, validator syntax, base-to-target and worktree diff checks, strict UTF-8 scan, independent 27-task traversal and exact layout/boundary/zlib arithmetic passed on Windows 10.0.19045 x64 with PowerShell 5.1.19041.7548, Node v24.12.0 and Git 2.52.0. No network, device, emulator or file modification was used.

The target is invalidated and requires a new clean freeze and full independent review.
