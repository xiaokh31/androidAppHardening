# M1-07 independent security review 8

## Target and result

- Frozen commit: `01f76f6c7dfa3a0fad999016c54351329bc56e29`
- Reviewer mode: eighth independent, offline, read-only, full review
- Result: **FAIL**
- Findings: P0 `0`, P1 `1`, P2 `1`
- Worktree before and after: clean
- Completed: `2026-08-05T14:44:38+08:00`

## Findings and remediation

1. The metadata-stage package comparison was not mechanically implementable because the fixed `AuthenticatedPayloadMetadata` fields omitted package or its digest. Remediation adds the 32-byte digest from the successful same-handle Framework package binding and requires constant-time comparison; current signer is compared the same way and lineage is compared exactly in old-to-new order.
2. The required independent-review input remained limited to the initial wire/crypto scope. Remediation extends both review-input and contract-verification checklists across Native transactions, successful secret cleanup, same-handle metadata, both ownership windows, exception/OOM injection and M3 evidence fields.

## Confirmed controls

The reviewer reconfirmed wire/layout arithmetic, 512 MiB and 1 MiB feasibility, one-shot per-chunk GCM, streaming tables, manifest/KDF/nonce/AAD/config/signer/package binding, the two exactly-once ownership designs, corrected authoritative startup order, M3 injection fields and the acyclic dependency graph.

## Verification

Governance, strict HandOff, validator syntax, diff checks, strict UTF-8 scan, independent dependency traversal and exact layout/zlib arithmetic passed on Windows 10.0.19045 x64 with Node v24.12.0 and Git 2.52.0. No network, device, emulator or file modification was used.

The target is invalidated and requires a new clean freeze and full independent review.
