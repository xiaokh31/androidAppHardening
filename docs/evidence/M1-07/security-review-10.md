# M1-07 independent security review 10

## Target and result

- Frozen commit: `358a71a9478a0ccb76f71538002184a6a4ea4dc4`
- Reviewer mode: tenth independent, offline, read-only, full review
- Result: **FAIL**
- Findings: P0 `0`, P1 `0`, P2 `1`
- Worktree before and after: clean
- Completed: `2026-08-05T15:04:49+08:00`

## Finding and remediation

The Guard mismatch matrix included build/key/version/original Factory without freezing a comparison source. Remediation maps package/current signer/lineage to Framework/apksig, build/key to same-call untrusted pre-read solely for inspect/open snapshot-change detection, and versions to constants `2.0/1/1`. Original Factory has no second trusted source: its value is consumed only from Native-authenticated metadata, while Factory/config tamper belongs to Native ConfigV2 digest/manifest tests and metadata encoding errors belong to M2-02 golden parser tests.

## Confirmed controls

The reviewer reconfirmed wire/layout and 512 MiB/1 MiB arithmetic, per-chunk one-shot GCM, complete cryptographic bindings, streaming tables, Native transaction/success cleanup, both ownership windows, all ten metadata getters, zero pre-session lookup/Factory/publication, M3 state fields, dependencies and HandOff.

## Verification

Governance, strict HandOff, validator syntax, diff checks, strict UTF-8 scan, independent dependency traversal and exact layout/zlib arithmetic passed on Windows 10.0.19045 with Node v24.12.0 and Git 2.52.0. No network, device, emulator or file modification was used.

The target is invalidated and requires a new clean freeze and full independent review.
