# M1-07 independent security review 9

## Target and result

- Frozen commit: `d5d5d292600953eb21c4422dee8038288bb19d6a`
- Reviewer mode: ninth independent, offline, read-only, full review
- Result: **FAIL**
- Findings: P0 `0`, P1 `1`, P2 `1`
- Worktree before and after: clean
- Completed: `2026-08-05T14:56:05.8279713+08:00`

## Findings and remediation

1. ADR 0007 required metadata rechecks before loader construction, while the fixed M2-02 API could expose metadata only through a `LoadedPayload` that already owned a provisional loader. Remediation defines Native package/signer authentication as the cryptographic gate, requires metadata construction before the internal provisional loader, and prohibits any payload class/resource lookup, Factory call or bootstrap publication until Guard rechecks and atomically returns a complete session.
2. Only the three security-binding metadata getters had exact Java signatures. Remediation freezes all ten cross-module getters, including Factory nullability, unsigned 16-bit version ranges, 16-byte build/key IDs, 32-byte digests, ordered lineage bounds and deep-copy semantics.

## Confirmed controls

The reviewer reconfirmed wire/layout/zlib arithmetic, one-shot per-chunk GCM, streaming tables, full cryptographic bindings, Native transaction and temporary-secret cleanup, both ownership windows, M3 evidence fields, dependency graph and clean HandOff state.

## Verification

Governance, strict HandOff, validator syntax, diff checks, strict UTF-8 decode, independent dependency traversal and exact layout/zlib arithmetic passed on Windows 10.0.19045 with Node v24.12.0 and Git 2.52.0. No network, device, emulator or file modification was used.

The target is invalidated and requires a new clean freeze and full independent review.
