# M1-04 Independent Security Review 5

- Frozen commit: `58352c6de732887cf497de2775bc0fa3021f5332`
- Tree: `adf29eaaefbd2c5b6fbe322a49c0b79b3c7bd229`
- Base commit: `ebbe92830cd5f3a4f3c7a51f058d8d5f6f74912a`
- Result: **PASS**
- Findings: `P0=0`, `P1=0`, `P2=0`
- Reviewer mode: independent, read-only

## Static conclusions

- CEK, root, shares, KEK, HKDF PRK, manifest/record keys, nonces, ConfigV2 envelope, compressed plaintext, inflater output, digests and crypto buffers enter a fixed owner before the next fallible operation and are cleared on failure.
- RNG destinations are registered before provider fill; ConfigV2 build/recovery, HKDF, key-plan and expected-binding construction are transactional under OOM.
- Builder stream construction, chunk emission, pass-1/pass-2 observations, verifier manifest/chunk handling and cleanup callbacks preserve the primary failure while continuing best-effort cleanup.
- GCM authentication completes before a compressed chunk reaches the continuous inflater.
- Input access remains read-only; failed `.part` files are removed; no DEX or compressed plaintext is persisted.
- Descriptor/plan construction, final input hash, output size/hash and sensitive cleanup complete before `ATOMIC_MOVE`; no ordinary-move fallback exists.
- AHDC v2, SPV1 and ConfigV2 wire layouts remain consistent with ADR 0006/0008 and fixed hashes did not drift.
- All findings from reviews 1 through 4 are closed.

## Independent verification

- `node tools/validation/verify-ahdc-v2-vector.mjs`: exit `0`, 44 ms, `PASS records=2`.
- Repository-local `:host:container:check --offline --no-daemon --console=plain --no-configuration-cache -Pkotlin.compiler.execution.strategy=in-process`: exit `0`, 38.005 seconds, 13/13 self-test groups passed.
- Environment: Windows 10 x64, Eclipse Temurin JDK 17.0.19+10, Gradle 9.5.0, SunJCE, JDK zlib-wrapped level 9.
- Final repository-local Java processes: `0`.
- Start/end HEAD and tree were unchanged and the worktree remained clean; reviewer changed no files.

A preliminary wrapper invocation exited after 5.537 seconds because it attempted an unavailable distribution connection. It did not run tests, leave Java processes, or download a tool. The required repository-local cached-toolchain command above passed.

## Artifact hashes

- Fixed AHDC: `3764b908e534ffa5179a9519045ec74a7caa44b30c80447998c593a1ac2fa60d`
- Vector JSON: `3b2421fcc91234333d13545826b51fbf0de25c5fa26b39aa17d90a9ff2133afc`
- Node consumer report: `542ba9db02b643f445fc9194220e7fac6debb28e45089de38403843c78be2b1a`
- Self-test report: `2968ce0a1b5f29090d79f526f83a81d4a228d5402ec9e8e708aeb4727a19cd2b`
- Production A: `11393ba742c23c0ea0057af6a102f5084532fb56948a21e832054180782d5a40`
- Production B: `31f943a81729e3224dd729c9b57062f854efb69563da40e962a5ab3cc2b44e62`

## Remaining limitation

Offline recovery material can still be extracted by an attacker who fully controls the client process; no absolute-protection claim is made. Ubuntu/Windows PR CI remains a later publication gate.
