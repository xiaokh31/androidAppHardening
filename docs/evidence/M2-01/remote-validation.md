# M2-01 remote validation

- Exact-head implementation parent: `6a5a2706dcbb1b2984fb2bc6edf4147e81f98773`
- Timestamp: `2026-08-11T03:01:29Z`
- Validation mode: `pre-cli`

## Ubuntu and Windows Build

GitHub Actions Build run [`31453271122`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31453271122) completed successfully on the exact implementation parent.

- Ubuntu 24.04 job `93661765099`: root checks, lint, M2-01 JVM state-machine/hostile-failure matrix, architecture gates, Release/R8 fixture assembly and existing runtime regressions passed.
- Windows 2025 job `93661765097`: the same repository checks and cross-platform regressions passed.

Governance run [`31453271096`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31453271096) completed successfully on the same commit on Ubuntu (`93661765089`) and Windows (`93661765082`).

## API 29 and API 36 Linux/KVM

GitHub Actions KVM run [`31453271138`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31453271138) completed successfully on the same implementation parent. Both jobs used repository-pinned packages, bounded commands, a job timeout and unconditional package/emulator cleanup. No local emulator was started.

Each platform passed three authenticated production-Shell paths: extracted Release/R8 with a custom original Factory, direct Release/R8 with a custom original Factory, and extracted Release/R8 with no original Factory. The no-Factory path verified zero Factory callbacks plus platform-default Application, Provider, Activity and Service creation. All paths also verified the final ClassLoader, cross-DEX, JNI, main/secondary process startup, metadata-null behavior, standard `INSTRUMENTATION_CODE: -1`, no plaintext DEX and package cleanup.

| Platform | Job | Report SHA-256 | Commands SHA-256 | Original-Factory instrumentation SHA-256 | No-Factory instrumentation SHA-256 | Artifact ID / digest |
|---|---|---|---|---|---|---|
| API 29 x86_64 | `93661765408` | `65af5c6965d4a8c4d8904c20925c39f52ac767f94294d446839f19ebaed8897b` | `bbf855ce80a65ca1786847a060d9e53569bd996dad6c0c067db38f2cfd5f0e0f` | `7cbc74cd8f71d497b3250926d378c90e33457453a2de8f7197dd16e2fcde9247` | `62f77173c920fe7886144ece2979b3a70bdea33a94744a6d8574711d51f73b1d` | `9087340545` / `24cb4a72ef3b268341cd332cad9c5bf6755a64cf1ae44981bc161cdffaf6460b` |
| API 36 x86_64 | `93661765385` | `5260439ef162190ad3b93df98e84522a6cd216907150c6a71984ab233599c573` | `d4d5c72afc3f26346c1e4457e6db7f9135a601adb9faeaba37f5681b50e2a8e8` | `7cbc74cd8f71d497b3250926d378c90e33457453a2de8f7197dd16e2fcde9247` | `62f77173c920fe7886144ece2979b3a70bdea33a94744a6d8574711d51f73b1d` | `9087389740` / `0b8e88d674199aecd1ec2294a92e1fcecff39ff32c167e8c52aa67558a8d5ecc` |

Both reports are `PASS` with `cleanup_passed: true`. The no-Factory transcript contains the exact stable result `original_factory=false factory_callbacks=0` and the platform success result `INSTRUMENTATION_CODE: -1`.

## Publication and evidence handling

PR [#45](https://github.com/xiaokh31/androidAppHardening/pull/45) was marked ready only after exact-head CI and the final independent review passed. It was merged with expected-head protection as merge commit `8dc20e65ed87c029cf14add3d3f5769719e13862`; Issue [#12](https://github.com/xiaokh31/androidAppHardening/issues/12) is closed.

Downloaded evidence copies live only under ignored `build/m2-01/ci-6a5a270-api29` and `build/m2-01/ci-6a5a270-api36`. Command transcripts redact the device serial, contain no customer APK or private signing material, and prove package absence after cleanup.
