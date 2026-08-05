# M1-07 independent security review 1

## Target and result

- Frozen commit: `e13927a22f8b008ab6bc419b26b53044a847ef4a`
- Branch: `docs/m1-07-chunk-authenticated-container-contract`
- Reviewer mode: independent, offline, read-only
- Result: **FAIL**
- Findings: P0 `0`, P1 `1`, P2 `1`
- Timestamp: `2026-08-05T13:17:49+08:00`

The reviewer confirmed the target SHA and clean worktree, made no file changes, used no network, and started no device or emulator.

## Findings

### P1-1: authoritative Runtime contracts retained whole-record AEAD wording

Although ADR 0008 correctly required one-shot GCM per canonical chunk, frozen downstream text still said “record AEAD tag”, “each DEX GCM tag” and “each record GCM tag”; the startup sequence also omitted the chunk table from manifest coverage. Those phrases could direct M2-02 back to the rejected whole-record buffering design and violated M1-07 acceptance criterion 3.

Remediation: architecture and M2-02 now explicitly state that no record-level tag exists; the manifest covers HeaderV2, `SPV1`, record table and chunk table; every canonical chunk uses one-shot GCM; only a successfully authenticated chunk enters its record's continuous inflater; Provider plaintext returned before final tag success is never consumed.

### P2-1: dependency proof sentence reversed the edge

The dependency diagram correctly showed `M1-07 -> M1-04`, but the following sentence incorrectly claimed that edge did not exist.

Remediation: the proof now states that M1-07 does not depend on M1-04 and no edge/path returns from M1-04 to M1-07, so the dependency is acyclic.

## Confirmed controls

The reviewer independently confirmed the 160/128/32/768-byte structure sizes, canonical chunk boundary calculations, 65,552-byte maximum one-shot crypto input, maximum compressed boundary arithmetic, streaming table design, per-record HKDF/nonce uniqueness, manifest/AAD/tag binding, implementable 1 MiB temporary-buffer contract, zeroization and primary-error preservation, no v1 fallback, acyclic actual dependency graph, and correct governance registration for ADR 0008, M1-07 and Issue #36.

## Commands

- `git rev-parse HEAD`, branch/status and merge-base checks: exit `0`.
- `node tools/governance/validate-project-package.mjs`: exit `0`, 27 task cards, 11 core docs, 8 ADRs.
- strict HandOff validation and both diff checks: exit `0`.
- independent structure/boundary calculations: exit `0`.
- UTF-8 replacement scan: no match.
- final worktree status: clean.

The frozen SHA is invalidated for publication. A new clean freeze and full independent re-review are mandatory after both findings are closed.
