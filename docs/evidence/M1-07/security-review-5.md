# M1-07 independent security review 5

## Target and result

- Frozen commit: `340b6ae83f05d89fb20d2d2d7d32ad1b55d65404`
- Branch: `docs/m1-07-chunk-authenticated-container-contract`
- Reviewer mode: fifth independent, offline, read-only, full review
- Result: **FAIL**
- Findings: P0 `0`, P1 `1`, P2 `0`
- Completed at: `2026-08-05T14:00:51+08:00`
- Worktree before and after review: clean

The reviewer changed no files, used no network, and started no device or emulator.

## P1-1: ownership gap after Native handle return and before public object return

The contract closed Native pre-handle failures and public `LoadedPayload.close`, but Java still had to obtain `ByteBuffer[]`, resolve the Native search path, construct `InMemoryDexClassLoader` and construct/return `LoadedPayload` after receiving the `long`. An exception or OOM in that window could leave mappings without a reachable AutoCloseable owner.

Remediation makes `PayloadRuntime.openVerified` returning a complete `LoadedPayload` the only public commit boundary. The facade keeps the primitive handle in a `committed=false` `try/finally` without allocating a guard object. Any buffer array/element, search-path, ClassLoader, LoadedPayload construction or pre-return failure invokes allocation-free Native close exactly once, clears mappings and partial Java references, publishes no object/`ByteBuffer`, preserves the primary error and suppresses cleanup errors. Only a complete result sets committed and transfers ownership.

M3-02 now distinguishes internal handle acquisition from public object publication and records close count and partial Java-reference cleanup for these injection points.

## Confirmed controls

The reviewer reconfirmed all wire, Provider, per-chunk, manifest/AAD/config/signer/package, KDF/nonce, v1 rejection, streaming/1 MiB, Native pre-handle transaction, successful temporary-secret cleanup, handle lifecycle, M3 cleanup fields, evidence timeline and dependency/governance controls.

## Commands

Target/base/status, Governance, strict HandOff, Node syntax, diff/index/UTF-8/whole-record scans, dependency traversal and independent layout/boundary/compress-bound calculations passed. A governance-only mode intentionally rejected the non-empty implementation repository and was not an M1-07 gate. Environment was Windows 10.0.19045, Node v24.12.0 and Git 2.52.0.

The target is invalidated and requires a new clean freeze plus full independent review.
