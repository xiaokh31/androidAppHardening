# M2-02 Implementation Plan

## Scope

- Task: `M2-02` only.
- Branch: `feat/m2-02-native-decrypt-loader`.
- Validation mode: `pre-cli`; the test-only integration driver consumes project-generated M1-04 vectors and never exposes a new product CLI.
- Owned production module: `runtime/native`.
- Out of scope: Shell bootstrap, signer verification policy, Guard/session publication, environment scoring, release signing, customer APKs and plaintext DEX files.

## Inputs and outputs

Input is the Framework-provided absolute `ApplicationInfo.sourceDir`, exact `ApplicationInfo.packageName`, shell `ClassLoader`, and a caller-supplied 32-byte signer digest that M2-03 will later derive from a verified installed APK. Native code locates only the fixed STORED assets `assets/ah/runtime/config.bin` and `assets/ah/runtime/payload.ahdc` and consumes the compiled current-ABI share slot.

Success returns an internal `LoadedPayload` that owns one typed Native handle, ordered direct DEX mappings, an unused provisional `InMemoryDexClassLoader`, and immutable authenticated metadata. Failure returns a stable `AAH-RUNTIME-CONTAINER-*` category, closes every owned mapping exactly once, clears temporary recovery material, and never returns a buffer, loader or partial object.

## Public and internal interfaces

- Public low-level facade: `ah.runtime.loader.PayloadRuntime`.
- Public ownership object: `LoadedPayload implements AutoCloseable`.
- Public immutable metadata: `AuthenticatedPayloadMetadata` with exactly the ten task-card accessors and deep-copy array semantics.
- Public explicitly untrusted pre-read: `UntrustedPayloadBinding`.
- Package-private JNI, handle and loader helpers: `NativePayloadBridge`, `PayloadMemoryHandle`, `PayloadClassLoaders`.
- JNI names, fixed assets and `libah_runtime.so` follow the task card exactly. No caller-controlled asset name or Native search path is accepted.

## Security and compatibility behavior

1. Validate ZIP central/local headers, uniqueness, STORED method, flags, CRC, lengths and checked offsets before trusting bytes.
2. Validate ConfigV2, current ABI `NativeShareSlotV1`, package/signer binding and CEK envelope.
3. Validate AHDC v2 topology and manifest MAC before any record decryption.
4. Authenticate each canonical chunk with one-shot AES-256-GCM, then and only then feed it to that record's single zlib-wrapped inflater.
5. Enforce exact output length and SHA-256, anonymous direct mappings, ordinal order and no trailing input.
6. Clear CEK/KEK/record keys/AAD/compressed plaintext/inflater scratch before handle return; retain only completed DEX mappings and non-secret metadata.
7. Support API 29 through compileSdk 36 and the four declared Runtime ABIs without claiming that shell ABI injection converts customer Native ABI support.
8. Keep the existing M0-05 extracted-plus-APK Native search-path algorithm; reject no match, duplicate ABI aliases and non-canonical library paths.

## Acceptance tests

- Positive: M1-04 single/multi-DEX golden vectors, exact source SHA-256, DEX ordinal and duplicate-class priority, metadata field equality and deep copies.
- Tamper: header, SPV1, record/chunk tables, signer/package/config/build/key slot, ciphertext/tag, offsets/counts/lengths and unknown versions.
- Compression: zlib wrapper/checksum, dictionary, raw/gzip, concatenated/trailing, early end, declared length and expansion limits.
- ZIP: duplicate fixed assets, compression/encryption/descriptor, CRC/central-local mismatch, ZIP64 bounds and truncation.
- Ownership: pre-handle first/middle/last failure injection; post-handle metadata/buffer/path/loader/object/return injection; exactly-once close, primary-error precedence and reference clearing.
- Device: API 29 and 36 x86_64 KVM; direct/extracted Native search path; no new disk file starting with DEX magic; JNI and ordered in-memory class resolution.
- Static: no hidden API, no filesystem DEX output, no secret logging, no `ah.runtime.guard` or M2-01 production implementation.

## Evidence plan

Record exact Windows/Ubuntu commands, tool versions, API/ABI matrix, test counts, sanitizer/static-scan results, source/recovered DEX hashes, AAR/SO/test-report hashes, known limitations and the independent review verdict. Generated APKs, DEX bytes, keys, test certificates and device artifacts remain under ignored `build/` or CI artifacts only.

## Preimplementation prerequisite closure

M2-07 closed the Native cryptography blocker through ADR 0009 and merged PR #42. The only approved backend is the immutable Mbed TLS `4.1.1` full archive with bundled TF-PSA-Crypto `1.1.1`, locked to `7099934` bytes and SHA-256 `3359a349e23db3d5536fcee032ae7b2ecbfc08972fab643089b5cbf2a375c98c`. License/NOTICE, security-advisory reachability, NIST AES-256-GCM, RFC 5869, Host concurrency/failure paths, the exact seventeen-entry internal symbol surface, zero related dynamic exports and all four Android ABIs passed independent review with `P0=0/P1=0/P2=0`.

PR #42 merged as `1ac8e308236078827ec3e4a8f438514dcf69b10c`. Final `main@e78fcaed58dd5211a465ea37a94db45dddc17dfa` passed Build `31151358692`, Governance `31151358963` and manually dispatched API 29/36 KVM `31151403785`, including cleanup and evidence upload. This branch has merged that exact main and may now implement only the bounded M2-02 container/parser/loader scope above; M2-03 and adjacent work remain out of scope.
