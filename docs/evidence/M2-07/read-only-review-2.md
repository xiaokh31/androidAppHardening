# M2-07 independent read-only security review 2

## Frozen input

- Commit: `699cbda469a85501294b7a83587ce89faaad7192`
- Reviewer: independent `m2_07_security_review_2` Agent
- Result: **FAIL**; `P0=0`, `P1=0`, `P2=3`
- Files changed by reviewer: none
- Branch/build/download/device/emulator actions: none

## New findings

1. **P2 — Unix symlink verification failed open.** `tools/validation/verify-m2-07-native-crypto.mjs` accepted either `0` or `147` symlinks on every platform. Deleting all 147 locked links after Linux extraction left the regular-file tree unchanged and still allowed the verifier to stamp and promote an incomplete tree. Unix must require exactly `147`; only Windows may accept `0` when the reviewed extractor skips the links (or `147` when they are preserved).
2. **P2 — Ubuntu runner and Host compiler were not pinned.** Build and KVM used the rolling `ubuntu-24.04` label without runtime assertions. Exact-SHA run `31136456081` reported image runtime `20260720.247.2`, manifest ref `ubuntu24/20260720.247` and GNU C/C++ `13.3.0`. A runner roll could therefore silently change the Host crypto-vector compiler and environment.
3. **P2 — README state contradicted the frozen repository.** README claimed M2 had not started while the same SHA declared active M2-07, draft PR #42 and open Issue #41, with M2-02 paused.

The symlinks are confined to disabled ML-DSA examples, Android artifacts remain pinned to NDK 29, and the README issue is governance truthfulness; no finding exposed a new Runtime cryptographic capability. All three findings nevertheless invalidate this frozen SHA.

## First-review disposition

| Review-1 finding | Result | Independent evidence |
| --- | --- | --- |
| P1 archive parsed before authentication and incomplete regular-tree binding | CLOSED | Archive-only verification precedes every parser; empty temporary extraction, regular-tree identity, stamp, atomic promotion and failure cleanup are present. The new platform-specific symlink gap is tracked separately above. |
| P1 incomplete crypto boundary matrix | CLOSED | AES key/nonce/tag/capacity/zero/null and HKDF zero/8160/8161/null/optional-empty cases are covered. |
| P1 unsafe concurrent PSA use | CLOSED | The complete AES/HKDF transaction is serialized by a global mutex and covered by 8 threads x 100 iterations. |
| P2 Debug rather than Release scan | CLOSED | Both platforms build `assembleRelease` and scan stripped Release plus RelWithDebInfo for all four ABIs. |
| P2 incomplete machine lock verification | CLOSED | Hardcoded expected lock plus deep equality and field/archive mutation tests fail closed. |
| P2 Windows clang not fixed | CLOSED | Exact Windows image, LLVM `20.1.8` and `cl.exe 19.51.36252` are asserted. |
| P2 missing CVE-2026-25832 | CLOSED | 4.1.1 affected/4.1.2 fixed and TLS-unreachable status are recorded; any TLS enablement requires upgrade and re-review. |

## Positive evidence

- Official archive identity remained `7099934` bytes/SHA-256 `3359a349...5c98c`; checksum asset, tag object `783058d...`, commit `0a8fda2...`, TF-PSA `1.1.1`, Apache-2.0 selection and both license hashes matched.
- NIST AES-256-GCM, RFC 5869, authentication-failure zeroing, PSA abort/destroy, maximum HKDF boundary and concurrency contracts were consistent.
- Four Release ABIs depended only on `libm.so`, `libdl.so` and `libc.so`, used `BIND_NOW`, exported no upstream API, and exposed no TLS/X.509/RSA/ECC/ChaCha/CCM/CBC/PKCS/private-libcrypto surface.
- CVE-2026-54435, CVE-2026-50584 and CVE-2026-50587 fixes were present. CVE-2025-66442 and CVE-2026-25832 remained affected upstream but unreachable under the locked algorithm/TLS exclusions.
- Build `31136456081`, Governance `31136456078` and API 29/36 KVM `31136456090` all succeeded on the exact frozen SHA. PR #42 remained draft; M2-02 remained paused at local commit `40e3900bdb0bd5c23a5fe52247e5a60ebe949cf9` and had no remote branch.

## Conclusion

This frozen commit is permanently rejected. Close all three P2 findings, create a new SHA, rerun exact-head Build/Governance/KVM, and obtain a fresh complete independent read-only review before ready/merge or M2-02 resume.
