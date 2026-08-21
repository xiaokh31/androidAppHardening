# M3-10 five-finding review remediation

- Timestamp: `2026-08-21T12:07:30+08:00`
- OS: Windows `10.0.19045` x64
- Branch: `feat/m3-10-startup-attribution-diagnostic`
- Base: `9d3fc3a4ae17d14f84d223b9dbb5f92016814f1a`
- Implementation freeze: `19fd56d0068e5416b390703c24a2c1d100d27a1b`
- Scope: review-1 findings only; no workflow, emulator, KVM, physical device, benchmark, ARM or M3-05 execution

## Finding closures

1. The tracked profile lock fixes the canonical APKs, observer source/DEX, derivation manifest and DEX outputs, unsigned/aligned/signed APKs, v3-only signer commitment, pinned toolchain and non-regeneration policy. Actual lock verification passed.
2. The verifier no longer calls the transformer as an equivalence oracle. It independently compares every non-probe DEX instruction through pinned `dexdump`, compares class/field/method/annotation/handler/debug/access metadata through dexlib2, and checks the two synthetic lifecycle overrides as exact four-instruction methods. The authenticated protected payload is compared after decryption.
3. `prepare-m3-10-profile-package.mjs` is the tracked test-only orchestration. One bounded execution compiled the observer, performed two deterministic post-build derivations, aligned and v3-only signed both pairs, compared unsigned/aligned/signed bytes, and removed the temporary keystore/password/seed root in `finally`. The final secret-root absence check passed.
4. Production source and build-script diff gates remain fail closed. Actual Release bootstrap/policy/native AARs, Release/R8 fixture APK, CLI ZIP and distribution JAR were recursively scanned as binary archives; a NUL-containing binary observer mutation was rejected.
5. The tracked runner and verifier now cover exact API/run/boot identity, 5+15 ordered samples, raw per-start calibration, adjacent timelines, nine-owner reconciliation, lifecycle events, immutable package hashes, cleanup, official GitHub page hashes/pagination/terminal uniqueness and downloaded artifact binding. Self-tests rejected 25 report/cleanup/GitHub mutations, 14 actual profile/APK/DEX/signature/resource/native/surface mutations and 8 production-surface mutations.

## Bounded commands and results

| Command | Exit | Result |
|---|---:|---|
| Node syntax for preparation, runner and verifier | 0 | PASS |
| `verify-m3-10-startup-attribution.mjs profile-lock ...` | 0 | exact canonical lock and common v3 signer commitment PASS |
| `verify-m3-10-startup-attribution.mjs apk-pair ...` for baseline/protected | 0/0 | independent non-probe DEX/APK comparison PASS |
| `:host:container:m310VerifyProfiles --offline` | 0 | signer, Manifest, metadata, exact overrides, authenticated container and four-ABI share-only delta PASS |
| `prepare-m3-10-profile-package.mjs ...` | 0 | two deterministic derivations/signatures and cleanup PASS |
| `verify-m3-10-startup-attribution.mjs self-test` | 0 | 25 named report/cleanup/GitHub mutations rejected |
| `verify-m3-10-startup-attribution.mjs profile-self-test ...` | 0 | 14 actual-file mutations rejected |
| `verify-m3-10-profile-freeze.mjs --self-test --base-ref ...` | 0 | 8 source/binary mutations rejected; workflows absent |
| `:host:container:test --offline` | 0 | 13 container/cleanup/tamper cases PASS |
| project governance | 0 | 36 tasks, 11 core docs and 16 ADRs PASS |
| `git diff --check` | 0 | PASS |

The first three preparation preflights exposed only tracked-script integration defects (D8 directory input, Windows batch invocation and seed-file argument). Each failed before producing a candidate package, and the script's `finally` removed the complete output and signing root. After those fixes, one complete bounded preparation passed; it was not repeated.

## Hashes

| Artifact | SHA-256 |
|---|---|
| canonical profile lock | `f0f7080414db38efcbca43b79ce58798979d6220269b6331760e06919ced546d` |
| complete verifier | `5a5b8e1bd3b7913b9ab4a41608cfa18e348559346a2db10cb28c90937c2e214d` |
| preparation script | `8beda752c076612350b2e25abcdaa40ec3258abc4e8007769b4af486e85eb5ac` |
| diagnostic runner | `0eb6f74f51027360d13ca46223527674457cad3d2b391e52507a5ec8e288a4e3` |
| actual profile verification report | `5b1f71716e95bd161904c113a584d1b4f7810026b14a3c007848190abe30c010` |
| bounded preparation report | `85115b526c490fde7cc107372074247d1eaf0a8e132c61f2e4770326af371c8f` |

## Remaining gate

This evidence does not authorize either canonical workflow. A second independent read-only review must assess the exact implementation freeze and this evidence successor. Only `P0=0/P1=0/P2=0` can unlock a separate workflow addition; the unique API 36 eligibility remains unconsumed.
