# M1-07 independent security review 3

## Target and result

- Frozen commit: `e35543804905df0045d22c1d6a06e903384afd93`
- Branch: `docs/m1-07-chunk-authenticated-container-contract`
- Reviewer mode: third independent, offline, read-only, full review
- Result: **FAIL**
- Findings: P0 `0`, P1 `1`, P2 `0`
- Worktree before and after review: clean

The reviewer changed no files, used no network, and started no device or emulator.

## P1-1: success verification incorrectly cleared committed DEX mappings

ADR 0008 correctly gave the pre-publication transaction ownership of completed/partial mappings and transferred all completed mappings to the published handle only after every DEX succeeded. Its final verification bullet nevertheless required those mappings to be cleared and unmapped on the “success” path. Implementing that test would invalidate the returned handle/ClassLoader and made the authoritative contract internally impossible.

Remediation splits the paths:

- successful commit immediately clears temporary key/AAD/compressed/inflater state, atomically transfers completed DEX mappings to the handle, and retains them until handle/ClassLoader lifecycle close;
- any pre-publication failure clears/unmaps every completed/partial mapping, publishes no handle/`ByteBuffer`, performs allocation-free best-effort cleanup, and preserves the primary error.

## Confirmed controls

The reviewer reconfirmed one-shot per-chunk GCM, no record-level tag/buffer, complete manifest coverage, transactional pre-publication ownership, first/middle/final failure coverage, 160/128/32/768-byte layouts, 65,552-byte maximum crypto input, 512 MiB compress-bound feasibility, streaming tables, 1 MiB temporary-buffer feasibility, KDF/nonce/AAD bindings, v1 rejection and acyclic dependencies.

## Commands

Target/base/branch/clean checks, Governance, strict HandOff, diff/UTF-8 scans and independent structure/boundary/maximum calculations completed successfully on Windows 10.0.19045 with Node v24.12.0 and Git 2.52.0. Final HEAD remained the target and worktree remained clean.

The frozen SHA is invalidated. The single P1 requires a new clean freeze and full independent review; no wire-layout or device change is required.
