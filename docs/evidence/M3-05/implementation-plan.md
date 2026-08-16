# M3-05 implementation plan

- Task: `M3-05`
- Issue: `#22`
- Branch: `chore/m3-05-performance-benchmarks`
- Base: `930b759c99f330218dc4404368e9844e80456c82`
- Validation mode: `full-flow`
- Adjacent-task boundary: M3-06 and later release work are not started.

## Fixed tooling and inputs

- JMH is pinned to `1.37`; AndroidX Benchmark Macro is pinned to stable `1.4.1`.
- The measured Host inputs are exactly the M3-01 Release fixtures `java-single-dex`, `kotlin-multidex`, and `jni-four-abi` after external ephemeral signing.
- The Android baseline and protected target for a fixture are produced from the same commit, build tools and release-equivalent instrumentation variant. The measurement hook records monotonic process-start, `Application.onCreate`, and first interactive-window timestamps without disabling signer, container, AEAD, ABI, policy or memory controls.

## Bounded execution

1. Host JMH performs three warmups and ten one-operation measurements per fixed fixture. `hostProcessMs` is the child CLI process CPU duration, including JVM startup and the complete product `protect` command; external signing is excluded. The two-minute wall-clock bound and raw wall samples are retained separately as diagnostics so hosted-runner scheduling noise stays visible without being misclassified as product processing time. Missing CPU accounting fails closed. Child peak RSS is polled independently on Windows and Ubuntu.
2. A separate 100 MiB synthetic valid APK case uses the same immutable-input assertion and the fixed 60-second/1-GiB budgets.
3. Android `observed_cold_start` performs five unreported cold warmups and thirty unmodified Release/R8 LOW samples for each fixed fixture on API 29 ARM64 and API 36 x86_64. The test-only APK is self-instrumenting (`package == targetPackage`) and declares visibility only for the three fixed synthetic fixture packages; the standalone benchmark main APK is retained for artifact-boundary inspection but is not installed. Each iteration force-stops the target, records both startup endpoints, polls peak PSS/native heap, samples stable PSS after five seconds, then records the shipped policy's post-start LOW/ALLOW observation.
4. Android `isolated_high_upgrade` runs thirty fresh instrumentation processes per fixture. Its Android-test-only bridge opens an authenticated owned session, proves zero pre-upgrade payload lookup, times one existing HIGH profile upgrade, records the Native 20-50 ms jitter and 250 ms wall bound, performs one post-upgrade lookup, and proves same-handle exactly-once cleanup. It is an incremental profile measurement, never a HIGH cold-start claim.
5. Raw samples are retained. P50 uses nearest-rank interpolation over sorted samples and P95 uses the same deterministic percentile implementation. Missing, non-finite, negative or wrong-count samples fail closed.
6. One benchmark workflow runs Host on Ubuntu/Windows and Android only on the two required reference profiles. Historical M0/M1/M2/M3 device, fuzz and equivalence matrices are not repeated.

## Evidence and cleanup

- `benchmark-results.json`, `benchmarks/environment.json`, the Markdown summary and a SHA-256 manifest contain no device serial, absolute user path, key material or plaintext DEX.
- APK size reconciliation is exact. `fourAbiRuntimeBaselineBytes` is reported separately and is never double-counted in the actual output.
- The report states that size budgets control added overhead and do not promise that protected output is smaller than input.
- Ephemeral signing material and installed benchmark packages are removed in unconditional cleanup. A failed budget or cleanup keeps M3-05 incomplete.
- The formal M3-07 report validator checks every Host and Android result. ZIP-aware artifact scanning requires the HIGH bridge in the androidTest APK and rejects it from Runtime Release AARs, the CLI JAR, benchmark production APK, and all baseline/protected fixture APKs.
