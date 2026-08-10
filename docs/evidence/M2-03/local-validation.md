# M2-03 local validation

- Implementation parent: `8211a60dca604ac1aab56b4839bcd96d5494aa05`
- Base commit: `dec1ef68f69eea26ae1bc6a1132bf26bf39ba0f8`
- Branch: `feat/m2-03-runtime-integrity`
- Environment: Windows 10 x86_64, project-local Temurin JDK `17.0.19+10`, Gradle `9.5.0`, Android SDK/NDK fixed by the repository
- Validation mode: `pre-cli`
- Timestamp: `2026-08-11T02:47:00+08:00`

## Passing local gates

1. Project-local `gradle --no-daemon --offline verifyGovernance verifyM203RuntimeIntegrity :runtime:policy:test :runtime:policy:lint` — exit `0`; 72 tasks, 57 non-empty policy cases, 14 architecture/security checks, lint and Governance passed in 49 seconds.
2. `node tools/governance/validate-project-package.mjs` — exit `0`; 28 task cards, 11 core docs and 9 ADRs accepted.
3. `git diff --check` — exit `0` at the implementation parent.
4. Project-local `gradle --no-daemon --offline :runtime:policy:assembleRelease` — exit `0`; 36 tasks passed in 25 seconds. `runtime/policy/build/outputs/aar/policy-release.aar` is `22052` bytes with SHA-256 `1279240a67dbcb2e6a0aef8cb82519cbf8efbde6e723483566be4723bfb05aff`.
5. `node --check tools/validation/run-m2-02-device-acceptance.mjs` and `node tools/validation/verify-m2-03-runtime-integrity.mjs` — exit `0` at `8211a60`; all 14 M2-03 architecture/security checks passed after the API 29 cold-start orchestration fix.

The production source scan SHA-256 is `d63b6cc4a3a22634ad90e1fc6721236706bdd4244bd790be080cc40ec73d1d11`. It confirms pinned `apksig 9.3.0`, a single production `PayloadRuntime` caller, no product signing/private-key capability, no startup `Context`/`PackageManager` dependency, the frozen Guard ordering, rollback ownership and primary/suppressed cleanup semantics.

## API 29 arm64 physical-device acceptance

The authorized Xiaomi `sirius`/MI 8 SE ran Android API 29 with `arm64-v8a`, `user/release-keys`, `ro.secure=1`, `ro.debuggable=0`, a 64-bit process and non-root ADB shell UID 2000. Only ordinary user-authorized USB installation was used; no secure setting, root path or prompt bypass was used.

Both extracted/direct Release/R8 variants passed non-empty instrumentation, 12 exception/OOM ownership windows, 12 metadata/cross-handle/cross-session rejection cases, cross-DEX, JNI, authenticated metadata, zero plaintext DEX files, exactly 20 cold starts, memory collection and final package cleanup.

| Variant | Target APK SHA-256 | Test APK SHA-256 | Cold-start p50/p95 | Peak PSS |
|---|---|---|---|---|
| extracted | `73acee2cc875998a250836ce88f0af61cfde5bcf6a2cf5f73bb7b6f0e02107f9` | `419f277267f13332b00cc25d8f82386061e0d4054c05c8bee62901d0e553f40e` | `328/411 ms` | `66403 KiB` |
| direct | `463d04edf6858cfa95d6bcf25ee1bfb7bc111c8495e160b4cf95c6ecc978e949` | `404b5adc34a5134e6c96ecc70cfb15334e38dd769b00cb89a1f9477e5fc59a86` | `316/330 ms` | `69443 KiB` |

- Report: ignored `build/m2-03/final-device-api29-arm64-pass/report.json`; SHA-256 `cf418b7d2cc2803b394d7be4a234f69e96b5c3eb8011bc8f29ebfc2d08234446`.
- Sanitized command transcript: ignored `build/m2-03/final-device-api29-arm64-pass/commands.json`; SHA-256 `9a955a563d6b28d09b6197cff59aab7f10cc312123dd02c607afe91679997025`.
- Extracted/direct instrumentation summaries: `253` bytes each; SHA-256 `7451ff9c7531cb32d0ba0f89ef8d84d56b56b3d8053ee93d2b16e15ed60f263d` each.
- Result: `PASS`; cleanup passed and no further physical-device interaction is required.

The physical report was generated at parent `659c2b8614f0f30b76d22d8269803925a06924a5` and is inherited with an explicit boundary. The diff to `8211a60dca604ac1aab56b4839bcd96d5494aa05` changes only the fixture Activity's non-sensitive run-token log, host signer/static validation and the host-side M2-03 cold-start orchestration. Production `runtime/**`, Native libraries, target/test APKs, `M203DeviceRunner` and the arm64 ownership/metadata/JNI/DEX behavior are unchanged. The inherited report proves physical arm64 behavior but does not prove the run-token negative matrix or API 29 KVM orchestration stabilization; those changes are accepted only from exact-head API 29/36 KVM evidence.

All installation certificates, APKs, reports and raw test outputs remain under ignored build directories. No private key, password, full certificate, APK or device path is tracked by Git.
