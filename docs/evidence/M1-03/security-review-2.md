# M1-03 second independent parser/security review

## Review target and outcome

- Branch: `feat/m1-03-binary-axml-transformer`
- Frozen commit: `99877a49c9950a64941858fa3a01d51dbf8c988e`
- Mode: independent, offline and read-only
- Verdict: **FAIL**
- Findings: P0 `0`, P1 `1`, P2 `0`

This frozen target is invalid as completion evidence. The reviewer changed no tracked file, used no network, device or emulator, and confirmed the worktree stayed clean at the same commit.

## Historical finding closure

All four findings from `security-review-1.md` were confirmed closed:

1. Existing Factory attribute extension bytes are preserved and compared after transformation.
2. Style validation has a global input-proportional work budget and duplicate-offset cache; namespaces use a 1,024-entry cap and constant-time active counts.
3. Chunk count is capped before copies, unknown chunks use one ordered aggregate digest, and total raw bytes remain under the 16 MiB Manifest limit.
4. High-bit unsigned resource-map and typed values are covered, and canonical reports explicitly bind string-index, resource-map-prefix, unknown-sequence and high-bit typed-value preservation.

## P1 finding: retained element paths amplify memory quadratically

Every start element constructed and retained its full ancestor path. With a 32,767-character UTF-8 element name nested 1,022 times under application, an approximately 95 KiB input could require about 17.2 billion UTF-16 code units. The fixed depth bound therefore did not prevent an unclassified `OutOfMemoryError` before the parser could return a stable `AXML_*` result.

Required remediation:

- Do not retain full element paths; use bounded parent/element role flags for the application whitelist and root-close validation.
- Add a long-name/deep-nesting regression that proves a stable `AXML_LIMIT_EXCEEDED` result rather than OOM or an unrelated exception.

## Independent verification

- `:host:axml:test --offline`: exit `0`; 6 positive fixtures, 17 negative fixtures and 5,000 fuzz samples.
- `check verifyGovernance --offline`: exit `0`; 237 actionable tasks.
- strict HandOff, Node syntax checks and `git diff --check`: exit `0`.
- Environment: Windows `10.0.19045` amd64; Temurin `17.0.19+10`; Gradle `9.5.0`; Node `24.12.0`; aapt2 `2.20-14042983`.

Reviewed report SHA-256 values:

- `transform-matrix.json`: `35bd420aa0fe05e1a5efee197bdea8d3699f5de743bf54974f13833e24ef5635`
- `error-matrix.json`: `8b491ed0fab772bd729274596eaa9058e747dcec9498f9d2737cf73777047240`
- `fuzz-summary.json`: `d1dbf919a489a067506ab40b629916ea66a5b8a3e3ced42e710ae8dc57f8dced`
- `aapt2-cross-check.json`: `916e2d79af152c6090fc7ba0c4b9b24f054f0eb094ff2968192b922d0d593672`

API 29 physical-device acceptance remained blocked by MIUI `INSTALL_FAILED_USER_RESTRICTED`; API 36 KVM, Ubuntu byte equivalence and publication remained pending and were not represented as passing.
