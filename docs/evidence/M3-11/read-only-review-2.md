# M3-11 independent read-only review 2

- Final reviewed implementation head: `a5397888ff7eeb9571f64d06dfc10e8edef7f37c`
- Base: `3458338e7886ac3fba8383bac47a0b655ca44533`
- Reviewer: independent `m3_11_security_review` Agent
- Result: `PASS`, `P0=0/P1=0/P2=0`
- Review mode: strict read-only; no file modification, network access, Gradle, KVM, emulator, physical device or benchmark

## Findings and closure

The first review rejected the Kotlin artifact selection, self-reported failure mapping, placeholder tuple and lexical-only artifact containment with `P0=0/P1=2/P2=2`. Replacement implementation `f16f7d4808925030f0cd7c74df89d91ae3b713df` closed those findings by selecting the exact `java-single-dex` pair, storing the exact 218-byte JSON tuple, parsing and hashing the actual manifest/repeatability/A/B reports, recomputing raw P50/delta/variation, and enforcing exact ignored-root, per-segment link/junction and realpath containment.

The second full review found one remaining P2: the M3-09 synthetic validator still calculated its same-named product tuple as `SHA-256(baselineHash + ":" + protectedHash)`. Commit `a5397888ff7eeb9571f64d06dfc10e8edef7f37c` changed that synthetic model to the same canonical JSON serialization used by M3-11. The bounded incremental review confirmed the P2 closed with no new finding.

## Independently confirmed

- The retained APK paths, sizes, SHA-256 values, v3 signature scheme, single signer and signer prefix match.
- The artifact manifest, repeatability aggregate and campaign A/B reports match their fixed hashes.
- The selected `java-single-dex/processToApplicationOnCreateMs/deltaP50` values recompute to `331/432 ms`; variation is `0.30513595166163143`, limit is `0.1`, and `pass=false`.
- The exact canonical tuple is 218 UTF-8 bytes and hashes to `883da673d3bced1ec93f11323fe63152c1007112d08c46643976c70397d0b8dd`.
- M3-11 rejected 26 named lock/evidence/path mutations; M3-09 rejected 58 named synthetic-contract mutations.
- The base diff is governance/evidence-only and introduces no Runtime, Host, fixture, benchmark or diagnostic workflow implementation.
- Governance, strict HandOff and diff checks pass.

This review authorizes publication of the M3-11 governance branch under the user's all-zero condition. It does not authorize M3-10 execution, another API 36 diagnostic, ARM, benchmark or M3-05 resumption.
