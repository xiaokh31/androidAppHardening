# M1-07 independent security review 2

## Target and result

- Frozen commit: `3380659355981738998d32a3b0f1dabb70a2067d`
- Branch: `docs/m1-07-chunk-authenticated-container-contract`
- Reviewer mode: second independent, offline, read-only, full review
- Result: **FAIL**
- Findings: P0 `0`, P1 `2`, P2 `0`
- Worktree before and after review: clean

The reviewer changed no files, used no network, and started no device or emulator.

## Findings and remediation

### P1-1: M2-02 Goal retained whole-record authentication wording

The Goal still said “each authenticated compressed record”. Because AHDC v2 has no record-level tag, that phrase could again imply buffering and authenticating a whole record before inflate.

Remediation: the Goal now defines per-canonical-chunk one-shot GCM, immediate feed to the record's continuous inflater after tag success, no record-level authentication and no whole-record buffer.

### P1-2: pre-publication partial DEX ownership and cleanup were incomplete

`nativeOpenVerifiedPayload` returns a handle only after all records succeed. If a later chunk, I/O, cancellation, OOM, zlib or digest check failed, earlier completed DEX mappings and the current partial DEX existed before any Java `LoadedPayload` could close them. The frozen contract did not assign transactional ownership or require failure-injection evidence for those mappings.

Remediation: ADR 0008, architecture, M2-02, threat model and test strategy now require one pre-publication transaction owner. Any failure clears and unmaps all completed/partial DEX mappings, destroys inflater/crypto state, publishes no handle/`ByteBuffer`, continues best-effort cleanup, and preserves the primary error. Only all-DEX success atomically transfers ownership to the published handle. Tests inject first/middle/final chunk and cleanup failures.

## Confirmed controls

The reviewer reconfirmed the 160/128/32/768-byte layouts, canonical chunk boundaries, a 65,552-byte maximum one-shot crypto input, maximum container/count arithmetic, streaming table, 1 MiB temporary-buffer feasibility, HKDF/nonce separation, full manifest/AAD/tag binding, v1 rejection, first-review remediations, acyclic dependencies and governance registration.

## Commands

SHA/branch/ancestor/status, Governance, strict HandOff, diff checks, structure/boundary calculations and UTF-8 scan completed successfully. Environment was Windows 10.0.19045, Node v24.12.0 and Git 2.52.0. The reviewer ended on the same target SHA with a clean worktree.

This frozen SHA is invalidated. Both P1 findings require a new clean freeze and full independent review.
