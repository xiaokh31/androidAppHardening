# M3-01 local validation

- Timestamp: `2026-08-14T02:23:00+08:00`
- Branch: `chore/m3-01-android-fixtures`
- Base: `6a1aa6a68ac58e0861f1e866c613138c5a9bc24c`
- Host: Windows 10.0.19045, JDK Temurin `17.0.19+10`, Gradle `9.5.0`, Kotlin `2.3.20`
- Android: compile SDK `36`, Build Tools `36.1.0`, NDK `29.0.14206865`
- Device boundary: no local emulator was started and no APK was installed locally. API 29/36 x86_64 KVM and ARM device evidence remain pending until the branch is published.

## Commands

| Command | Exit | Result |
|---|---:|---|
| `gradle :fixtures:android:assembleFixtures :integration-tests:test --offline --no-daemon --console=plain` | 0 | Nine Release/R8 source fixtures, deterministic unsigned assembly, catalog/schema/DEX/ABI checks and exact in-task double-build comparison passed in 46 seconds. |
| `gradle :integration-tests:runFixtureHostMatrix --offline --no-daemon --console=plain` | 0 | All nine signed-input/product/unsigned-output/same-signer-output flows plus unsigned-input and multiple-current-signer negatives passed in 5m16s. |
| `gradle :host:repacker:test --offline --no-daemon --console=plain` | 0 | Four ABI policies, ELF `SHT_NOBITS` regression and failure matrix passed in 45 seconds. |
| `node tools/governance/validate-project-package.mjs` | 0 | `28` task cards, `11` core documents and `11` ADRs passed. |
| `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md` | 0 | Active HandOff is structurally valid. |
| `git diff --check` | 0 | No whitespace errors. |

## Deterministic unsigned fixtures

`assembleFixtures` canonicalizes each source APK twice in one invocation and compares the resulting bytes before publishing. Current SHA-256 values are:

| Fixture | SHA-256 |
|---|---|
| `java-single-dex` | `d810607dc42da4d1c9678c99f14ecc5b3b2994225612fa340a321f77085bca0a` |
| `kotlin-single-dex` | `fe82bf540d9ff03917634cd59e93db0d30eaed9adb2356111c92e4cc2bf8134d` |
| `kotlin-multidex` | `f0a4f663a00b12ab89f1b084f3c32d930cd827c196e241f0976e5d08ffb33de8` |
| `custom-application` | `3ece0bce161ecf610b93a453634b2118efeeedd2b1c763dfcb3e309fb296e9eb` |
| `custom-factory` | `7a9c0b97d4622772992535208640ecdb392789e491285c8c515273613b1c9af2` |
| `startup-provider` | `7b30d439817b65623d0580d21cfc63f0d7bc237dd803cc2949f7b26a61426859` |
| `multi-process` | `9aef72f8eada095a3d42dc64f97acf008ed088388d5957bf7491e2563e94a7de` |
| `jni-four-abi` | `fcc8d6ebd4b0308331aa537c533a6ec11b0fdb2c92319ac8ef820441135a1bf4` |
| `jni-arm-only` | `5b008bf98183e88400e74d033a2d5585d5dc28e7a6757ba2653903b550ba3231` |

The Kotlin multi-DEX APK contains a compact D8-generated `classes2.dex`; the catalog test proves `SecondaryMarker` exists only in a secondary DEX and the application loads it by the runtime class loader.

## Host full-flow report

- Report: ignored `integration-tests/build/reports/fixture-host-results.json`
- Bytes: `6049`
- SHA-256: `1a35855a89cfc70a0db37a3558f003f4e10af2c7fdf3ad14028f7a83b12c22fe`
- Result: `status=pass`, `fixture_count=9`, `test_signing_cleanup=true`
- Negative matrix: `unsigned_input`, `multiple_current_signer`
- Each row records the signed input, unsigned product output, externally signed output and product report SHA-256; input immutability, unsigned product output and identical current signer all passed.

The per-run RSA-3072 test certificates, signed APK copies and passwords existed only under ignored `integration-tests/build/test-signing/`. The driver's `finally` removed that directory; the final check returned `False`. A changed-file scan found no APK, DEX, keystore, private-key marker, token pattern or UTF-8 replacement character.

## Pending completion gates

The implementation is locally ready, but M3-01 remains active. Completion still requires publishing the frozen branch, Ubuntu/Windows CI, API 29/36 x86_64 device full-flow, ARM device validation for `jni-arm-only`, evidence reconciliation, README update and final strict HandOff after merge.
