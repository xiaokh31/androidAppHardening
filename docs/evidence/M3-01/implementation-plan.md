# M3-01 implementation plan

- base: `6a1aa6a68ac58e0861f1e866c613138c5a9bc24c`
- branch: `chore/m3-01-android-fixtures`
- issue: `#18`
- mode: full-flow, using only repository-generated fixtures and an integration-test-only RuntimeBundle assembled from the frozen M2 runtime

## Fixed scope

The task adds exactly the nine fixture IDs required by `docs/tasks/M3-01-android-fixtures.md`: Java and Kotlin single DEX, Kotlin multidex, custom Application, custom AppComponentFactory, startup Provider, multi-process, four-ABI JNI, and ARM-only JNI. It adds the versioned catalog/schema, deterministic unsigned fixture assembly, `FixtureDescriptor`, the bounded matrix driver, and machine-readable reports. M3-02 fuzzing, M3-03 cross-platform corpus comparison, M3-04 the complete API/ABI matrix, and M3-05 benchmarks remain unstarted.

## Signing and device boundary

Generated APKs and signing material remain under ignored `build/` directories. The driver creates a fresh non-production certificate for each run, signs the input and protected install copy externally with the same certificate, verifies that the product output remains unsigned, and removes the signing directory in `finally`. The product CLI never receives a keystore, alias, password, private key, or signing option. Device validation uses a bounded adb invocation and a fixture-owned read-only event provider; it does not scrape general logcat and it always uninstalls its packages.

## Efficient validation

Local validation is limited to catalog/schema checks, deterministic assembly, host full-flow/signing negatives, targeted fixture tests, governance, strict HandOff, diff checks, and sensitive-material scans. API 29/36 device execution belongs to bounded CI after publication authorization; no local emulator is started.
