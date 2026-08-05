# M1-04 Independent Security Review 4

- Frozen commit: `25d12336f912a14d889eb594a089cfe6178047fb`
- Base commit: `ebbe92830cd5f3a4f3c7a51f058d8d5f6f74912a`
- Result: **FAIL**
- Findings: `P0=0`, `P1=1`, `P2=0`
- Reviewer mode: independent, read-only

## Blocking finding

### P1 — Remaining ownership-before-try windows

Review 3's four direct findings are closed, but the same systemic pattern remains:

- Builder record keys are derived before stream construction is protected by `finally`.
- Chunk nonce, AAD, and compressed-plaintext copies are created before the cleanup region; an allocation observer failure can leave plaintext uncleared.
- Verifier manifest keys are derived before the zeroed-header copy is protected.
- Verifier ciphertext, nonce, and AAD are obtained before the per-chunk cleanup region.
- Pass-2 mismatch exits before wiping its digest.
- Pass-1 mismatch/failure and `CompressionObservation` construction failure can leave returned digest arrays without an owner.

Required remediation: transfer every derived key, crypto input, compressed plaintext,
and temporary digest to a fixed cleanup owner before the next fallible operation,
then add OOM, observer-failure, and mismatch injections for these exact boundaries.

## Closed review-3 findings

- Verifier ConfigV2/`R_native` copying is transactional.
- ConfigV2 recovery registers every allocated derivation buffer.
- HKDF protects PRK/output/previous allocations with `finally`.
- Random destinations are registered before provider fill.

## Independent verification

- `node tools/validation/verify-ahdc-v2-vector.mjs`: exit `0`, 0.066 seconds, `PASS records=2`.
- `:host:container:check --offline --no-daemon --console=plain --no-configuration-cache -Pkotlin.compiler.execution.strategy=in-process`: exit `0`, 50.527 seconds, 13 self-test groups passed.
- Environment: Windows 10 x64, Eclipse Temurin JDK 17.0.19+10, Gradle 9.5.0, SunJCE.
- Java processes after review: `0`.
- Start/end HEAD: `25d12336f912a14d889eb594a089cfe6178047fb`; tree `c6db9b67380d0a9ff4f5a37023e466c70dbb0723`; worktree clean; reviewer changed no files.

## Artifact hashes

- Fixed AHDC: `3764b908e534ffa5179a9519045ec74a7caa44b30c80447998c593a1ac2fa60d`
- Vector JSON: `3b2421fcc91234333d13545826b51fbf0de25c5fa26b39aa17d90a9ff2133afc`
- Node report: `542ba9db02b643f445fc9194220e7fac6debb28e45089de38403843c78be2b1a`
- Self-test report: `faf6985daa680ba87e297e0b643fd8d56bec7358e861e3fe9b1aecbd74be8a0e`
- Production A: `9cc76ad417b9718734c011260021e4aa47bff609c0312088833d57835a16d774`
- Production B: `6877cae1f3fc7e9279c5cbe93c40f613136ffed385b30ed1350a815fde95b48e`
