# M2-07 independent read-only security review 8

- Frozen commit: `dfda35fad30d7edf2ee1fdfe26d3248dacd15e91`
- Review timestamp: `2026-08-07T13:09:32+08:00`
- Result: **FAIL**
- Severity totals: `P0=0`, `P1=0`, `P2=1`
- Mutation: none; the reviewer changed no files or Git state and performed no build, download, device or emulator operation

## Prior findings

All nineteen findings from reviews 1 through 7 were independently verified **CLOSED**. Frozen-SHA Build `31148877894`, Governance `31148877818` and API 29/36 KVM `31148877817` were successful. The shared parser captured related names before type filtering, rejected global/uppercase/other types and related exports, and both platforms propagated its failures.

## New finding

1. **P2 — the declared `t↔d` self-test covered only `t→d`.** The exact comparator independently rejected a synthetic `d→t` mutation of `mbedtls_platform_dev_random`, so no known gate bypass existed, but the in-process test and evidence claimed both directions without explicitly exercising the sole locked local-data entry.

## Disposition required

The frozen SHA is permanently rejected. Add a direct `d mbedtls_platform_dev_random` to `t mbedtls_platform_dev_random` negative to the shared parser self-test, preserve the existing mutation matrix, create a new SHA, rerun exact-head Build/Governance/KVM and perform review 9. PR #42 remains draft and M2-02 remains paused.
