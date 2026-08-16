# M3-05 local pre-freeze validation

- Timestamp: `2026-08-15T23:38:42+08:00`
- Branch: `chore/m3-05-performance-benchmarks`
- Base commit: `930b759c99f330218dc4404368e9844e80456c82`
- Environment: Windows x64; Eclipse Temurin `17.0.19+10`; Gradle `9.5.0`; Node.js `24.12.0`
- Device status: no emulator or physical device was started; the Android matrix is intentionally deferred to one exact-head batch.

## Passing commands

All commands exited `0`.

1. `node --check tools/validation/run-m3-05-android-benchmark.mjs`
2. `node --check tools/validation/verify-m3-05-test-bridge-artifacts.mjs`
3. `node tools/governance/verify-m3-07-high-benchmark-contract.mjs --self-test`
4. `node tools/governance/verify-m3-07-high-benchmark-contract.mjs`
5. `gradlew --offline --no-daemon --no-configuration-cache :benchmarks:android:compileDebugAndroidTestKotlin :benchmarks:host:compileJmhJava`
6. `gradlew --offline --no-daemon --no-configuration-cache :fixtures:android:compileM301JavaSingleDexReleaseJavaWithJavac :benchmarks:android:assembleDebug :benchmarks:android:assembleDebugAndroidTest :runtime:policy:assembleRelease :host:cli:jar`
7. `node tools/validation/verify-m3-05-test-bridge-artifacts.mjs <androidTest-apk> <benchmark-apk> <native-release-aar> <policy-release-aar> <cli-jar>`
8. `gradlew --offline --no-daemon --no-configuration-cache :benchmarks:host:check :benchmarks:android:lintDebug`
9. `node tools/governance/validate-project-package.mjs`
10. `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict`
11. `git diff --check`

The formal contract mutation suite rejected all ten production-surface mutations and all twenty malformed report mutations. The artifact scan required `M305HighProfileBridge` in the androidTest APK and proved it absent from both Runtime Release AARs, the CLI JAR and the benchmark production APK. Host statistics, Android-test/Host compilation, one Release fixture compilation, packaging and lint all passed.

## Local artifact hashes

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Android benchmark androidTest APK | 47849207 | `36f2bb328194e548945bc7d33086d0339dbb06b8f14805f1974b41e6e3483b2a` |
| Android benchmark production APK | 2741225 | `723a7983357353e9fc7cff4248364a33776c8d25e52a4cdb43bce2c9837ab6d7` |
| Native Runtime Release AAR | 458037 | `a7b973c7d258c10268d5797d4b2de703673d1a05cf13c362fd92ff9def226108` |
| Policy Runtime Release AAR | 36474 | `a7fdea442c5419ac1c7081dba59261d1fe297de6d51ec40fccb4603a6ea3cad5` |
| Host CLI JAR | 80264 | `213612a9f08ac6c06921e253364708a013f24541e27dd161981989dde8615aa9` |

These are local compile/artifact-boundary evidence only. Performance budgets, raw 10/30-sample reports, repeatability, package cleanup and the reference-device claims remain pending until the frozen exact-head batch.

## ADR 0015 single-pair resume validation

- Timestamp: `2026-08-16T14:22:15+08:00`
- Implementation commit: `e41f9d2d5071525f92dc23a34cc25d1d89b06e47`
- Integrated main base: `7f10a0b84d9680e4b9311e680d0508e7fde512cd`
- Scope: benchmark/test orchestration only; no production Runtime, Host processing, fixture behavior, budget, threshold, warmup count, sample count or security-control change.
- Device status: no local emulator, KVM job or physical device was started. API 29 is excluded at matrix scheduling time for the M3-05 branch push, and ARM remains blocked until the sole API 36 A/B aggregate passes.

The resume checks all exited `0`:

1. `node --check tools/validation/run-m3-05-android-benchmark.mjs`
2. `node --check tools/validation/create-m3-05-ab-evidence.mjs`
3. `node tools/validation/run-m3-05-android-benchmark.mjs --self-test`
4. `node tools/validation/create-m3-05-ab-evidence.mjs --self-test`
5. `node tools/governance/verify-m3-07-high-benchmark-contract.mjs --self-test`
6. `node tools/governance/verify-m3-08-startup-stability-contract.mjs --self-test`
7. `node tools/governance/validate-project-package.mjs`
8. `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict`
9. `git diff --check`
10. `gradlew --offline --no-daemon --no-configuration-cache --console=plain -PbenchmarkCampaign=B -PbenchmarkOutput=reports/performance/campaign-b -PbenchmarkReuseTargets=true -PbenchmarkDeferBudgetFailure=true :benchmarks:android:tasks`

The A/B generator self-test built campaign A/B reports with the fixed opposite fixture and mode orders, copied six canonical APK identities, recomputed all 90 rows from 30 raw samples, and passed the formal M3-08 validator. Its negative check retained a campaign budget failure in the aggregate. The workflow schedules exactly one API 36 job for the branch push, executes A then B in the same emulator process, proves the six prepared target APK bytes unchanged between campaigns, and fails the formal gate if either campaign budget, any repeatability row, cleanup, identity or artifact binding fails. Pull-request merge-ref KVM is skipped for this branch, so the one push cannot create a second pair.

## Bounded corrections before the passing snapshot

- The first wrapper invocation inherited the machine's JDK 8; the passing commands explicitly used the repository-pinned JDK 17 and repository-local ignored Gradle user home.
- Packaging initially rejected the new runtime test dependency because its runtime classpath was absent from the lock state. `--write-locks` resolved only `debugAndroidTestRuntimeClasspath`; the subsequent fully offline package passed.
- Lint first identified three test-harness-only application warnings. The fixed API 36 target and non-user-facing, non-backed-up instrumentation harness are now documented as narrow lint suppressions; the replacement lint run passed.
- The first physical-device attempt stopped before measurement because the preparation task tried to install a target before the owned benchmark runner. MIUI rejected that redundant installation and the subsequent `pm path` checks proved all five target/test packages absent. Preparation is now explicitly Host-only; the benchmark runner is the sole install/uninstall owner.
- Exact-head Ubuntu Host completed both fixed passes, while two Windows attempts independently exceeded the unchanged 10% repeatability gate on wall-clock `hostProcessMs` because hosted-runner scheduling delay was included. The bounded repair defines `hostProcessMs` as child CPU processing duration and retains raw wall duration separately; no budget or repeatability threshold was relaxed.
- API 36 KVM reached the Android harness but AndroidX Macrobenchmark rejected the required emulator and the intentionally non-profileable Release reference fixture before sampling. The replacement passes the test-only suppression list `EMULATOR,NOT-PROFILEABLE` only; `DEBUGGABLE` and all product safety checks remain unsuppressed, and the limitation is written into the report.
- The first suppression-enabled API 36 run proved installation success but Macrobenchmark could not resolve the installed fixed target through Android package visibility. The bounded manifest correction enumerates only the three synthetic M3-01 fixture packages in both benchmark APK manifests; it does not request `QUERY_ALL_PACKAGES` and does not change product manifests.
- The visibility-enabled run reached the next fail-closed Macrobenchmark precondition and rejected AGP's default `.test -> main` instrumentation wiring as `NOT-SELF-INSTRUMENTING`. The application-plugin benchmark module now fixes `testApplicationId` to its own package, installs only that self-contained test APK, and invokes its self-targeting instrumentation component. The merged manifest must prove package equals targetPackage; this condition is not suppressed. AndroidX adds `QUERY_ALL_PACKAGES` only to the test APK, while the benchmark main APK and all product/fixture manifests remain unchanged by that dependency permission.
