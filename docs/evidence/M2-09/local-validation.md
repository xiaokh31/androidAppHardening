# M2-09 local validation

- Remediation freeze: `dd78179f41c97aab7e3f38c0f571c4e6198f8939`
- Initial production freeze: `9ba6ec28c7d1450c3ca51175f78e3aa2d292331f` (review 1 rejected for one JVM-test P2; production finding count was zero)
- Contract parent: `89f0194d`
- Base: `a65433ae0bda651fc1088d187913b2dbfa7b02d1`
- Environment: Windows x64, Eclipse Temurin `17.0.19+10`, Gradle `9.5.0`, Node.js `24.12.0`
- Recorded at: `2026-08-15T11:23:45+08:00`
- Result: local bounded gates `PASS`; device/KVM and independent review remain pending

## Scope and behavior

The production change adds one package-private synchronized coordinator lookup. It returns only the already committed immutable `READY` result. A later Shell wrapper may cache that result only when the Framework loader is the same object as the frozen final loader. The path cannot invoke the Guard opener, construct another original Factory, call its ClassLoader hook, expose `NEW`/`INSTALLING`/`FAILED`, or change public interfaces.

The M201 device runner now performs one bounded real `Activity.recreate()` and requires the relaunched Activity to use the same in-memory final loader. With a custom original Factory the activity callback count changes exactly from `1` to `2`; the process ClassLoader hook remains exactly `1`. The existing extracted/direct and no-Factory variants consume the new `configuration_relaunch=true` marker.

## Commands

| Command | Exit | Result |
|---|---:|---|
| `node tools/validation/verify-m2-01-bootstrap.mjs` | 0 | Guard call count remains one; READY attachment, final-loader identity, six public callbacks, hidden-API and dependency boundaries pass. |
| repository-local offline Gradle `:runtime:bootstrap:test :runtime:bootstrap:lint :runtime:bootstrap:assembleDebugAndroidTest` | 0 | `158` tasks; `M2-09 bootstrap self-test PASS (10 groups)`; Java, Android test APK and lint pass in `46s`. |
| repository-local offline Gradle M201 extracted/direct Release plus both debug androidTest APKs | 0 | `328` tasks; both Release/R8 targets and relaunch runner compile/package in `37s`. |
| repository-local offline Gradle `:runtime:bootstrap:assembleRelease` | 0 | Release AAR produced in `26s`. |
| repository-local offline Gradle `:runtime:bootstrap:bootstrapSelfTest` after review remediation | 0 | Second Shell READY/mismatch/NEW/INSTALLING/FAILED matrix passed in `21s`; Guard open and Factory construct/hook counts remain exact. |
| `node tools/governance/validate-project-package.mjs` | 0 | `31` task cards, `11` core docs and `13` ADRs pass. |
| strict HandOff validator and `git diff --check` | 0 | Handoff schema and whitespace pass. |

Two non-evidence preflights stopped before compilation: the first wrapper call used the default user cache and the sandbox denied its network attempt; the next call used the repository cache but PowerShell split an optional property argument into a nonexistent task. No tool was installed or downloaded. All successful commands above used ignored repository-root `.toolchains/jdk`, `.toolchains/gradle-user-home` and `.toolchains/android-m0-04`; no large program was downloaded to C:.

## Local artifacts

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `runtime/bootstrap/build/outputs/aar/bootstrap-release.aar` | 20631 | `9c71b6519bec3095ad3217c39394a6a7ec8ebaffdb77b8b67838f7fbfa1a9490` |
| M201 extracted Release APK | 582648 | `79832c44c286e6599a9cd0c75ab0cd18754ee4d98e7b8978def1b27f9296d496` |
| M201 direct Release APK | 1270044 | `7549ba707c481b2ff1e1d3ed0fb8608daca7327b88d5fb78345d2e9387439d7e` |
| M201 extracted debug androidTest APK | 104086 | `6fa54bf0b86d1c1427cd2e22dad78349d86662c55be380a060a41631eca5f018` |
| M201 direct debug androidTest APK | 104086 | `ac2afc4acf990480be155da65e5257cafbff968a87a43f76cb28d1fd9020d72a` |

All artifacts are ignored build outputs. They contain only repository-generated synthetic fixtures and no customer APK, production signing material or plaintext DEX evidence.

## Pending gates

- Incremental independent read-only review of test-only remediation freeze `dd78179f41c97aab7e3f38c0f571c4e6198f8939`.
- One unique draft PR for Issue #59.
- Exact-head Ubuntu/Windows Build/Governance and one bounded API 29/36 KVM run proving the production configuration relaunch.
- Expected-head merge, post-merge gates, then resumption of M3-04 PR #58.
