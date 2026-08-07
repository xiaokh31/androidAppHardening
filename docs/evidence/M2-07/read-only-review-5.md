# M2-07 independent read-only security review 5

- Frozen commit: `a6497c2713e9025db11c4b5ccc657d42beb9236e`
- Result: **FAIL**
- Severity totals: `P0=0`, `P1=0`, `P2=2`
- Scope: immutable dependency identity, extraction boundary, license and vulnerability evidence, Host vectors and failure paths, Android four-ABI Release surface, CI/toolchain assertions, governance truthfulness and M2-02 resume gate
- Mutation: none; the reviewer was read-only and did not start a device or emulator

## Prior findings

All thirteen findings from reviews 1 through 4 were independently verified **CLOSED**. The frozen SHA's exact-head Build `31143702806`, Governance `31143702757` and API 29/36 KVM `31143702763` runs were all successful.

## New findings

1. **P2 — recorded algorithm surface contradicted the built four-ABI surface.** Each unstripped Release ELF retained the local symbols `mbedtls_aes_crypt_ecb`, four `mbedtls_ctr_drbg_*`, four `mbedtls_entropy_*` and three `psa_random_internal_*` symbols. They were not dynamically exported and no public facade exposed them, but the ADR and vulnerability record claimed ECB/random support was excluded while CI's forbidden-symbol expression did not match the actual names. The review could not accept vulnerability reachability or least-surface evidence until the implementation either removed the support or explicitly machine-locked and reviewed the required internal boundary.
2. **P2 — HandOff active-workstream truth was stale.** M1-06 still said that post-merge main gates were pending, and M2-07 still described the first-review remediation as current despite four later review cycles.

## Disposition required

The frozen SHA is permanently rejected. Remediation must either remove the internal DRBG/entropy/AES block code without inventing cryptography, or document why PSA initialization requires it and bind the exact configuration and exact four-ABI local-symbol set into failure-closed machine and CI gates. Vulnerability evidence must distinguish an internal AES block primitive from a consumable ECB/padding API and prove that RSA/CBC/padding decrypt paths remain unreachable. HandOff must describe current state. The replacement SHA then requires full exact-head Build, Governance and KVM runs plus a sixth complete independent read-only review with all fifteen findings dispositioned and `P0/P1/P2=0`.
