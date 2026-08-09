# M2-02 independent read-only security review 2

- Frozen SHA: `6916c96c00efb0d9749d1f0f3ca956d1927106f2`
- Implementation parent: `ea805004fcadd8fc704e3125fa551c862d5c77f5`
- Result: **FAIL**
- Findings: `P0=0`, `P1=2`, `P2=1`
- Reviewer mode: independent and read-only; no edit, stage, commit, push, download, emulator or APK installation.

The second frozen SHA is invalidated. README completion, HandOff completion, PR readiness and merge remain prohibited until a new frozen commit passes a full independent review with `P0=0`, `P1=0`, `P2=0`.

## Verified evidence

- Build run `31300991302` and KVM run `31300991305` both target implementation parent `ea805004fcadd8fc704e3125fa551c862d5c77f5`; Ubuntu, Windows, API 29 x86_64 and API 36 x86_64 jobs passed.
- The API 29 arm64 extracted/direct reports, command transcripts, instrumentation logs and APK hashes match `local-validation.md`; each variant recorded 20 cold starts, 20 memory samples, instrumentation success, zero plaintext DEX files and cleanup.
- The remediation closed the first review's zlib zeroizing allocator, Release AAR/SO hash and exact 2 GiB source-size-limit findings.
- The frozen commit itself only adds evidence documentation and does not change production or test code.

## P1 findings

### P1-1: cleanup aggregation is not tested across the real JNI boundary

The Native implementation now preserves the primary error and reports cleanup failure, but no executable test injects a cleanup failure through JNI and asserts the exact primary/suppressed stable codes. Host tests call `PayloadHandle.close()` directly, while the existing device test covers only normal close and the Java OOM ownership window. The required rollback, handle-install and explicit-close cleanup behavior therefore remains unverified at the JNI/Java boundary.

Evidence at the rejected freeze: `runtime/native/src/main/cpp/jni_bridge.cpp:52-87,356-371,409-422`; task contract `docs/tasks/M2-02-native-decrypt-and-inmemory-loader.md`.

### P1-2: KVM connected checks are empty and metadata golden coverage is partial

The KVM workflow invokes both `connectedCheck` tasks, but neither module contains an `src/androidTest` source set. Jobs `93213651710` and `93213651733` report `compileDebugAndroidTestJavaWithJavac NO-SOURCE`, so the apparent connected-check success executes no tests. The device metadata assertions also do not compare all ten authenticated getters against independent expected values.

Evidence at the rejected freeze: `.github/workflows/m0-05-linux-kvm.yml:470-472`, KVM jobs `93213651710` and `93213651733`; task contract `docs/tasks/M2-02-native-decrypt-and-inmemory-loader.md`.

## P2 findings

### P2-1: ZIP overlap validation excludes the local header and name/extra region

The overlap predicate compares only payload data ranges. A crafted entry can place its local header, name or extra field inside another fixed asset while keeping the eventual data ranges disjoint. The fixed-asset parser must reject overlap across the complete local-entry range beginning at the local-header offset, with a deterministic regression test.

Evidence at the rejected freeze: `runtime/native/src/main/cpp/zip_assets.cpp`; task contract `docs/tasks/M2-02-native-decrypt-and-inmemory-loader.md`.

## Residual risk

Even after remediation, root, process injection, modified ART/kernel or full process control can intercept runtime plaintext. The design raises extraction cost and must not claim absolute confidentiality. CI artifacts expire, and the single Xiaomi API 29 arm64 device does not represent every OEM implementation.
