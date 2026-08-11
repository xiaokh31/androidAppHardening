# M2-01 local validation

- Final implementation parent: `6a5a2706dcbb1b2984fb2bc6edf4147e81f98773`
- Base commit: `f4b773fc59129ea69c2dabde553438d8e62c549e`
- Merge commit: `8dc20e65ed87c029cf14add3d3f5769719e13862`
- Branch: `feat/m2-01-shell-app-component-factory` (merged to `main`)
- Environment: Windows 10 x86_64, project-local Temurin JDK `17.0.19+10`, Gradle `9.5.0`, Android platform tools `35.0.2-12147458`, repository-pinned Android SDK/NDK and Native crypto source
- Validation mode: `pre-cli`
- Timestamp: `2026-08-11T11:08:01+08:00`

## Passing local gates

1. Final targeted M2-01/root regression matrix — exit `0`; 406 actionable tasks passed in 32 seconds after the no-original-Factory and independent-review fixes.
2. Final hostile-Throwable classification regression — exit `0`; 33 actionable tasks passed in 26 seconds. `HardeningBootstrap` no longer invokes an untrusted `Throwable` method while classifying failures.
3. Post-merge `:runtime:bootstrap:assembleRelease :fixtures:android:assembleM201ExtractedRelease :fixtures:android:assembleM201DirectRelease` — exit `0`; 173 actionable tasks, 26 executed, 2 from cache and 145 up-to-date, completed in 36 seconds.
4. `node tools/validation/verify-m2-01-bootstrap.mjs` — exit `0`; production source has one Guard entry, no `ah.runtime.loader` import or hidden API, no production test-only public API, stable total failure classification, and an authenticated no-original-Factory KVM path.
5. Release AAR `javap -public` surface inspection — exit `0`; `ShellAppComponentFactory` exposes only its constructor and the six platform callback overrides.
6. `node tools/governance/validate-project-package.mjs`, strict HandOff validation and `git diff --check` passed at the implementation freeze; they are rerun without exemption for the post-merge coordination commit.

The first frozen review at `c2b083032e585a6e95e5a5d0661724dc1a7b63bb` found two P1 issues and one P2 issue: no authenticated production-Shell device path without an original Factory; non-total hostile `Throwable.getMessage()` classification; and three public test-only Shell methods. Commit `62976b859ad4a8e605083d50d3bd75e70613b5ad` added the no-Factory path, removed the public diagnostics, and hardened the ordinary throwing case. Commit `6a5a2706dcbb1b2984fb2bc6edf4147e81f98773` removed all untrusted Throwable method calls, closing the remaining blocking/stalling case. The final independent review returned `P0=0`, `P1=0`, `P2=0`.

## Post-merge local artifacts

| Artifact | Size | SHA-256 |
|---|---:|---|
| `runtime/bootstrap/build/outputs/aar/bootstrap-release.aar` | 20,527 bytes | `4a09503c6b64ba441cfb9b75d6390462b405170e5955a6a21319a3d3d3420211` |
| `fixtures/android/build/outputs/apk/m201Extracted/release/android-m201Extracted-release.apk` | 562,240 bytes | `b45f0b7bdbfa62cc086aa9d99a72fc606b1b7291bdf2665cc86977ee69a76c22` |
| `fixtures/android/build/outputs/apk/m201Direct/release/android-m201Direct-release.apk` | 1,253,660 bytes | `dcfbe130e2c07530b59a90db2c690fa5902d43e02d934eada5de57375cead9df` |
| `fixtures/android/build/outputs/apk/androidTest/m201Extracted/debug/android-m201Extracted-debug-androidTest.apk` | 111,562 bytes | `6d41e494b662dc2809f5f7756c3996b7e451031e1366563881a686f3b5e82696` |
| `fixtures/android/build/outputs/apk/androidTest/m201Direct/debug/android-m201Direct-debug-androidTest.apk` | 111,562 bytes | `512f2688aec6e25402286469564b560055c162b0af66d34f4b60a24db159bc77` |

The APKs and test outputs are synthetic fixtures and remain in ignored build directories. No private key, password, customer APK, plaintext DEX, complete certificate or device path is tracked by Git.
