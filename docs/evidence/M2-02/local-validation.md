# M2-02 local validation

## 2026-08-07 bounded format foundation

- Branch/commit before implementation: `feat/m2-02-native-decrypt-loader@311913e857062539bc4e14acb1e0dcff0164e968`.
- Scope: dependency-free C++17 parsing for exact 160-byte HeaderV2, bounded SPV1, 128-byte RecordV2, 32-byte ChunkV2, 768-byte ConfigV2 and cross-record/chunk topology. No JNI, ClassLoader, APK write, device or emulator operation was performed.
- Fail-closed bounds include 64 DEX, 16 lineage entries, 65,536 chunks, 64 KiB canonical chunks, 512 MiB per DEX, 4 GiB total DEX, checked payload/tag arithmetic, canonical DEX names, nonzero nonce prefixes, reserved/zero-fill fields and the shell-Factory exclusion.
- MSVC command: compile `container_format.cpp` and `container_format_test.cpp` with Visual Studio 2022 x64 `cl.exe /std:c++17 /EHsc /W4 /WX /DAH_CONTAINER_FORMAT_STANDALONE_TEST`, then run the generated ignored test executable. Exit code `0` at `2026-08-07T14:03:42+08:00`.
- Android command: repo-local JDK 17 and Gradle user home, `gradlew.bat :runtime:native:assembleRelease --offline --no-daemon --no-configuration-cache`. Exit code `0` in 30 seconds at `2026-08-07T14:04:35+08:00`; `armeabi-v7a`, `arm64-v8a`, `x86` and `x86_64` CMake builds all succeeded.
- Generated `.obj`, executable, AAR and native build outputs remain untracked under ignored build locations; two accidentally emitted root `.obj` files were immediately removed after exact absolute-path validation.

The foundation does not yet claim M2-02 completion. Native ZIP asset location, Config/share recovery, manifest HMAC verification, per-chunk GCM-to-zlib transaction ownership, anonymous mappings, JNI/Java facade and device matrices remain required before freeze or publication.
