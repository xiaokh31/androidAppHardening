# M2-04 remote validation

## PR #46 initial head `f9bc93e54cf2ced1125e7d6fe0190e9d4050679f`

- Draft PR: `https://github.com/xiaokh31/androidAppHardening/pull/46`, base `main`, closes Issue #15.
- Build run `31511953664` and Governance run `31511953804` started normally.
- KVM run `31511953811` failed before JDK, Android tools, build or emulator startup. Both API jobs rejected newly deployed runner image `ubuntu24/20260810.271.1` because the fail-closed allowlist ended at `20260804.265.1`.
- GitHub's official `actions/runner-images` manifest at ref `ubuntu24/20260810.271` identifies image `20260810.271.1`, Ubuntu `24.04.4 LTS`, GNU C/C++ `13.3.0`, and file blob SHA `8a92fe558f0741f9c2e2ca77deae648bd30bfcd8`.
- The bounded correction adds only that exact image/ref pair to the existing Ubuntu allowlist in Build and KVM. Compiler checks remain fixed at GCC/G++ `13.3.0`; no Android, Runtime, device or acceptance behavior changes.

Replacement exact-head run IDs and artifacts will be appended only after the corrected head completes. Successful jobs will not be manually rerun.

## Replacement head `d72f45677984728e01fbeffa425c283c697470b1`

- Governance run `31512206204` passed. KVM run `31512206214` accepted the new image/GCC gate and proceeded into Android preparation.
- Build run `31512206267` failed identically on Ubuntu job `93848558347` and Windows job `93848558408` before compilation tasks executed: Gradle resolved `archiveM204NativeDebugSymbols` during task-graph construction and required an already-existing unstripped x86_64 ELF. The local dirty build had masked this clean-run defect.
- The fix adds a configuration-cache-compatible staging task with declared inputs and outputs. It depends on `stripReleaseDebugSymbols`, discovers and requires exactly one unstripped ELF per ABI only after Release native construction, stages the four files, and then feeds the reproducible ZIP task.
- Pinned offline regression `:runtime:native:clean :runtime:native:archiveM204NativeDebugSymbols` exited `0` in 36 seconds, built all four ABIs and stored the configuration cache. The resulting ZIP contains exactly one `libah_runtime.so` under each of `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`; it is 3603789 bytes with SHA-256 `ec4ee7d911e0476ec3fd5cdd2581543db501ee8f2c877288bedb44ef88ba9098`.

This correction changes debug-symbol discovery/staging only. Runtime sources, stripped AAR inputs, ABI policy, device fixtures and acceptance behavior are unchanged. A new exact-head CI round is required.

## Replacement head `46aad4a741c32d9e0410b814f007c7a99a987192`

- Build run `31512864693` passed Ubuntu job `93850732573` and Windows job `93850732314`, including clean full checks, exact four-ABI verification and evidence upload. Governance run `31512864733` passed.
- KVM run `31512864768` built both API fixtures successfully, then API 29 job `93850890009` and API 36 job `93850890033` failed at the same `compileDebugAndroidTestJavaWithJavac` boundary. `NativeConnectedRunner` extends platform `Instrumentation`, which has no `getArguments()` method; this source had not previously been compiled by the local fixture-only target.
- The bounded fix copies the supplied `Bundle` in `onCreate()` and reads the configured expected ABI from that private field in `onStart()`. No production source, JNI behavior or fixture contract changes.
- Pinned offline `-Pm204TargetAbi=x86_64 :runtime:native:compileDebugAndroidTestJavaWithJavac` exited `0` in 25 seconds with configuration cache stored.

A new exact-head KVM run must execute both API device matrices. The already proven Build/Governance semantics remain useful regression evidence but the final completion point still requires all checks on the same final head.

## Replacement head `731d527eed405913ef9ebd706876f5b8c11fdbdd`

- Build `31514011700` and Governance `31514011659` passed. KVM `31514011674` compiled both Release/R8 fixture sets, but API 36 job `93854526311` failed the Native connected smoke because its direct-packaged test APK had no extracted `libah_runtime.so` under `nativeLibraryDir`.
- The connected runner now follows the same bounded packaging rule as the production device runner: extracted packages read the ELF from `nativeLibraryDir`; direct packages read the first device-supported `lib/<abi>/libah_runtime.so` entry from the read-only APK ZIP. Pinned offline x86_64 androidTest compilation exited `0` in 21 seconds.

## Replacement head `2fbb36176675f2e7a78ab6b75569f78ec8279113`

- Build `31515149352` and Governance `31515149349` passed. API 29 KVM job `93858328057` passed the connected, policy, M2-01 and M2-02 matrices, then failed before the first M2-04 package publication because the ephemeral fixture keystore password was scoped to the preceding GitHub step.
- The correction writes the four synthetic signing variables to a mode-`0600` file below `RUNNER_TEMP`, sources it only for the M2-04 package window, unsets the variables immediately afterward and removes the whole temporary signing directory from the existing `EXIT/INT/TERM` cleanup. The file is not under the repository or any uploaded artifact path.

## Final implementation head `ed6b21b362dc5447e9605e04f634d81357dca34c`

- Draft PR [#46](https://github.com/xiaokh31/androidAppHardening/pull/46) remains the unique PR for Issue #15.
- Build `31516454041` passed Ubuntu job `93862687328` and Windows job `93862687429`. Governance `31516454109` passed Windows job `93862687968` and Ubuntu job `93862688162`.
- KVM `31516454190` passed API 29 job `93862769578` and API 36 job `93862769385`. API 29 covered both `x86_64` and `x86`; API 36 covered `x86_64`. Every ABI executed extracted/direct Release/R8 instrumentation, ten failure-injection windows, cross-DEX, JNI, authenticated metadata, one bounded cold start, memory collection, zero plaintext DEX files and cleanup.

| Platform / ABI | Report SHA-256 | Commands SHA-256 | Instrumentation SHA-256 |
|---|---|---|---|
| API 29 / x86 | `1c133ae2cf7f926d63623bd4a9df9235c2990f5988c2a689c6b4c1dba65ac3ed` | `0607d6a5b0f65c3179b7d4916e27fbad88c0e7039e26b22560e100868286b74a` | `68252834db3ac7aefc7fbb1d1e5911db9e4c813ed3684dd41043f2a2b80e844b` |
| API 29 / x86_64 | `82f5fc28d0e8bbba77e8a0e08cde617b0276d3504ca972738bc21dcaac2d2990` | `0e0e1356a1f08c793ff7d66678cb68663d31614d8b10806baff335dc5b5a286e` | `c0faabb7e4f972a5f3c565eb97bcdfb7a3fa091a420f420573acdb8f46adeba8` |
| API 36 / x86_64 | `e5db64782d3a86e115c5e7363cbb5bf0fdf2176c84dcf240b68576a2435232dd` | `ba68f0bbb40ffcff30237a28aa231f2fd6489d1f2cf89c5bfcfbf8fb29fcb494` | `c0faabb7e4f972a5f3c565eb97bcdfb7a3fa091a420f420573acdb8f46adeba8` |

The KVM artifacts are `9111742709` (API 29, ZIP digest `cc61de8778c185429145ecfb81feb28d0c4ce40b4235535459b43612d1218a3c`) and `9111766338` (API 36, ZIP digest `0dd389a374e7ac089bfe4f88ef855beaff136dea51e204c046542ab79c924646`).

Ubuntu artifact `9111354216` records a four-ABI report SHA-256 `3b03921d81981b532ae0a04130a0636005eca56cc8c25cd04505a5754a9f77f5`, AAR SHA-256 `8af14b39d86322dd2b037123c31f1998b80c137e5bf53c9e25e2e56f99cdd043` and debug-symbol ZIP SHA-256 `b3f9e2f5cf34f76ca1013ece358a04405bd4d58399ecd2be1c1631f06eeae1fd`. Windows artifact `9111551582` records report SHA-256 `f19421c56fe21c220760aeeaa0fffc3d2b2a6801064b419c50339204d2818bd6`, AAR SHA-256 `03ee3f8df1e4e0757e382009f50462bb44e78470b853dcc91658fd892a9016ff` and debug-symbol ZIP SHA-256 `9f650e279b55b53bc678ad28598250f453353df980bed30c73b976f18f13b3cb`. Both reports require exactly four hardened ABIs, the five-symbol JNI allowlist, one 104-byte alloc/read-only share section, RELRO/NOW, non-executable stack and passing mutation self-tests.

The ignored downloads are under `build/m2-04/remote/ed6b21b/`; no artifact or tool was downloaded to the C drive. Although a documentation-only successor changes no implementation semantics, GitHub evaluates the `pull_request` path filter against the complete PR diff and therefore starts replacement KVM jobs as well as Build and Governance. The final PR head must let that automatically triggered round complete; no manual rerun is required.

## Merger-ready head `80fee2559073278eb55f94de4a9ac2065777ba6b`

- Build `31518486332` passed Ubuntu and Windows; Governance `31518486285` passed Ubuntu and Windows; KVM `31518486273` passed API 29 and API 36.
- The successor is evidence/coordination-only relative to the independently reviewed implementation. Production Runtime, JNI, ABI policy, fixtures and device acceptance behavior are unchanged.
- PR [#46](https://github.com/xiaokh31/androidAppHardening/pull/46) was marked ready and merged with exact-head protection as merge commit `d5c74e7d3bfbcebff9c782134795f23ddd16c5e7`; Issue [#15](https://github.com/xiaokh31/androidAppHardening/issues/15) closed automatically.
