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
| `node tools/validation/verify-m2-07-native-crypto.mjs --self-test` | 0 | 7,099,934-byte archive, SHA-256 `3359a349e23db3d5536fcee032ae7b2ecbfc08972fab643089b5cbf2a375c98c`; 3,927-file/60,515,866-byte extracted tree SHA-256 `7c4ba655...d2140`; 147 Unix symlinks confined to disabled ML-DSA examples; exact tag/commit/API/checksum/license/algorithm/ABI identity; one-byte and every locked-field mutation rejected |
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
| `armeabi-v7a` | ELF32 ARM | 147476 | `20f3c54a10a4ef4d2dd8ee0fea82c74111236e3c4333ba41c9084212dcfce8df` |
| `arm64-v8a` | ELF64 AArch64 | 236304 | `5a49c4e91b87b50bbe880aec818168227608a366e99817a14ba3835dd29c8871` |
| `x86` | ELF32 Intel 80386 | 229656 | `7a1f5c4954cf7f1d2e02beaec87bc9277637a4dabdcd55484ae6c9a31f7f5310` |
| `x86_64` | ELF64 AMD x86-64 | 236760 | `6b3f33dc77fc2c890a111dd3be2dce043be7e8e1d7f828130ecf58091c8b3d0c` |

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

Run `31132353130` confirmed that this activation found MSVC `19.51.36252` and configured CMake/Ninja successfully. Compilation then failed in upstream `extras/pk_ecc.c` and `extras/pk_rsa.c`: with every PK key type intentionally disabled, MSVC rejects an internal zero-sized array with `C2229`, even though those object files are not selected into the final minimal facade. The same locked source/configuration passes on Ubuntu and all Android Clang targets, and the immutable runner manifest includes LLVM `20.1.8`. The next Windows candidate retains the VS 2026 SDK/link environment but explicitly selects preinstalled `C:/Program Files/LLVM/bin/clang-cl.exe`; it does not enable PK/RSA/ECC or weaken the product feature profile.

Candidate `10a1862dbce4c1b6defbfa16ebd4bb49e8335e58` closed every replacement gate on `2026-08-07`:

| Workflow | Run | Result | Relevant proof |
| --- | ---: | --- | --- |
| Build | [`31132692644`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31132692644) | PASS | Ubuntu 24.04 and Windows 2025 both verified the locked archive, ran NIST AES-256-GCM and RFC 5869 case 1, ran the root regression, and built/scanned all four Android ABIs. Windows used the immutable runner's preinstalled LLVM `20.1.8` `clang-cl` with its VS 2026 SDK/link environment. |
| Governance | [`31132692665`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31132692665) | PASS | Ubuntu 24.04 and Windows 2025 passed project-package validation, strict PR HandOff validation, negative HandOff tests, and Git object verification. |
| M0-05 Linux KVM | [`31132692597`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31132692597) | PASS | API 29 and API 36 x86_64 both verified the same Native source before two-pass Release/R8 fixture builds, completed bounded device acceptance, cleanup diagnostics, and evidence upload. |

That successful Windows Build run self-reported `ImageOS=win25-vs2026` and runtime `ImageVersion=20260803.193.1`; its immutable official manifest is published under ref `win25-vs2026/20260803.193` and retains LLVM `20.1.8`, Visual Studio Enterprise 2026 `18.8.12023.21`, x64 tools `18.8.11901.359` and Windows SDK `10.0.26100.0`. The remediation workflow now fails closed unless the exact runtime image, `clang-cl 20.1.8` and activated `cl.exe` runtime `19.51.36252` all match.

The GitHub annotation that `actions/upload-artifact` is being forced from Node.js 20 to Node.js 24 is a non-failing action-runtime deprecation warning; it does not change dependency bytes or test results. With all three exact-head workflows green, this evidence commit may freeze the input for independent read-only security review. PR #42 remains draft and M2-02 remains paused until that review has zero P0/P1/P2 findings and replacement exact-head CI passes.

## Rejected freeze and remediation

Independent read-only review of `f428e4ac8cc12223ad6c6d2dabdf83c55f0f987a` returned **FAIL** at `2026-08-07T08:15:45+08:00`: `P0=0`, `P1=3`, `P2=4`. The immutable findings are archived in `read-only-review-1.md`; that SHA can never be merger-ready.

The remediation candidate verifies archive bytes before any parser, validates member paths and the complete regular-file tree in a new temporary directory, writes a locked archive/tree stamp, and atomically promotes only the verified tree. The machine verifier now exactly locks every field plus offline GitHub asset/checksum evidence. The facade serializes each complete PSA transaction and the Host test adds all required AES/HKDF boundaries, null/length semantics and an eight-thread stress matrix. CI now requires Android Release ELF, scans stripped and unstripped outputs, asserts LLVM `20.1.8`, and explicitly records CVE-2026-25832 as affecting 4.1.1 but unreachable while TLS is excluded.

Local Windows `:runtime:native:assembleRelease` completed `34` tasks in `48s`; the four Release ELF rows above are the replacement outputs and passed `BIND_NOW`, dynamic dependency/export, required facade and forbidden local-symbol checks. A local Windows Host compiler is still unavailable, so the expanded Host executable must be compiled and run by the next Ubuntu/Windows CI before a new freeze.

The exact Windows preparation route was also replayed in ignored repository storage: fixed CMake extracted the already authenticated archive to `.toolchains/native-crypto/validation-extract`, the pre-promotion tree matched all three tree invariants, UTF-8/LF stamp creation succeeded, post-stamp verification passed, and the temporary directory was removed. The replacement offline root command `check verifyGovernance :runtime:native:assembleRelease` then passed `283` tasks in `1m31s` with exit `0`.

Replacement Build run [`31135168838`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31135168838) on remediation commit `14b8c8c8cddb6f4b29f5457975b31054f3c582b7` passed the complete Ubuntu Host boundary/thread matrix, root regression and Release four-ABI build/scan. Its Windows job first passed the authenticated archive-before-parser and full-tree promotion gates, then failed closed before compiling because the workflow asserted manifest ref value `20260803.193` while the runner environment correctly reported runtime `ImageVersion=20260803.193.1`. This is an exact immutable-image assertion correction only; no version range, latest alias, crypto feature or acceptance gate is relaxed. Replacement exact-head Build, Governance and KVM runs remain required before freezing a second review input.

The next replacement Build run [`31135503969`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31135503969) proved the exact runtime image assertion and source promotion gates, then failed closed before CMake because `cl.exe` self-reported `19.51.36252` while the workflow expected the same toolset version with a non-reported `.0` suffix. Ubuntu again passed the expanded Host vectors and all root/Release ABI gates. The next candidate asserts the exact `cl.exe` runtime string `19.51.36252`; it does not accept a range or change the pinned image, LLVM, VS component, SDK, CMake, crypto source or feature set.

## Remediation review-freeze input

Implementation candidate `e471a74d02a1426ccd17542ea8bb9f4ee956f6bf` closed every pre-review remote gate on `2026-08-07`:

| Workflow | Run | Result | Relevant proof |
| --- | ---: | --- | --- |
| Build | [`31135865293`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31135865293) | PASS | Ubuntu and Windows authenticated the archive before parsing, verified the full source tree, built and ran the expanded AES-256-GCM/RFC 5869 boundary and eight-thread Host matrix, passed the root regression, then built and scanned stripped/unstripped Release outputs for all four Android ABIs. Windows also matched exact runtime image `20260803.193.1`, LLVM `20.1.8` and `cl.exe` `19.51.36252`. |
| Governance | [`31135865277`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31135865277) | PASS | Ubuntu and Windows passed project-package validation, strict PR HandOff validation, negative HandOff tests and Git object verification. |
| M0-05 Linux KVM | [`31135865495`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31135865495) | PASS | API 29 and API 36 x86_64 both verified the locked Native source, completed two-pass signed Release/R8 fixture builds, bounded device acceptance, cleanup diagnostics and evidence upload. |

The evidence-only successor commit freezes this candidate and these immutable run IDs as the sole input to the second full independent read-only M2-07 review. PR #42 remains draft and M2-02 remains paused; any review finding invalidates that freeze and requires remediation, a new SHA and replacement exact-head CI.

Independent review 2 rejected frozen SHA `699cbda469a85501294b7a83587ce89faaad7192` with `P0=0/P1=0/P2=3`. All seven review-1 findings were independently closed, but Unix could accept a zero-symlink extraction, Ubuntu runner/GNU Host compiler identity was not asserted, and README still claimed M2 had not started. The full immutable conclusion is archived in `read-only-review-2.md`. Remediation is limited to platform-exact symlink validation, locked Ubuntu image/compiler assertions and truthful M2 status; it requires a new SHA, replacement exact-head CI and a third complete independent review.

On `2026-08-07T09:24:10+08:00`, the second-review remediation passed the Native dependency self-test with the locked 7,099,934-byte archive, 3,927-file/60,515,866-byte tree, source hash, licenses, stamp and all lock mutations. The platform matrix independently rejects zero Unix links and partial Windows links while accepting the complete 147-link tree and the reviewed Windows zero-link extraction. Governance validation passed `28` task cards, `11` core documents and `9` ADRs. The clean Windows offline command `check verifyGovernance :runtime:native:assembleRelease` then completed `283` tasks with exit `0` in `1m24s`; all root regressions and the four NDK 29 Release ABIs passed. Ubuntu exact image/compiler assertions and the Unix real-extraction negative remain mandatory in replacement CI before a new freeze.

Remediation candidate `b80acc824708a5725831dcecf580e3633d312583` closed every replacement gate on `2026-08-07`:

| Workflow | Run | Result | Relevant proof |
| --- | ---: | --- | --- |
| Build | [`31137953160`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31137953160) | PASS | Ubuntu and Windows passed the exact runner/toolchain assertions, authenticated preparation, Host crypto matrix, root regression and four-ABI Release scans. Ubuntu's real tar extraction contained exactly 147 accepted-prefix links. |
| Governance | [`31137953050`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31137953050) | PASS | Ubuntu and Windows passed project-package, strict HandOff, negative HandOff and Git object checks with the corrected README state. |
| M0-05 Linux KVM | [`31137952989`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31137952989) | PASS | API 29 and API 36 each passed the Ubuntu image/GNU assertion, verified 147-link source preparation, two-pass Release/R8 fixture builds, bounded device acceptance, cleanup diagnostics and evidence upload. |

The evidence-only successor freezes this candidate and the three run IDs for the third complete independent read-only review. Any finding invalidates the new freeze; PR #42 remains draft and M2-02 remains paused.

The first exact-head run of evidence freeze `7ac703e56e31869adf839242252368632c3cdd5d` failed Windows Build `31138599592` before compiler invocation because GitHub assigned the previously reviewed runtime `20260728.188.1` instead of `20260803.193.1`; Ubuntu completed every Build gate, and Governance `31138599573` passed. This demonstrates that the hosted `windows-2025` pool is concurrently heterogeneous, so a single runtime assertion is nondeterministic even without repository changes. The replacement uses an exact two-entry runtime-to-immutable-manifest mapping for the only two independently observed/reviewed images, retains identical LLVM/VS/x64-tools/`cl.exe` assertions, and rejects any third image or version range. The failed freeze is invalid and replacement exact-head CI is required.

Reviewed-pool candidate `4526729b678fe3238edd01f49a59d093b507b328` then passed all replacement gates:

| Workflow | Run | Result | Relevant proof |
| --- | ---: | --- | --- |
| Build | [`31138903978`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31138903978) | PASS | Ubuntu and Windows selected reviewed immutable image identities, verified compiler/runtime assertions and the locked source, ran the complete Host matrix/root regression, and scanned all four Release ABIs. |
| Governance | [`31138903915`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31138903915) | PASS | Both platforms passed project-package, strict HandOff, negative HandOff and Git object gates. |
| M0-05 Linux KVM | [`31138903927`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31138903927) | PASS | API 29 and API 36 passed the pinned Ubuntu/GNU gate, 147-link source identity, two-pass Release/R8 build, bounded acceptance, cleanup diagnostics and evidence upload. |

The evidence-only successor is the sole third-review freeze. It must independently repeat these three workflows on its exact HEAD before review; PR #42 remains draft and M2-02 remains paused.

Independent review 3 rejected exact frozen SHA `0662b9e2ce22f5728bb7a757ada6b6bac8a94536` with `P0=0/P1=0/P2=1`, archived in `read-only-review-3.md`. It independently closed all ten earlier findings and confirmed exact-SHA Build `31139693696`, Governance `31139693779` and KVM `31139693700`. The sole new finding is test-only: production already rejects a symlink outside the reviewed prefix, but self-test did not inject a wrong-prefix path into otherwise full 147-entry Linux and Windows inventories. Remediation adds those two direct surface negatives without changing accepted source, algorithms, ABI, toolchain or Runtime behavior; a new SHA and full gates/review remain mandatory.

Wrong-prefix remediation commit `ef8d26a6043e3d511ae8a59ff0f85378e54e98b1` passed Governance run [`31140905977`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31140905977). Build run [`31140905972`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31140905972) failed closed before Ubuntu setup or tests because GitHub assigned new runtime `ImageVersion=20260804.265.1` while the lock accepted only `20260720.247.2`; the Windows job passed completely. The runner advertised immutable manifest ref `ubuntu24/20260804.265`, whose official inventory still provides GNU C/C++ `13.3.0`. Remediation adds only the exact new runtime/ref as a second reviewed entry, preserving the old exact entry during the hosted-pool rollout and rejecting ranges, `latest`, order changes and any third value. This SHA is not reviewable evidence; replacement exact-head gates remain mandatory.

Replacement candidate `cd3b5ce8bfe50715e10b6d0967dbecb8c88bd1cf` passed every required exact-head workflow:

| Workflow | Run | Result | Scope |
| --- | --- | --- | --- |
| Build | [`31141373708`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31141373708) | PASS | Ubuntu and Windows selected reviewed immutable image identities, asserted compiler/runtime versions, authenticated the locked archive and complete source surface, ran NIST/RFC/boundary/thread Host tests and the root regression, and scanned all four Android Release ABIs. |
| Governance | [`31141373717`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31141373717) | PASS | Ubuntu and Windows passed project-package, strict PR HandOff, negative HandOff and Git object gates. |
| M0-05 Linux KVM | [`31141373706`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31141373706) | PASS | API 29 and API 36 each passed the reviewed Ubuntu image/GNU gate, authenticated 147-link source, two-pass Release/R8 fixture builds, bounded device acceptance, cleanup diagnostics and evidence upload. |

The evidence-only successor freezes this candidate and these run IDs as the sole input to the fourth complete independent read-only M2-07 review. PR #42 remains draft and M2-02 remains paused; any finding invalidates the freeze and requires a new remediation SHA plus replacement exact-head gates.

Independent review 4 rejected frozen SHA `a764d102492f4c6074d928a240ae1c62abc2d320` with `P0=0/P1=0/P2=2`, archived in `read-only-review-4.md`. All eleven earlier findings were independently closed. The new findings are limited to two truth/gate gaps: Windows did not consume the locked Visual Studio `18.8.12023.21` and x64 tools component `18.8.11901.359` values at runtime, and HandOff still described the already-pushed evidence successor as a future action. Remediation reads the exact installed Enterprise instance and selected x64-tools package, compares both against the authenticated machine lock, runs mismatch negatives, prints evidence, and reconciles HandOff. No dependency, algorithm, ABI or Runtime facade behavior changes; a new SHA, full exact-head gates and fifth complete independent review remain mandatory.

Review-4 remediation candidate `024d24eaf8bd82c07ebe67dfb7750762cb3c8400` passed every required exact-head workflow:

| Workflow | Run | Result | Scope |
| --- | --- | --- | --- |
| Build | [`31143103481`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31143103481) | PASS | Ubuntu/Windows completed the full source, Host, root and four-ABI gates. Windows selected manifest `win25-vs2026/20260803.193`, then read and printed exact installed Visual Studio `18.8.12023.21` and selected x64 tools component `18.8.11901.359` before the Host tests. |
| Governance | [`31143103486`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31143103486) | PASS | Ubuntu and Windows passed project-package, strict PR HandOff, negative HandOff and Git object gates. |
| M0-05 Linux KVM | [`31143103462`](https://github.com/xiaokh31/androidAppHardening/actions/runs/31143103462) | PASS | API 29 and API 36 passed reviewed Ubuntu/GNU, authenticated source, two-pass Release/R8, bounded device acceptance, diagnostics, cleanup and evidence upload. |

The commit containing this record is the sole review-5 freeze only if its own exact-head Build, Governance and KVM workflows all succeed. The independent reviewer must resolve and verify those run IDs directly from that SHA; PR #42 remains draft and M2-02 remains paused.

Independent review 5 rejected frozen SHA `a6497c2713e9025db11c4b5ccc657d42beb9236e` with `P0=0/P1=0/P2=2`, archived in `read-only-review-5.md`. It independently closed all thirteen earlier findings. Direct inspection of all four unstripped Release ELFs found twelve required TF-PSA internal local symbols: one AES block primitive, four CTR-DRBG symbols, four entropy symbols and three PSA internal-random lifecycle symbols. A bounded attempt to remove `MBEDTLS_CTR_DRBG_C` and `MBEDTLS_PSA_BUILTIN_GET_ENTROPY` failed at compile time with `No DRBG module available for the psa_crypto module`, confirming they are required by PSA initialization. Remediation therefore makes the actual boundary enforceable: the config bytes and exact twelve-symbol set are machine-locked, each ABI must match that set with no missing or extra member, none may be dynamically exported, and the facade still exposes no PSA ECB/CBC, padding or random-generation operation. The CVE closure now relies on the absence of RSA/CBC/padding APIs and call paths and on authenticated GCM-only decryption, not on an inaccurate claim that the internal AES block symbol is absent. The second P2 is closed by reconciling the stale M1-06 and M2-07 workstream rows. At `2026-08-07T11:50:51+08:00`, the lock self-test, governance validator and `git diff --check` exited `0`; a local `llvm-nm` comparison passed all four ABIs with `exact=12` and `dynamic=0`; and the fixed repository JDK/Gradle cache completed offline `check verifyGovernance :runtime:native:assembleRelease` with exit `0` in 2m13s (`283` tasks). No device or emulator was started. A new SHA, replacement exact-head Build/Governance/KVM and sixth complete independent review remain mandatory.

Independent review 6 rejected frozen SHA `9c29fb63c8e54cb6d0670e6e01bdbfc88e113cc3` with `P0=0/P1=0/P2=2`, archived in `read-only-review-6.md`. Its exact-head Build `31145662390`, Governance `31145662398` and KVM `31145662402` runs were all green, and fourteen of fifteen earlier findings were closed. The remaining review-5 boundary finding was only partially fixed: every ABI actually has seventeen related local symbols because DRBG/entropy also retains three unprefixed helpers and two platform entropy-hook symbols, including one local-data (`d`) object. The replacement lock therefore records the complete set as sixteen local-text (`t`) entries plus one local-data entry, and both platform gates compare exact type-plus-name values over a filter that captures all seventeen. The second new P2 is closed by updating HandOff only after the failed review and completed CI are recorded. A new SHA, replacement exact-head gates and seventh full independent review remain mandatory.

At `2026-08-07T12:17:52+08:00`, the replacement lock self-test rejected type, name, order and config mutations; governance and `git diff --check` exited `0`; local NDK 29 `llvm-nm` comparison reported `exact=17`, sixteen `t`, one `d` and `dynamic=0` for each of `armeabi-v7a`, `arm64-v8a`, `x86` and `x86_64`; and the fixed repository JDK/Gradle cache completed offline `check verifyGovernance :runtime:native:assembleRelease` in 1m40s with exit `0` (`283` tasks). No device or emulator was started.

Review-6 remediation candidate `b7c120b7774bf788de750f35d0fbe946af6c2307` passed exact-head Build `31147033042`, Governance `31147033069` and API 29/36 KVM `31147033086`. Both Build jobs passed the revised four-ABI step that compares every related symbol's exact local `t/d` type plus name against the seventeen-entry machine lock. The evidence-only successor must not repeat stale future-tense steps: it is reviewable only if the reviewer independently resolves three successful workflows on that successor's own SHA; no further commit may be created merely to copy those run IDs.
