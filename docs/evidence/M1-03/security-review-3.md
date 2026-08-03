# M1-03 third independent parser/security review

## Review target and outcome

- Branch: `feat/m1-03-binary-axml-transformer`
- Frozen commit: `1425f911eb48796a8e4ade9aa3c5fcec09cb1f7b`
- Mode: independent, offline and read-only
- Verdict: **PASS**
- Findings: P0 `0`, P1 `0`, P2 `0`

The reviewer changed no tracked file, used no network, device or emulator, and confirmed the worktree stayed clean at the same commit.

## Finding closure

- The second-review path-memory P1 is closed: complete ancestor paths are gone. Element frames retain only namespace/name indices and `isManifest`; the direct application whitelist uses the bounded `isApplicationElement` role flag.
- The 32,767-character element-name and 1,025-level nesting fixture was run and returned `AXML_LIMIT_EXCEEDED` without OOM, crash or unrelated exception.
- All four first-review findings remain closed: Factory attribute extensions are preserved; style/namespace work is bounded; chunk allocation and unknown summaries are bounded; high-bit unsigned and explicit preservation evidence remain covered.
- The semantic whitelist excludes only the direct application's `android:appComponentFactory`. Other ordered events, typed values, resource IDs, attribute extensions and unknown summaries remain compared.
- Public exceptions expose only stable `AXML_*` codes and optional numeric offset/type values.
- Production `host/axml/src/main` contains no APK/ZIP writer, signing tool, keystore/private-key handling or process execution capability.

## Independent verification

- `:host:axml:test --offline`: exit `0`; 6 positive fixtures, 18 negative fixtures and 5,000 seeded malformed samples.
- `check verifyGovernance --offline`: exit `0`; 237 actionable tasks.
- strict HandOff, two Node syntax checks and base-to-HEAD `git diff --check`: exit `0`.
- Production signing/ZIP capability scan: no match.
- Environment: Windows `10.0.19045` amd64; Temurin `17.0.19+10`; Gradle `9.5.0`; Node `24.12.0`; aapt2 `2.20-14042983`.

Canonical report SHA-256 values:

- `transform-matrix.json`: `35bd420aa0fe05e1a5efee197bdea8d3699f5de743bf54974f13833e24ef5635`
- `error-matrix.json`: `9a60c0c9fe710798d7f458822c1f2d6ffb9a22527a43c176c6c3869fb6dcf49c`
- `fuzz-summary.json`: `d1dbf919a489a067506ab40b629916ea66a5b8a3e3ced42e710ae8dc57f8dced`
- `aapt2-cross-check.json`: `916e2d79af152c6090fc7ba0c4b9b24f054f0eb094ff2968192b922d0d593672`

API 29 physical-device acceptance remains blocked by MIUI `INSTALL_FAILED_USER_RESTRICTED`; API 29/36 x86_64 KVM, Ubuntu/Windows equivalence and publication remain pending and are not represented as passing.
