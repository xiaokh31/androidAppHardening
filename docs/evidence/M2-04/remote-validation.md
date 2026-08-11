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
