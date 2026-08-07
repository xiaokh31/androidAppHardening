# M2-07 implementation plan

## Authorization and scope

- Authorized by the user on 2026-08-07 as an independent Native cryptography backend ADR/task-contract and supply-chain revision.
- Issue: [#41](https://github.com/xiaokh31/androidAppHardening/issues/41).
- Branch: `chore/m2-07-native-crypto-backend` from `main@1bce1f61a3edcebdf94a511c495006a38edb6cb4`.
- M2-02 remains preserved at local `feat/m2-02-native-decrypt-loader@40e3900bdb0bd5c23a5fe52247e5a60ebe949cf9` and is not resumed until M2-07 merges and final `main` gates pass.

## Fixed dependency

- Mbed TLS `4.1.1` official full archive, 7,099,934 bytes, SHA-256 `3359a349e23db3d5536fcee032ae7b2ecbfc08972fab643089b5cbf2a375c98c`.
- Annotated tag object `783058d12831aedd3ef57a64577f6f8a88d23bd3`, commit `0a8fda272a5a0abef3b47c91bed37185d5a726b1`.
- Bundled TF-PSA-Crypto verified from archive as `1.1.1`; selected license Apache-2.0.
- Large archive/source stays under ignored `.toolchains/native-crypto/`; no dependency or toolchain is downloaded to C:.

## Verification route

1. Machine lock validates every immutable identity field. Downloaded bytes are verified before parsing, extracted only into an empty temporary directory, bound to a complete regular-file tree hash, stamped and atomically promoted.
2. Host Release self-test runs NIST AES-256-GCM and RFC 5869 HKDF-SHA-256 vectors, the complete required boundary/null matrix, and an eight-thread serialized-backend stress matrix on Ubuntu and Windows.
3. Fixed NDK 29/CMake 4.1.2 builds four Android Release ABIs in CI; stripped and unstripped ELF scans reject dynamic crypto/TLS dependencies, upstream exports and out-of-scope local crypto objects.
4. Governance, full root checks, diff/sensitive-data scans and evidence are frozen in a commit.
5. A fresh independent read-only security-review Agent reviews the exact frozen SHA. Findings invalidate the freeze until fixed and re-reviewed.
6. Only a passing frozen revision may be pushed as merger-ready, merged with expected-head protection, and followed by strict HandOff and final `main` CI.

## Explicit exclusions

- No AHDC parser, record/chunk derivation, zlib, DEX mapping, JNI handle or ClassLoader implementation.
- No TLS, X.509, RSA, ECC, PKCS, signing, networking or runtime dynamic backend selection.
- No local emulator. Four ABI compilation runs in GitHub Actions; device behavior remains M2-02/M2-04 scope.
