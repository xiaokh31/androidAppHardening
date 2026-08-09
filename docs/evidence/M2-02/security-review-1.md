# M2-02 independent read-only security review 1

- Frozen SHA: `99e83445ac8d4f16e6232684d81807d1fb910f56`
- Implementation parent: `b09d582ff14c7a0fa6fb48d937c3bd9bfc4a26f5`
- Merge base: `e78fcaed58dd5211a465ea37a94db45dddc17dfa`
- Result: **FAIL**
- Findings: `P0=0`, `P1=3`, `P2=2`
- Reviewer mode: independent and read-only; no edit, stage, commit, push, download, emulator or APK installation.

The frozen SHA is invalidated. README completion, HandOff completion, PR readiness and merge remain prohibited until a new frozen commit passes a full independent review with `P0=0`, `P1=0`, `P2=0`.

## Verified evidence

- Build run `31298945845` and KVM run `31298945842` both target implementation parent `b09d582ff14c7a0fa6fb48d937c3bd9bfc4a26f5`; Ubuntu, Windows, API 29 x86_64 and API 36 x86_64 jobs passed.
- The ignored API 29 arm64 extracted/direct reports, transcripts and instrumentation hashes match `local-validation.md`; each variant recorded 20 cold starts, 20 memory samples, instrumentation success, zero plaintext DEX files and cleanup.
- The four ABI libraries depend only on the expected system libraries, expose only the five fixed JNI exports and contain one read-only 104-byte `.ah_share_v1` section.
- No APK, DEX, SO, AAR, keystore, private key, customer path or production secret is tracked.

## P1 findings

### P1-1: zlib internal plaintext history is not cleared

`authenticated_payload.cpp` uses zlib's default allocator and only calls `inflateEnd`. The internal window/state can contain recovered DEX history, but there is no custom zeroizing `zfree` or positive hook proving that inflater scratch is cleared before the Native handle returns. This violates the frozen short-plaintext-lifetime requirement.

Evidence at the rejected freeze: `runtime/native/src/main/cpp/authenticated_payload.cpp:281-285,428-433`; task contract `docs/tasks/M2-02-native-decrypt-and-inmemory-loader.md:36`.

### P1-2: Native cleanup failure is discarded at JNI/Java boundaries

The Native transaction returns `cleanup_failed`, but JNI ignores it and throws only the primary status. Handle close status is also discarded; the Java close path cannot surface a stable cleanup failure or attach it as suppressed without replacing the primary error. This violates the cleanup aggregation and error-precedence contract.

Evidence at the rejected freeze: `runtime/native/src/main/cpp/authenticated_payload.cpp:464-495`, `runtime/native/src/main/cpp/jni_bridge.cpp:304-317,363-368`, `runtime/native/src/main/java/ah/runtime/loader/NativePayloadBridge.java:23`, `PayloadMemoryHandle.java:13-20`, `LoadedPayload.java:35-50`; task contract `docs/tasks/M2-02-native-decrypt-and-inmemory-loader.md:36,39,48,51`.

### P1-3: mandatory deterministic acceptance matrices are incomplete

Neither local nor CI commands invoke the task card's connected checks. Deterministic tests cover only a subset of required header/ciphertext/ZIP/zlib/metadata/cross-handle/JNI ownership mutations; random fuzz cannot replace stable error classification. Therefore the rejected evidence claim that all mandatory gates were closed was false.

Evidence at the rejected freeze: `docs/evidence/M2-02/local-validation.md:50,69`, `runtime/native/src/main/cpp/m2_02_payload_vector_test.cpp:145-199`, `runtime/native/src/main/cpp/m2_02_foundation_test.cpp:138-158`; task contract `docs/tasks/M2-02-native-decrypt-and-inmemory-loader.md:48,51`.

## P2 findings

### P2-1: documented AAR hash predates the JNI/R8 repair

The documented ignored AAR hash and embedded `proguard.txt` predate the `PayloadLoadException` JNI keep rule from commit `41db1f4b5f438ca8901f0ddcc073d9f360998087`. A new four-ABI Release AAR and hashes must be rebuilt and recorded at the next freeze.

Evidence at the rejected freeze: `docs/evidence/M2-02/local-validation.md:52`, `runtime/native/consumer-rules.pro:4-8`; task contract `docs/tasks/M2-02-native-decrypt-and-inmemory-loader.md:54`.

### P2-2: Runtime source APK limit exceeds the frozen product limit

The mapped source APK accepted up to `UINT32_MAX`, while the frozen product limit is `2,147,483,647` bytes. This expands the supported/resource-consumption boundary and permits an early mapping close to 4 GiB without matching tests.

Evidence at the rejected freeze: `runtime/native/src/main/cpp/mapped_apk.cpp:34-40`, `docs/tasks/M1-01-untrusted-apk-inspector.md:53`, `docs/tasks/M1-07-chunk-authenticated-container-contract.md:24`.

## Residual risk

Even after remediation, root, process injection, modified ART/kernel or full process control can intercept runtime plaintext. The design raises extraction cost and must not claim absolute confidentiality. CI artifacts expire, and the single Xiaomi API 29 arm64 device does not represent every OEM implementation.
