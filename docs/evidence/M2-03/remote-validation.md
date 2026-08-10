# M2-03 remote validation

- Exact-head implementation parent: `8211a60dca604ac1aab56b4839bcd96d5494aa05`
- Timestamp: `2026-08-10T18:45:04Z`
- Validation mode: `pre-cli`

## Ubuntu and Windows Build

GitHub Actions Build run [`31419276164`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31419276164) completed successfully on the exact implementation parent.

- Ubuntu 24.04 job `93555830810`: clean checks, M2-02 sanitizer/fuzz/failure injection, dependency-verification negative, M2-03 policy matrix and four Native ABIs passed.
- Windows 2025 job `93555830952`: full checks, byte-consistency regressions, M2-03 policy matrix and four Native ABIs passed.

Governance run [`31419276874`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31419276874) completed successfully on the same commit on Ubuntu (`93555832970`) and Windows (`93555833035`).

## API 29 and API 36 Linux/KVM

GitHub Actions PR KVM run [`31419279082`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31419279082) completed successfully on the same implementation parent. Both jobs used project-local pinned Android packages, a 45-minute job timeout, bounded commands and unconditional package/emulator cleanup. No local emulator was started.

Each platform passed:

- extracted/direct Release/R8 instrumentation;
- 12 exception/OOM Guard ownership windows and 12 metadata/cross-handle/cross-session rejection cases per variant;
- same/different/unsigned/multiple-current/valid-rotation/historical-only signer fixtures;
- seven real startup rejection cases with a unique per-run 16-hex token, exactly one matching marker, `lookup_count=0` and `session_published=false`;
- cross-DEX, JNI, authenticated metadata, exactly 20 cold starts per variant, memory, zero plaintext DEX files and cleanup.

| Platform | Job | Report SHA-256 | Commands SHA-256 | Signer report SHA-256 | Signer commands SHA-256 | Artifact ID / digest |
|---|---|---|---|---|---|---|
| API 29 x86_64 | `93555839095` | `5d620f56de5cdd4b6b1aa674e5e53afb3f8df22e36d750066d32a66a28dd7428` | `93e4f0fcb3bbafed801dc7664894e38b388273c3d6e6e9f34a3cd95fd7bae802` | `b4ecb1586446c3d863e0b92df6c0f57b2cfe505f6e404382d09f0f65cca4ab54` | `415f10ac6e48331ea96395c3852264af21cc67d14e9dafe24813d5f84af845bc` | `9075019028` / `fe0ac06176604c9988e33dc836c090258b4654fdf42e0d7f6809f8e3fcdbb62f` |
| API 36 x86_64 | `93555839055` | `22ad1b3dc984458eb8b4a5643ff9c0940bbde8de3e67086cb39b224f104e4311` | `f67a35b78a0824c945809e8491a56ba892a07a1bf8d4bc33c982f1a2325f423b` | `139612d0d4d30295a1944cfcb6ad0fdfd960b10a3e455059c3360f9dc62fe34c` | `35895c1dad16c9b594a001ac7a0c368c47e6059191580ec798d07ac2e1461d25` | `9075172980` / `bd9a6b0eaf45afe10ea51d4ead2790b5304944720fe4243602c0fd7a52bbd3ff` |

API 29 extracted/direct cold-start p50/p95 values were `725/860 ms` and `703/743 ms`; peak PSS was `34568/36655 KiB`. API 36 values were `1243/1558 ms` and `1362/1629 ms`; peak PSS was `18599/19117 KiB`.

The extracted/direct instrumentation summaries on both KVM platforms are `247` bytes each and have identical SHA-256 `9f56d43e4794b86259f5f274c147dcd2fd9941a8cea8bcefee7ee0871ebe9623`.

## Run-token false-positive closure

The superseded parent `659c2b8614f0f30b76d22d8269803925a06924a5` exposed that API 29 could retain tagged logcat lines after `logcat -c`. Commit `0ed9240617527a321a4baaf38a4d7e15f5d2eb33` binds each startup attempt to a random non-sensitive 16-hex token and requires exactly one exact marker for that token. In both downloaded exact-head artifacts, all seven `signer-matrix/*.logcat.txt` files contain exactly one line and seven distinct tokens; historical-only and repeated CONTAINER cases cannot match prior scenarios.

## API 29 cold-start orchestration closure

The first evidence-only child `d2b6d8eb187ec5f33e8b60d4f5635ab749990672` exposed an API 29 orchestration race in KVM run [`31417694822`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31417694822), job `93550510843`: after 32 successful M2-03 cold starts, `am start -W` reported the Launcher, `pidof` briefly returned a PID and `dumpsys meminfo` then reported no process. The old shared runner failed closed but did not retain cold-start logcat for the PSS failure. Commit `8211a60dca604ac1aab56b4839bcd96d5494aa05` adds an M2-03-only 150 ms post-`force-stop` stabilization window, requires the exact target Activity/PID/PSS and writes logcat before any such failure. It performs no retry or sample substitution; both variants still require exactly 20 successful samples. Independent incremental review returned `P0=0/P1=0/P2=0`. Two independent exact-head API 29 KVM jobs (`93555839095` and `93555811567`) passed the full matrix after the fix.

Downloaded evidence copies live only under ignored `build/ci-artifacts/m2-03-8211a60-api29` and `build/ci-artifacts/m2-03-8211a60-api36`. The archived transcript redacts full signer digests and device paths, omits raw signer-matrix logcat output, and verifies remote-file/package cleanup before reporting `PASS`.
