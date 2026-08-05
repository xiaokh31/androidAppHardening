# M1-04 Independent Security Review 3

- Frozen commit: `ee5553ba0318f3b6560858f15549b556e767bbce`
- Base commit: `ebbe92830cd5f3a4f3c7a51f058d8d5f6f74912a`
- Result: **FAIL**
- Findings: `P0=0`, `P1=1`, `P2=0`
- Reviewer mode: independent, read-only

## Blocking finding

### P1 — OOM sensitive-material ownership is not closed

Several paths allocate or copy sensitive arrays before a cleanup owner is fully established:

- `DexContainerVerifier` obtains the ConfigV2 copy before the `R_native` copy. If the second copy fails, the first copy is not wiped.
- `ConfigV2Codec.recoverCek` derives `R_java` and the root before later nonce, prefix, and envelope allocations. A later allocation failure can bypass cleanup of the earlier values.
- `ContainerCrypto.hkdfSha256` derives the PRK before allocating output/previous buffers. Allocation failure can bypass PRK cleanup.
- `SecureContainerRandom` allocates and fills an array internally. If the provider fails during `nextBytes`, `BuildSecrets` has not yet registered ownership and cannot wipe the partial random material.

Required remediation: establish transactional ownership before every subsequent fallible allocation, let `BuildSecrets` allocate/register random destinations before filling them, and add OOM injection tests for these consumption and derivation windows while preserving primary-error precedence.

## Prior findings

- Review 1 findings are closed.
- Review 2 dynamic-validation gap is closed.
- Review 2 OOM ownership finding is only partially closed; constructor-copy paths are fixed, but the consumption/derivation windows above remain.

## Independent verification

- `node tools/validation/verify-ahdc-v2-vector.mjs`: exit `0`, `PASS records=2`.
- `:host:container:check --offline --no-daemon --no-configuration-cache -Pkotlin.compiler.execution.strategy=in-process`: exit `0`, 44 seconds, 13 self-test groups passed.
- Environment: Windows 10 x64, Eclipse Adoptium JDK 17.0.19, Gradle 9.5.0, SunJCE.
- Java processes after review: `0`.
- Before/after HEAD: `ee5553ba0318f3b6560858f15549b556e767bbce`; worktree clean; no files changed by reviewer.

## Artifact hashes

- Fixed container: `3764b908e534ffa5179a9519045ec74a7caa44b30c80447998c593a1ac2fa60d`
- Vector JSON: `3b2421fcc91234333d13545826b51fbf0de25c5fa26b39aa17d90a9ff2133afc`
- Node consumer report: `542ba9db02b643f445fc9194220e7fac6debb28e45089de38403843c78be2b1a`
