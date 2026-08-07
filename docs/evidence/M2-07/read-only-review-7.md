# M2-07 independent read-only security review 7

- Frozen commit: `4a4de2820308eb7adefa4f5cfbc76eed0dbb6031`
- Review timestamp: `2026-08-07T12:49:06+08:00`
- Result: **FAIL**
- Severity totals: `P0=0`, `P1=0`, `P2=2`
- Mutation: none; the reviewer changed no files or Git state and performed no build, download, device or emulator operation

## Prior findings

The immutable dependency, license, vulnerability, facade, actual seventeen-symbol four-ABI surface, exact-head CI and conditional Ordered Next Actions evidence passed. Sixteen of seventeen prior findings were closed; the review-6 symbol-gate finding remained only partially closed because a future uppercase/global related symbol could bypass collection. Frozen-SHA Build `31147638536`, Governance `31147638532` and API 29/36 KVM `31147638429` were all successful.

## New findings

1. **P2 — Ubuntu collection discarded non-`t/d` related symbols before exact comparison.** The filter accepted only lowercase local types while the dynamic export scan covered only `psa_`/`mbedtls_`. A future `T ctr_drbg_future_helper` or `T entropy_future_helper` could therefore evade both gates. Windows also needed the same all-type collection semantics and explicit mutation tests. Existing four-ABI ELFs still matched the intended seventeen local symbols and had zero upstream exports, so this did not make a known RSA/CBC/PSA-ECB/padding/TLS path reachable.
2. **P2 — Active Workstreams described completed work as future work.** The M2-07 row still said that replacement CI and review 7 would run after they had already completed. Ordered Next Actions itself remained conditionally self-describing and valid.

## Disposition required

The frozen SHA is permanently rejected. Both platforms must use one parser that collects every symbol with a related name before type filtering, compares the complete type-plus-name set, and rejects related dynamic exports. Parser self-test must cover extra local, global/hidden-global, missing, `t↔d`, `t→T` and export cases. HandOff's workstream row must be conditionally truthful after CI/review state changes. The new SHA requires replacement Build/Governance/KVM and an eighth complete independent read-only review. PR #42 remains draft and M2-02 remains paused.
