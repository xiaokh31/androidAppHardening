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

## Pending remote evidence

The local Visual Studio installation lacks the C/C++ compiler workload, so the Windows Host executable was not run locally. The checked-in CI builds and executes the exact C++ facade against NIST AES-256-GCM and RFC 5869 case 1 on Ubuntu and Windows before Gradle, then independently builds/scans all four Android ABIs. Those results must pass before the implementation commit is frozen for independent review.

At `2026-08-07T07:21:21+08:00`, draft PR #42 still had no check suites or workflow runs for its `opened`, later `synchronize`, or explicit `reopened` events. Read-only checks confirmed that repository Actions are enabled with all actions allowed, both Build and Governance workflows are active, the same-repository PR targets `main`, the public repository has no private-minute constraint, and the authenticated GitHub UI displays no approval, suspension, billing, or workflow-parse warning. This is an external scheduling blocker rather than a failed test. The PR remains draft; no frozen-review claim or M2-02 resume is permitted until actual Ubuntu/Windows runs pass.

GitHub began scheduling the delayed runs at `2026-08-07T07:22:13+08:00`. Governance run `31130748526` reached both platforms and failed only because the committed HandOff snapshot declared `working_tree: dirty` while CI correctly had a clean checkout. The follow-up candidate changes that declaration to clean; the earlier scheduler observation remains historical evidence, not an active outage claim.

Build run `31130748976` then proved the Ubuntu Host NIST/RFC vectors, dependency mutation self-test, full Gradle regression, and four-ABI scan all PASS. Its Windows job downloaded the correct 7,099,934-byte archive but 7-Zip 26.02 returned exit `1` after safely rejecting 147 relative symbolic links confined to disabled `tf-psa-crypto/drivers/pqcp/mldsa-native/examples`; no crypto test executed there. Local CMake 4.1.2 `-E tar xjf` validation extracted the same locked archive with exit `0`, skipped those links with warnings, and retained the required `tf-psa-crypto/CMakeLists.txt`, so Windows CI now uses that fixed extractor. KVM run `31130748457` independently exposed that its fixture build did not prepare the new fail-closed dependency; it now downloads, verifies, and extracts the same locked archive before Gradle. Replacement runs remain required.
