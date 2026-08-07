# M2-07 local validation

## Environment

- Timestamp: `2026-08-07T06:59:54+08:00` onward.
- OS: Windows 10 x64.
- JDK: Eclipse Temurin `17.0.19+10`.
- Android NDK: `29.0.14206865`.
- Clang: Android clang `21.0.0`, revision `5e96669f06077099aa41290cdb4c5e6fa0f59349`.
- CMake: `4.1.2`.
- All new dependency bytes are under ignored repository-root `.toolchains/native-crypto/`; existing fixed SDK/NDK/CMake binaries were read from `C:\Environment\Android\SDK` and no new package was downloaded to C:.

## Dependency and governance

| Command | Exit | Result |
| --- | ---: | --- |
| `node tools/validation/verify-m2-07-native-crypto.mjs --self-test` | 0 | 7,099,934-byte archive, SHA-256 `3359a349e23db3d5536fcee032ae7b2ecbfc08972fab643089b5cbf2a375c98c`, TF-PSA `1.1.1`, Apache-2.0 choice; hash/version/license mutations rejected |
| `node tools/governance/validate-project-package.mjs` | 0 | 28 task cards, 11 core docs, 9 ADRs |
| `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict` | 0 | strict HandOff valid |
| `git diff --check` | 0 | no whitespace errors |

## Android four-ABI build

Command (fixed JDK/Gradle home and existing fixed Android SDK):

```powershell
.\gradlew.bat --offline --no-daemon :runtime:native:assemble
```

Exit `0`, 64 tasks. A deliberate first run exposed that the hidden facade was garbage-collected before M2-02 had a JNI consumer; a hidden linker anchor was added and the final four artifacts all retain the facade without exporting it.

| ABI | ELF | Release stripped bytes | SHA-256 |
| --- | --- | ---: | --- |
| `armeabi-v7a` | ELF32 ARM | 142172 | `d91400347a996d882263a2ccd1aad9973f524fbe31381b707738ed4e17b08a74` |
| `arm64-v8a` | ELF64 AArch64 | 225016 | `8375e0248f5d772f6fad27bfec28bf6016167504f8cf738501c2f25831692120` |
| `x86` | ELF32 Intel 80386 | 218868 | `4d8076a5d15ce4c1db509ba8674c7d005f21b6c3705e391980545032e388d57b` |
| `x86_64` | ELF64 AMD x86-64 | 226352 | `ba4efe2086e536071b12d897bee709e2c9880338aafd2eef9332d47c7b5fe16e` |

`llvm-readelf` reports only `libm.so`, `libdl.so`, and `libc.so` as dynamic dependencies and `BIND_NOW` on all four artifacts. `llvm-nm -D --defined-only` finds no `psa_*` or `mbedtls_*` exports. Unstripped libraries contain local hidden `aes256GcmDecrypt`, `hkdfSha256`, `secureZero`, and `ah_crypto_backend_anchor` symbols for every ABI. The x86_64 local symbol scan finds no RSA, ECC/ECDSA, ChaCha, CCM, CBC, X.509, SSL or PKCS implementation surface; it does find the intended AES/GCM/SHA-256/PSA AEAD/key-derivation objects.

## Full local regression

Command:

```powershell
.\gradlew.bat --offline --no-daemon check verifyGovernance :runtime:native:assemble `
  -Paapt2Executable=C:\Environment\Android\SDK\build-tools\36.1.0\aapt2.exe `
  -Paapt2AndroidJar=C:\Environment\Android\SDK\platforms\android-36\android.jar
```

Exit `0` in 1m33s, 296 tasks. Existing M1-01 through M1-06 self-tests and full-flow remained green; Native Android lint/check and four ABI assemble passed.

## Remote CI evidence

The local Visual Studio installation lacks the C/C++ compiler workload, so the Windows Host executable was not run locally. The checked-in CI builds and executes the exact C++ facade against NIST AES-256-GCM and RFC 5869 case 1 on Ubuntu and Windows before Gradle, then independently builds/scans all four Android ABIs. Those results must pass before the implementation commit is frozen for independent review.

At `2026-08-07T07:21:21+08:00`, draft PR #42 still had no check suites or workflow runs for its `opened`, later `synchronize`, or explicit `reopened` events. Read-only checks confirmed that repository Actions are enabled with all actions allowed, both Build and Governance workflows are active, the same-repository PR targets `main`, the public repository has no private-minute constraint, and the authenticated GitHub UI displays no approval, suspension, billing, or workflow-parse warning. This is an external scheduling blocker rather than a failed test. The PR remains draft; no frozen-review claim or M2-02 resume is permitted until actual Ubuntu/Windows runs pass.

GitHub began scheduling the delayed runs at `2026-08-07T07:22:13+08:00`. Governance run `31130748526` reached both platforms and failed only because the committed HandOff snapshot declared `working_tree: dirty` while CI correctly had a clean checkout. The follow-up candidate changes that declaration to clean; the earlier scheduler observation remains historical evidence, not an active outage claim.

Build run `31130748976` then proved the Ubuntu Host NIST/RFC vectors, dependency mutation self-test, full Gradle regression, and four-ABI scan all PASS. Its Windows job downloaded the correct 7,099,934-byte archive but 7-Zip 26.02 returned exit `1` after safely rejecting 147 relative symbolic links confined to disabled `tf-psa-crypto/drivers/pqcp/mldsa-native/examples`; no crypto test executed there. Local CMake 4.1.2 `-E tar xjf` validation extracted the same locked archive with exit `0`, skipped those links with warnings, and retained the required `tf-psa-crypto/CMakeLists.txt`, so Windows CI now uses that fixed extractor. KVM run `31130748457` independently exposed that its fixture build did not prepare the new fail-closed dependency; it now downloads, verifies, and extracts the same locked archive before Gradle. Replacement runs remain required.

On replacement HEAD `c7499b5a8045c02ff7095b78d79b0811761be68b`, Governance run `31131750917` passed on Ubuntu and Windows, the Ubuntu job in Build run `31131751261` passed all M2-07 and root gates again, and both KVM jobs in `31131755538` passed the new dependency preparation step. The Windows Build also passed archive preparation but CMake reported that it could not discover a Visual Studio instance. GitHub's official Windows 2025 runner image manifest lists Visual Studio Enterprise 2022 at `C:/Program Files/Microsoft Visual Studio/2022/Enterprise`; the next candidate passes that immutable preinstalled path through `CMAKE_GENERATOR_INSTANCE` while retaining CMake 4.1.2 and the `windows-2025` runner.

Run `31132095119` then showed that its actual immutable image was `windows-2025-vs2026` version `20260728.188.1`, not the current generic Windows 2025 manifest. The run-provided official manifest pins Visual Studio Enterprise 2026 `18.8.12023.21` at `C:/Program Files/Microsoft Visual Studio/18/Enterprise`. CMake 4.1.2 supports Visual Studio generators only through VS 2022, so it cannot consume a VS 2026 generator instance. Windows Host CI now activates that image's x64 compiler environment with `VsDevCmd.bat` and uses the already fixed CMake 4.1.2/Ninja generator; no compiler, SDK, CMake, or runner downgrade is downloaded.

Run `31132353130` confirmed that this activation found MSVC `19.51.36252.0` and configured CMake/Ninja successfully. Compilation then failed in upstream `extras/pk_ecc.c` and `extras/pk_rsa.c`: with every PK key type intentionally disabled, MSVC rejects an internal zero-sized array with `C2229`, even though those object files are not selected into the final minimal facade. The same locked source/configuration passes on Ubuntu and all Android Clang targets, and the immutable runner manifest includes LLVM `20.1.8`. The next Windows candidate retains the VS 2026 SDK/link environment but explicitly selects preinstalled `C:/Program Files/LLVM/bin/clang-cl.exe`; it does not enable PK/RSA/ECC or weaken the product feature profile.

Candidate `10a1862dbce4c1b6defbfa16ebd4bb49e8335e58` closed every replacement gate on `2026-08-07`:

| Workflow | Run | Result | Relevant proof |
| --- | ---: | --- | --- |
| Build | [`31132692644`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31132692644) | PASS | Ubuntu 24.04 and Windows 2025 both verified the locked archive, ran NIST AES-256-GCM and RFC 5869 case 1, ran the root regression, and built/scanned all four Android ABIs. Windows used the immutable runner's preinstalled LLVM `20.1.8` `clang-cl` with its VS 2026 SDK/link environment. |
| Governance | [`31132692665`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31132692665) | PASS | Ubuntu 24.04 and Windows 2025 passed project-package validation, strict PR HandOff validation, negative HandOff tests, and Git object verification. |
| M0-05 Linux KVM | [`31132692597`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31132692597) | PASS | API 29 and API 36 x86_64 both verified the same Native source before two-pass Release/R8 fixture builds, completed bounded device acceptance, cleanup diagnostics, and evidence upload. |

The GitHub annotation that `actions/upload-artifact` is being forced from Node.js 20 to Node.js 24 is a non-failing action-runtime deprecation warning; it does not change dependency bytes or test results. With all three exact-head workflows green, this evidence commit may freeze the input for independent read-only security review. PR #42 remains draft and M2-02 remains paused until that review has zero P0/P1/P2 findings and replacement exact-head CI passes.
