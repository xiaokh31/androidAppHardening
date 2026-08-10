# M2-03 remote validation

- Exact-head implementation parent: `0ed9240617527a321a4baaf38a4d7e15f5d2eb33`
- Timestamp: `2026-08-10T18:04:15Z`
- Validation mode: `pre-cli`

## Ubuntu and Windows Build

GitHub Actions Build run [`31415786223`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31415786223) completed successfully on the exact implementation parent.

- Ubuntu 24.04 job `93544350382`: clean checks, M2-02 sanitizer/fuzz/failure injection, dependency-verification negative, M2-03 policy matrix and four Native ABIs passed.
- Windows 2025 job `93544350324`: full checks, byte-consistency regressions, M2-03 policy matrix and four Native ABIs passed.

Governance run [`31415786181`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31415786181) completed successfully on the same commit on Ubuntu (`93544350167`) and Windows (`93544350282`).

## API 29 and API 36 Linux/KVM

GitHub Actions KVM run [`31415786339`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31415786339) completed successfully on the same implementation parent. Both jobs used project-local pinned Android packages, a 45-minute job timeout, bounded commands and unconditional package/emulator cleanup. No local emulator was started.

Each platform passed:

- extracted/direct Release/R8 instrumentation;
- 12 exception/OOM Guard ownership windows and 12 metadata/cross-handle/cross-session rejection cases per variant;
- same/different/unsigned/multiple-current/valid-rotation/historical-only signer fixtures;
- seven real startup rejection cases with a unique per-run 16-hex token, exactly one matching marker, `lookup_count=0` and `session_published=false`;
- cross-DEX, JNI, authenticated metadata, exactly 20 cold starts per variant, memory, zero plaintext DEX files and cleanup.

| Platform | Job | Report SHA-256 | Commands SHA-256 | Signer report SHA-256 | Signer commands SHA-256 | Artifact ID / digest |
|---|---|---|---|---|---|---|
| API 29 x86_64 | `93544350615` | `765f15c79d1a7c8b9d303f9871c1fe1691212c2d63545498d41d496d0bad8659` | `83a9e9056cba30fef2fd8b1a04d12a1694a702149597f175de6f22ae53dc54a0` | `4b5284b2438fc2a7f07c13e0494360d59d4b8bb580a4752c6f9e988ea2af7873` | `cb4caa8f7afb27c3cad16bfc5917908fd659527d69d6e0218c5ed7cbc1ac48ae` | `9073752802` / `6218c77446216d29a980a28c245f43dfa14af6dde7bb4d4aac9f67aee31bc38f` |
| API 36 x86_64 | `93544350706` | `0bf34c210a8340d3e204df59a4206b7f004718bfd4c45e9029bb0ed000234959` | `cfb8a8dacc744620badfa202e85d3222e279883a1760a8bfffb9fee1829226b7` | `a4bca0f49f12d09187759a4f648c17b65b4b34e69753b3edcb96a882dd4a6553` | `788f572999a2965fd22aca4819e9e3cdf9e020087ac47d39138fd3fdf4adbf8b` | `9073865744` / `5b7edbdd8018c35bb78bc68759d38d1caf725c47396c035df594e4b3288cc410` |

API 29 extracted/direct cold-start p50/p95 values were `931/1057 ms` and `953/1142 ms`; peak PSS was `34272/34147 KiB`. API 36 values were `1367/1804 ms` and `1451/1946 ms`; peak PSS was `18367/18903 KiB`.

The extracted/direct instrumentation summaries on both KVM platforms are `247` bytes each and have identical SHA-256 `9f56d43e4794b86259f5f274c147dcd2fd9941a8cea8bcefee7ee0871ebe9623`.

## Run-token false-positive closure

The superseded parent `659c2b8614f0f30b76d22d8269803925a06924a5` exposed that API 29 could retain tagged logcat lines after `logcat -c`. Commit `0ed9240617527a321a4baaf38a4d7e15f5d2eb33` binds each startup attempt to a random non-sensitive 16-hex token and requires exactly one exact marker for that token. In both downloaded exact-head artifacts, all seven `signer-matrix/*.logcat.txt` files contain exactly one line and seven distinct tokens; historical-only and repeated CONTAINER cases cannot match prior scenarios.

Downloaded evidence copies live only under ignored `build/ci-artifacts/m2-03-0ed9240-api29` and `build/ci-artifacts/m2-03-0ed9240-api36`. The archived transcript redacts full signer digests and device paths, omits raw logcat output, and verifies remote-file/package cleanup before reporting `PASS`.
