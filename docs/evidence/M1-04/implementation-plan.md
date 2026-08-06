# M1-04 implementation plan

## Fixed scope

- Issue: `#9 [M1-04] Encrypted DEX container`
- Branch: `feat/m1-04-encrypted-dex-container`
- Base: `main@ebbe92830cd5f3a4f3c7a51f058d8d5f6f74912a`
- Validation mode: `pre-cli`
- Product scope: Host-only AHDC v2 container/config construction and read-only verification.
- Excluded: Runtime C++ consumption, APK ZIP injection, signing, CLI exposure, devices and emulators.

The rejected AHDC v1 implementation remains only on local archive branch
`spike/m1-04-rejected-ahdc-v1`. No source commit from that branch will be
merged, cherry-picked or used as the implementation base.

## Contract

The implementation follows accepted ADR 0008 and ADR 0006 without changing
wire fields:

- 160-byte HeaderV2, 128-byte RecordV2 and 32-byte ChunkV2;
- one continuous zlib-wrapped level-9 stream per DEX, split after compression
  into canonical 65,536-byte authenticated plaintext chunks;
- one AES-256-GCM message per chunk, with the exact ADR 0008 nonce and AAD;
- manifest HMAC over the zero-MAC header, SPV1, records and chunk table;
- 768-byte ConfigV2 with `container_major=2` and a one-shot in-memory
  `KeyPackagingPlanV2`;
- strict checked arithmetic, canonical ordering, full consumption and no v1
  fallback.

`DexContainerBuilder` owns an explicit input APK path and exposes the task-card
`build(ApkInspection, SignerPolicyV1, Path)` operation. It reads only the
canonical root DEX entries named by the inspection model and requires the APK
hash, package digest, DEX length/digest and both compression passes to remain
unchanged. The output path must be absent and distinct from the input.

`DexContainerVerifier` accepts only explicit `ExpectedBinding` material derived
from the one-shot plan. It authenticates each chunk before feeding the
continuous inflater, verifies exact DEX size/digest/order, and never writes DEX
or compressed plaintext to disk.

## Failure and cleanup

Stable errors are limited to the task-card `CONTAINER_*` taxonomy. The first
failure remains primary; cleanup failures are suppressed. CEK, root material,
shares, KEK, derived keys, AAD, authenticated compressed chunks and temporary
digest material are wiped in `finally` blocks. A failed build removes its
partial encrypted output and never leaves a successful-looking artifact.

The implementation will keep simultaneous explicitly allocated crypto/zlib
buffers below 1 MiB and will not materialize a payload-sized buffer or an
object-per-chunk table. Metadata tables are emitted/re-read as fixed-width
records.

## Verification map

- Standard vectors: RFC 5869 HKDF, NIST AES-GCM and zlib-wrapped DEFLATE.
- Positive: single/multi-DEX round trip, exact order/size/SHA-256, independent
  parser pass and one-shot plan ownership.
- Determinism/randomness: fixed RNG golden bytes across platforms; production
  runs have distinct CEK/build/key/share/nonces and container SHA-256.
- Boundaries: 1/65,535/65,536/65,537 compressed-byte topology, checked
  overflow/count/offset/truncation/trailing input and v1/unknown flags.
- Tamper: every HeaderV2/SPV1/RecordV2/ChunkV2/config/envelope/ciphertext/tag
  binding class fails closed before affected compressed bytes enter inflater.
- Failure paths: input changes between passes, I/O/cancellation injection,
  malformed authenticated zlib, primary/suppressed cleanup precedence and
  zeroization hooks.
- Hygiene: input APK hash unchanged, no plaintext DEX/compressed file in the
  work directory, no signing/private-key/keystore capability and no absolute
  security claim.

## Completion gate

Run `:host:container:test`, the repository root `clean check verifyGovernance`,
strict HandOff, diff/UTF-8/sensitive scans and a `pre-cli` validation report.
Freeze a clean commit and obtain an independent read-only cryptography and
binary-format review with P0/P1/P2 all zero before requesting publication.
