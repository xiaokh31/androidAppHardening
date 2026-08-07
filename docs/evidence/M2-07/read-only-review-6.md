# M2-07 independent read-only security review 6

- Frozen commit: `9c29fb63c8e54cb6d0670e6e01bdbfc88e113cc3`
- Review timestamp: `2026-08-07T12:11:16+08:00`
- Result: **FAIL**
- Severity totals: `P0=0`, `P1=0`, `P2=2`
- Mutation: none; the reviewer changed no files or Git state and performed no build, download, device or emulator operation

## Prior findings

Fourteen of the fifteen findings from reviews 1 through 5 were independently verified **CLOSED**. Review-5 finding 1 remained open because the replacement gate captured only part of the actual internal symbol surface; review-5 finding 2 was closed. Frozen-SHA Build `31145662390`, Governance `31145662398` and API 29/36 KVM `31145662402` were all successful and bound to the exact commit.

## New findings

1. **P2 — the exact internal symbol contract captured 12 of 17 related local symbols.** Every four-ABI unstripped Release ELF also contained `ctr_drbg_update_internal`, `entropy_gather_internal`, `entropy_update`, `mbedtls_platform_get_entropy` and local-data symbol `mbedtls_platform_dev_random`. The lock and CI prefix filter omitted them and compared only names, not the required local `t/d` binding type. Dynamic upstream exports remained zero and no RSA/CBC/PSA-ECB/padding/TLS path became reachable, but the claimed least-surface gate was incomplete.
2. **P2 — HandOff ordered actions again lagged completed facts.** It still described the review-5 remediation commit/push, exact-head CI and review 6 as future work after all three had occurred.

## Disposition required

The frozen SHA is permanently rejected. The machine lock and Ubuntu/Windows four-ABI gates must compare the full seventeen-symbol set by exact name and local `t/d` type and reject any missing, extra, type-changed or dynamically exported member. ADR, task, toolchain and vulnerability evidence must describe the same boundary. HandOff must record this failed review and set the next checkpoint to a new remediation SHA, replacement Build/Governance/KVM and seventh complete independent read-only review. PR #42 remains draft and M2-02 remains paused.
