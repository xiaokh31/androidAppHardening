# M1-07 independent security review 4

## Target and result

- Frozen commit: `dd0c4c0811557be09ce2ac2b11afde5d7794b337`
- Branch: `docs/m1-07-chunk-authenticated-container-contract`
- Reviewer mode: fourth independent, offline, read-only, full review
- Result: **FAIL**
- Findings: P0 `0`, P1 `2`, P2 `1`
- Worktree before and after review: clean

The reviewer changed no files, used no network, and started no device or emulator.

## Findings and remediation

### P1-1: downstream success boundary did not require immediate temporary-secret cleanup

ADR 0008 correctly retained only completed DEX mappings after successful commit, but M2-02 and its tests could keep CEK, derived keys, AAD and inflater/crypto scratch until handle close.

Remediation: architecture, M2-02, M1-07 and Test Strategy now require all temporary secrets/state to be cleared after all DEX succeed but before handle return. A success hook checks this after `openVerified` and before `close`, while completed mappings remain valid and exclusively owned by the handle; mappings are cleared/unmapped only on lifecycle close.

### P1-2: M3-02 did not prove failed pre-publication transaction cleanup

The tamper contract asserted only `payloadLoaded=false`, stage/code and no affected chunk entering the inflater. A middle/final chunk failure could therefore leave completed/partial mappings while still passing M3-02.

Remediation: the catalog/result contract now records handle/`ByteBuffer` publication, completed/partial mapping cleanup, primary-code preservation and suppressed cleanup failure. First/middle/final chunk and cleanup-injection cases must prove no publication, applicable mappings cleared/unmapped and primary error retained.

### P2-1: review timestamps were not trustworthy

HandOff recorded review 2 after its remediation commit and then review 3 earlier than review 2. The review 2 completion clock is not recoverable from the preserved output.

Remediation: review 2 now explicitly uses the verifiable remediation archive commit time `2026-08-05T13:29:52+08:00`, not a claimed review-completion time; review 3 keeps its reviewer-provided `2026-08-05T13:36:03+08:00`. The archive text states the distinction.

## Confirmed controls

The reviewer reconfirmed per-chunk one-shot GCM, no whole-record tag/buffer, complete manifest/AAD/config/signer/package binding, KDF/nonce separation, v1 rejection, streaming table, 1 MiB feasibility, checked arithmetic, pre-publication transaction ownership, primary-error preservation and acyclic dependencies.

## Commands

Governance, strict HandOff, Node syntax, diff checks, changed-file UTF-8 scan, independent layouts/boundaries/compress-bound arithmetic, dependency traversal, forbidden whole-record scan and final SHA/base/clean checks all passed. Environment was Windows 10.0.19045, PowerShell 5.1, Node v24.12.0 and Git 2.52.0.

This frozen SHA is invalidated. Both P1 findings and the evidence-timestamp P2 require a new clean freeze and full independent review.
