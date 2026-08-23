# M3-14 workflow-absent local validation

- Task: M3-14 / Issue #82
- Branch: `feat/m3-14-successor-startup-diagnostic`
- Base: `960eb9f406eb1a7b7c9b324598fb59936aa1c5b5`
- Initial implementation commit: `2367e8fdd8ce4eae753647475fdf3f2fe9abdc86`
- Review-1 remediation implementation: `2751581cebaed5a0b91681d7b638dbe4aa9e10f1`
- Timestamp: `2026-08-23T08:49:03+08:00`
- Environment: Windows 10 amd64; Git `2.52.0.windows.1`; Node.js `24.12.0`; repository-local Temurin JDK `17.0.19+10`; repository-local Gradle `9.5.0`

## Boundary

Both canonical workflow paths were absent throughout validation:

- `.github/workflows/m3-13-startup-attribution.yml`
- `.github/workflows/m3-13-startup-attribution-evidence.yml`

No API 36 run, emulator, Android device, ARM, API 29, M3-05 benchmark, profile regeneration or signing operation was executed. The exact M3-12 retained profile package and M3-11 canonical product bytes were read-only inputs.

## Results

| Command | Exit | Result |
| --- | ---: | --- |
| repository-local Gradle `:host:container:testClasses :host:container:m310MetadataSelfTest --offline --no-daemon` with `JAVA_HOME` set to Temurin 17 | 0 | Kotlin test sources compile; 10 metadata/probe-adjacency mutations pass |
| repository-local Gradle `:host:container:m310VerifyProfiles --offline --no-daemon` against exact retained M3-12 APKs and M3-11 originals | 0 | four-APK byte/signer/manifest/container/share/probe verification PASS |
| `node tools/governance/verify-m3-14-profile-freeze.mjs --self-test --base-ref 960eb9f406eb1a7b7c9b324598fb59936aa1c5b5` | 0 | 14 mutations PASS, including terminal deletion filtering; workflows absent; production observer absent |
| `node tools/validation/verify-m3-14-startup-attribution.mjs self-test` | 0 | canonical model PASS; 59 rejected mutations, including 10 execution-ledger mutations |
| `node tools/validation/run-m3-14-startup-attribution.mjs --cleanup-self-test` | 0 | 8 cleanup failure injections PASS |
| `node tools/validation/collect-m3-14-github-evidence.mjs --self-test` | 0 | 5 redirect cases plus 1 archive-bound case PASS |
| Node syntax checks for the freeze, runner, verifier and collector | 0 | PASS |
| `node tools/governance/validate-project-package.mjs` | 0 | 39 task cards, 11 core docs, 18 ADRs |
| strict HandOff validation with the documented pending-clean allowance | 0 | PASS before freeze commit |
| `git diff --check` | 0 | PASS |

The Kotlin compiler daemon could not create its default per-user cache marker; Gradle automatically used its supported non-daemon compiler fallback and completed successfully. No large program or Android package was downloaded to the system drive. The only missing pinned test dependency had already been restored to the ignored repository-local `.toolchains/gradle-user-home` before this final validation.

## Hashes

- Local profile verification report: `build/m3-14/local-freeze/profile-verification.json`, SHA-256 `1610f895cb1a3003387a2c7f2e2e1474d6fbbfc523da8fc11c88d6cd283c5b93`
- Canonical baseline APK: `4607d3289e1fc3bd95282ab47791ec810a5d2d3ac0a69fc0f91388901e412dcf`
- Canonical protected APK: `1eb159d7f0149a943fb2e1c4d8467f283d1cfbbfad670628402cfb0cd23390d9`
- Retained profile baseline APK: `a062e0994482b1db417ff710c554364ec80e9f8d5fa84b5745ff5753308b764b`
- Retained profile protected APK: `1ce941404d8e6105764d041c449a60016312bc9c9671a8f8eb97c4e8b6820a10`
- Retained profile archive: `21816d2a843bb5c59902224c7bf786d546d52b4a5b2d1168ca0c449a2ca27964`
- Profile verifier: `622c1ef1047aeeac4fb34c275747c181fb68dee6c5edf01daa3cf86b98df0468`
- Read-only retained-profile support: `dc4e5459612d7a0fe0b6fff3a5d966d8e87d08aab862aade29cc2eb1b99a2bc9`
- Workflow-absent freeze validator: `5394091a49d9a77d6a693fb82c543aeb2af1a3195b0b02051d39c8dbacd544bb`
- Attribution verifier: `eb490d82c3c4d84e66c6bc48a4133740c9db8cfcd182073ef45724f68cb5c43c`
- Runner: `194a2908f75b753416c6b3160e98485b8246b42dcb3a8702f16783fdc0390449`
- Terminal collector: `7b329fa2d4d64c2723cd4a4f9a3a5f129a70e993da4bd5b7e6f14a97a4f39605`
- Toolchain lock: `264d210c530bf4a4618a3d241c9b2c4600ac193053e9c782810bfdd743b78c68`
- Qualification evidence: `ca8fa557e2ac6d5a4ec54f18850caeef31a20f299bc6ecd40f5229911d9378bd`

This document does not claim that API 36 executed or that publication is authorized. The exact frozen Git object must first receive an independent read-only `P0=0/P1=0/P2=0` review.
