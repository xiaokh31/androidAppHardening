# M1-01 formal host validation

## Frozen candidate

- Task: M1-01, Issue [#6](https://github.com/xiaokh31/androidAppHardening/issues/6).
- Branch: `feat/m1-01-untrusted-apk-inspector`.
- Verified implementation commit: `bb2a6a93b840dd0416118119b4fe4434e395be02`.
- Base: `main@e02954f8d4ff9bd9c1a9b643d5bc8c88cd295030`.
- Timestamp: `2026-08-02T13:20:17+08:00`.
- Environment: Microsoft Windows 10 `10.0.19045` x64; Temurin `17.0.19+10`; Gradle `9.5.0`; Kotlin JVM plugin `2.4.10` (Gradle embedded Kotlin `2.3.20`).
- Tool source: repository-local ignored `.toolchains/jdk/jdk-17.0.19+10` and `.toolchains/gradle/gradle-9.5.0`; existing Gradle dependency cache reused offline. No download and no emulator/device process.

## Commands and results

All Gradle commands set `JAVA_HOME` to the repository-local JDK and use `--offline --no-daemon --console=plain`.

| Command | Exit | Result |
|---|---:|---|
| `gradle :host:apk-inspector:test` | 0 | PASS; 32 named negative fixtures, every public error code, positive/boundary models, 10,000 seeded fuzz samples |
| `gradle check` | 0 | PASS; 231 actionable tasks, including the same formal inspector self-test, Android lint/static tasks and existing ConfigV2 tamper suite |
| `node tools/governance/validate-project-package.mjs` | 0 | PASS; 26 task cards, 11 core docs, 7 ADRs |
| `node .agents/skills/coordinate-project-handoff/scripts/validate-handoff.mjs HandOff.md --strict` | 0 | PASS without exemption on clean implementation commit |
| `git diff --check` | 0 | PASS |

The formal fuzz seed is `0x4d312d3031`; sample count is exactly `10,000`. The root-check run reported peak used JVM memory `316,326,544` bytes. The self-test periodically executes identical bytes twice and requires byte-stable success/error outcomes. It also verifies input hashes before/after success and failure, `INPUT_CHANGED`, cancellation cleanup, Windows rename-after-close, defensive byte-array copies, unmodifiable lists and no extraction artifact creation.

## Canonical accepted model

The sanitized baseline input SHA-256 is `fcad7d3410aebcec8a9347a001ee5d96f672a116445982dd6c929e98ab8879fb`. Its package UTF-8 SHA-256 is `6a74da948cef80fb8e8655c1a66992b118979135694811f53594b52f31aebc65`.

```json
{"inputSha256":"fcad7d3410aebcec8a9347a001ee5d96f672a116445982dd6c929e98ab8879fb","packageName":"ah.fixtures.inspector","packageNameSha256":"6a74da948cef80fb8e8655c1a66992b118979135694811f53594b52f31aebc65","minSdk":29,"targetSdk":36,"applicationClass":"ah.fixtures.inspector.FixtureApplication","appComponentFactoryClass":"ah.fixtures.inspector.FixtureFactory","dexEntries":["classes.dex","classes2.dex"],"nativeAbis":["armeabi-v7a","arm64-v8a","x86","x86_64"],"markerIds":["CUSTOM_APPLICATION","CUSTOM_APP_COMPONENT_FACTORY","NATIVE_ABI_ARMEABI_V7A","NATIVE_ABI_ARM64_V8A","NATIVE_ABI_X86","NATIVE_ABI_X86_64"]}
```

The canonical model report SHA-256 is `a689e24f5a0e5dd81fcfe4175cacb3566477a4a659ed3da5dd3c6a84014264d3`. The full 32-fixture error matrix report SHA-256 is `545aa5987cc82fc98a0f7f20dcc5492ba84d40d91431a3350da6122854f39618`. These two reports are deterministic inputs for the pending Ubuntu equivalence gate; peak-memory data is intentionally kept in a separate non-canonical report.

## Public error-code evidence

| Code | Representative synthetic fixture | Input SHA-256 | Actual |
|---|---|---|---|
| `INPUT_IO` | `missing.apk` | not applicable | `INPUT_IO` |
| `INPUT_ZIP_STRUCTURE` | `central-local-length-conflict.apk` | `3d1699fe363ac8c9abfa719eda3953e35c9c05d24aa59d15fcf01d3b2b9ace43` | `INPUT_ZIP_STRUCTURE` |
| `INPUT_LIMIT_EXCEEDED` | `compression-bomb.apk` | `883d6242b5d05031135f5c9861b94f04357deb4062d92473c1b2530e1dc1ddc3` | `INPUT_LIMIT_EXCEEDED` / `compressionRatio` |
| `INPUT_DUPLICATE_ENTRY` | `duplicate-entry.apk` | `bf2be951dc95a849af4a502bbc04b9934d2d67137385b1a1d67bc0a1cf841c50` | `INPUT_DUPLICATE_ENTRY` |
| `INPUT_PATH_UNSAFE` | `path-traversal.apk` | `0a88fa375f5e3181b78b90e2c8cc112bbf7af22305c6408959c6b533fa378c56` | `INPUT_PATH_UNSAFE` |
| `INPUT_MANIFEST_INVALID` | `manifest-string-offset-conflict.apk` | `b85bcad5075378318c8a28e1a39747bf32f1936acfb1a9043bd8fbf39bfdec20` | `INPUT_MANIFEST_INVALID` |
| `INPUT_DEX_INVALID` | `dex-checksum.apk` | `8912f27bf401dd31fec98e4de9e37ddca3361a04debf59a42ea7213bb9634a30` | `INPUT_DEX_INVALID` |
| `INPUT_CHANGED` | `input-changed.apk` | before `fcad7d...879fb`, after `c5a4a21a91b66200d4b824a32fdfa3deb2f99b91945eafd7a76e4eeefea77b0e` | `INPUT_CHANGED` |
| `COMPAT_MIN_SDK` | `min-sdk-28.apk` | `7019a9ab9046338a147590208cfedef39762f4c624c9e0bfb85aad905e687149` | `MIN_SDK_BELOW_29` |
| `COMPAT_SPLIT` | `split.apk` | `082573481d5749f9541e06d2baee26ef68bbbd0a0e838b2349710d4d67d4cc38` | `MANIFEST_SPLIT_ATTRIBUTE` |
| `COMPAT_FRAMEWORK` | `flutter.apk` | `e88a29b174c075e33fa0de15e92153e623555af3c43ded001e85c7cdff68251e` | `FLUTTER_RUNTIME` |
| `COMPAT_EXISTING_SHELL` | `existing-shell.apk` | `a6a66b7a2dc2abcf8b8ce377b36da51afae74e6e9e20b5d39abbaa0119b0120e` | `QIHO0_JIAGU_SHELL` |
| `COMPAT_RESERVED_NAMESPACE` | `reserved-namespace.apk` | `c988902d4b0d018ed4647d0b8dcb45fa3c1d4f5e6b628c6f7e40120b2d149cda` | `AH_RUNTIME_ASSET_NAMESPACE` |

Additional named fixtures cover NFC collision, 1025-byte path, Zip64, encrypted entry, offset overflow, 65 DEX, illegal package, non-contiguous DEX, AAB/APKS, Unity, React Native, Tinker, Sophix, plugin Runtime, unsupported ABI, and the reserved class/native namespaces. Positive boundaries cover a 1024-byte path, 64 contiguous DEX files, single/multi DEX, custom/no Application and Factory, four independent supported ABIs, STORED, raw DEFLATE and signed data descriptor entries.

## Side-effect and resource conclusion

- Production code opens only the input `Path` with `READ`; it has no output-path API and no filesystem write call.
- ZIP data is CRC-checked with bounded 64 KiB buffers. Manifest and each DEX are materialized one at a time in fixed segments; the implementation does not retain plaintext payloads or full class-name lists after parsing.
- The self-test snapshots the corpus directory before/after inspection, confirms no new extraction path, then removes its generated corpus in `finally`.
- Successful, malformed, changed and cancelled paths all release the channel; Windows rename/move succeeds immediately after each case.
- No local emulator was started. No APK, plaintext DEX, certificate, secret or customer path is committed.

## Pending gates

This is the local Windows frozen candidate. The next gate is an independent read-only `m1_01_security_review` of the exact evidence commit. Publication remains forbidden until that review reports P0/P1/P2 all zero. Ubuntu canonical-report equivalence and normal PR CI remain required after the independent review and publication authorization; M1-01 is not complete and M1-02/M1-03/M2 remain blocked.
