# M3-10 second-review remediation evidence

- Timestamp: `2026-08-21T12:48:05+08:00`
- Base evidence successor: `55c59fc559f1122df42ed457df629c7a79f063c0`
- Implementation freeze: `e28a563b0b24446f8dbee6da5e7cadb86e3d2c61`
- Environment: Windows 10 amd64; Temurin 17.0.19; Gradle 9.5.0; Node.js 24.12.0; project-local ignored Gradle/Android toolchains
- Dynamic scope: no workflow, API 36, KVM, emulator, ARM, benchmark or M3-05 execution

## Independent review 2 result

The independent read-only review of `19fd56d..55c59fc` returned `FAIL — P0=0/P1=6/P2=1`. The findings were: unbound Release artifact identity; double-P95 calibration; fail-open `pm path` cleanup; incomplete image/emulator/job/boot binding and missing pre-install preflight; weak profile-verification trust/sensitive scanning; incomplete DEX try/debug semantics and unpinned tools; and incomplete named mutations. The canonical API 36 eligibility remained unconsumed.

## Remediation

- `release-artifact-lock.json` binds six concrete Release/CLI/distribution artifacts by role, archive type, byte size, SHA-256 and required entries. It also binds Build Tools 36.1.0 `source.properties`, `dexdump`, `apksigner.jar`, `zipalign` and `d8.jar`.
- `api36-environment-lock.json` binds the official API 36 default x86_64 revision 2 image, Emulator 37.1.11 build 15917651, official archive hashes, package metadata hashes and exact build fingerprint.
- The runner completes M3-11 actual-byte validation, profile-lock/signature verification, two independent DEX comparisons, Release-surface validation and a freshly generated Kotlin profile-verification report before its first device mutation. It binds a normalized official current-job response and rechecks the same boot before/after cleanup and campaign boundaries.
- Cleanup first performs a successful `pm path` query, accepts only exact package paths, requires exact `Success` for a necessary uninstall, then requires a second successful empty `pm path` and no `m3-10-*` remote staging files. Any failure deletes the incomplete output.
- Each campaign now preserves the 15 raw calibration values from protected retained ordinal 1 unchanged; only the verifier computes nearest-rank P95 once.
- The DEX transformer explicitly relocates every debug line/local/source/prologue/epilogue item when probes are inserted. The independent verifier compares try start/end, handler type/target, parameter names, debug addresses/lines/local names/types/signatures and exact probe adjacency. A dedicated metadata self-test rejects try-handler target, debug address, debug line and debug-local mutations.
- Package verification requires the exact profile report schema and canonical/profile/lock/signer identities and sensitive-scans that report plus the derivation, environment and GitHub evidence.
- Named checks now cover result owner summaries, selected owner, all four eligibility thresholds, environment fingerprint/image/emulator/current-job, cleanup, GitHub evidence, real APK/archive surfaces, unpinned tools, and M3-05 dependency/budget text.

## Fixed identities

| Item | Size | SHA-256 |
|---|---:|---|
| canonical profile lock | 2812 | `a9e130bb4e66e14443d83ea01ef0d60a95adddefa9dc92a9bdc980e5728dab4b` |
| Release artifact/tool lock | 2641 | `45107b474fcdc1ec7356f31609bc79827b0cec0a01558e047a47b6d9ac5a55f9` |
| API 36 environment lock | 868 | `6e8fe036b3eadc7dad0fd1eed90178d96feae569ab8cbbac5f94717e21f34a1f` |
| preparation script | 11858 | `003f1a79586e76bc545023a9f32e7d1d3439c535c5a0d5019778149927e22028` |
| diagnostic runner | 19972 | `f8724ba83e37d3c3539626ba41bef990ff8cd51887337cc68dee7121365b6f52` |
| complete verifier | 72868 | `316b167200dd9b8363c4ba11a987e3c6b5b5fc22fd8268e47806ae032000db7f` |
| governance validator | 9863 | `85965eedfbd3e60f8bc7e8ecb54b90ddc9b38f930ac538d47f423c8a781ba690` |
| actual Kotlin report | 800 | `1610f895cb1a3003387a2c7f2e2e1474d6fbbfc523da8fc11c88d6cd283c5b93` |

The regenerated profile signer is represented only by prefix `1e21b13e836d` and commitment `e5dc8b0776b0569e54c1deb19d3a2e948d1bca6a780fff35ca0ee7c7fc5252c4`. Its private key, password and container seed were deleted before evidence creation.

## Commands and results

| Command | Result |
|---|---|
| `node tools/validation/prepare-m3-10-profile-package.mjs ...` using the fixed project-local tools | PASS; two deterministic derivations; secret root absent |
| `:host:container:m310VerifyProfiles :host:container:m310MetadataSelfTest --offline --no-daemon --no-configuration-cache` | PASS; actual four-APK semantics and 4 metadata mutations |
| `node tools/validation/verify-m3-10-startup-attribution.mjs profile-self-test ...` | PASS; 16 real byte/signer/surface/tool mutations |
| `node tools/validation/verify-m3-10-startup-attribution.mjs self-test` | PASS; 33 rejected report/result/cleanup/environment/GitHub mutations plus 4 threshold cases |
| `:host:container:test --offline --no-daemon --no-configuration-cache` | PASS; 13 cases |
| six fixed Release build tasks with the project-local dependency cache | PASS; all locked hashes reproduced after updating the freshly rebuilt fixture hash |
| `node tools/governance/verify-m3-10-profile-freeze.mjs --self-test --base-ref 9d3fc3a...` | PASS; 10 named governance mutations |
| global governance and `git diff --check` | PASS |

An initial offline Release command used the wrong project-local Gradle cache and failed before completion because its AAPT2 artifact was absent. Re-running once with the already pinned cache succeeded; no download occurred and the failed attempt produced no acceptance evidence.

## Gate

This is an implementation/evidence candidate only. The next action is a third independent read-only review of the exact implementation/evidence chain. No canonical workflow may be added and no API 36 eligibility may be consumed unless that review returns exactly `P0=0/P1=0/P2=0`.
