# M1-01 formal host validation

## Remediated implementation candidate

- Task: M1-01, Issue [#6](https://github.com/xiaokh31/androidAppHardening/issues/6).
- Branch: `feat/m1-01-untrusted-apk-inspector`.
- Verified implementation commit: `e97d67f9fbfc5b4c23751a85822dc6c96af4c6c5`.
- Base: `main@e02954f8d4ff9bd9c1a9b643d5bc8c88cd295030`.
- Timestamp: `2026-08-02T14:23:23+08:00`.
- Environment: Microsoft Windows 10 `10.0.19045` x64; Temurin `17.0.19+10`; Gradle `9.5.0`; Kotlin JVM plugin `2.4.10`.
- Tool source: repository-local ignored JDK and Gradle with the existing dependency cache in offline mode. No download, device or emulator was used.

## Commands and results

All Gradle commands set `JAVA_HOME` to the repository-local JDK and use `--offline --no-daemon --console=plain`.

| Command | Exit | Result |
|---|---:|---|
| `gradle :host:apk-inspector:test` | 0 | PASS; 58 named error fixtures, positive/boundary models and exactly 10,000 seeded samples |
| `gradle check` | 0 | PASS; 231 actionable tasks, including the formal inspector suite, Android lint/static tasks and ConfigV2 tamper suite |
| `git diff --check` | 0 | PASS |

The formal fuzz seed is `0x4d312d3031`. The root-check run reported peak used JVM memory `108,715,272` bytes. The repeated-string-data-offset DEX regression contains 4,096 string/type/class rows and must reject within five seconds. Success, failure, input-change and cancellation paths verify handle release and unchanged input; a restore-after-parse regression proves the returned model cannot be detached from its initial block-hash snapshot. The inspector has no extraction or output path.

## Canonical accepted model

```json
{"inputSha256":"3e14f28ae8426392e2ce28359f5da5bb023b5322c317809078fcc2aeb2c3e419","packageName":"ah.fixtures.inspector","packageNameSha256":"6a74da948cef80fb8e8655c1a66992b118979135694811f53594b52f31aebc65","minSdk":29,"targetSdk":36,"applicationClass":"ah.fixtures.inspector.FixtureApplication","appComponentFactoryClass":"ah.fixtures.inspector.FixtureFactory","compatibilityRulesVersion":"compatibility-rules-v1","dexEntries":["classes.dex","classes2.dex"],"nativeAbis":["armeabi-v7a","arm64-v8a","x86","x86_64"],"markerIds":["CUSTOM_APPLICATION","CUSTOM_APP_COMPONENT_FACTORY","NATIVE_ABI_ARMEABI_V7A","NATIVE_ABI_ARM64_V8A","NATIVE_ABI_X86","NATIVE_ABI_X86_64"]}
```

- Canonical model report SHA-256: `c15561ee6d6e879ad9db058be2762282538a77d4204279d6b5d6d57b1f1d52bf`.
- Full 58-fixture error matrix SHA-256: `b396616ff369fa2d4db56c92f6908253339867d71554f96debee4d7ed06a02fc`.
- Fuzz summary is deliberately non-canonical because it includes measured memory; seed and sample count remain fixed.

These canonical reports are the byte-equivalence inputs for the later Ubuntu/Windows PR gate.

## Representative failure evidence

| Requirement | Synthetic fixture | Input SHA-256 | Actual |
|---|---|---|---|
| ZIP central/local range | `central-local-length-conflict.apk` | `125c905a8e6161ff2fa32cab79e1b71820971c6053c276388b8c25347ec1d505` | `INPUT_ZIP_STRUCTURE` |
| Compression budget | `compression-bomb.apk` | `8f2dcbaa82fcdbedb51f26087b3ae0384b85d11b3625b6d91465f4083580df6a` | `INPUT_LIMIT_EXCEEDED` |
| Duplicate entry | `duplicate-entry.apk` | `af98ea57dbc10e7f49a5a0495e4c2e336cac79a87426595bafbcd21b4aac37c4` | `INPUT_DUPLICATE_ENTRY` |
| Unsafe path | `path-traversal.apk` | `57360525e5c5d6ef7b5f0e3dd0f714fd4694e5a94f0c433f0b6709495d8363bd` | `INPUT_PATH_UNSAFE` |
| AXML resource ID mismatch | `manifest-resource-id-mismatch.apk` | `025f3ba181f789f6d71a2b35cd97452c925712e23d2a5e4d32246edd4ccedab4` | `INPUT_MANIFEST_INVALID` |
| DEX repeated string-data offset | `dex-repeated-string-data-offset.apk` | `0c5c019028d1b43f8c48ab90a1088a472f3232f7e8098ce849e0c03bf556f758` | `INPUT_DEX_INVALID` |
| API boundary | `min-sdk-28.apk` | `d8bcb30f8140740c126d576720546602cfe544779cda7433769a9e7d75567cc5` | `COMPAT_MIN_SDK` / `MIN_SDK_BELOW_29` |
| Framework | `flutter.apk` | `6e437364ead1e35b20b09b151bf56581a214436e496b41c43f6fe25943376afc` | `COMPAT_FRAMEWORK` / `FLUTTER_RUNTIME` |
| ELF ABI mismatch | `native-elf-abi-mismatch.apk` | `061bb55e5f45a69e1d349be80520e5b4f34c39354716a02003bacff5dfafd695` | `COMPAT_FRAMEWORK` / `NATIVE_ELF_ABI_MISMATCH` |
| Existing shell | `existing-shell.apk` | `dc2e571c2b6385692e737f6a3f25dce6f52c07d6f55c4f0ab7460cb0c3b5d9e1` | `COMPAT_EXISTING_SHELL` / `QIHO0_JIAGU_SHELL` |
| Reserved namespace | `reserved-namespace.apk` | `9e38ad62ec9086c354b4b06b67cb44af32dbd9d0865c2d80a5a229763ba8efaa` | `COMPAT_RESERVED_NAMESPACE` / `AH_RUNTIME_ASSET_NAMESPACE` |

The full matrix additionally covers `INPUT_IO`, actual CRC corruption, Zip64, encrypted entries, NFC collision, exact 1,024-byte path, package missing/duplicate/illegal/invalid UTF-8, resource-map overflow, namespace scope, namespaced core elements, raw/typed AXML conflict, DEX checksum/SHA-1/file-size/table/map/data/magic/version/descriptor errors, non-canonical and 64/65 DEX boundaries, ordinary and restored-byte `INPUT_CHANGED`, Split/AAB/APKS, Unity, React Native, Tinker, Sophix, plugin Runtime, unsupported ABI, invalid/truncated ELF, and all reserved namespaces.

## Review history and remediation

- The first review attempt was interrupted before a formal conclusion. Its allocation concern was treated as a failed gate and fixed by `d3dbfaa8ce4317d8b394f22478ddbb185fd480cb`.
- The completed second independent read-only review of frozen SHA `02e6334e916581f3d49c89ec512f6e9a9ec4a245` returned FAIL with P0 `0`, P1 `4`, P2 `3`; the exact findings are archived in `security-review-2.md`.
- Implementation `e267e3c7eab7d3b7d5d8c90947c79f0c77ee1208` closes every reported code/test finding: fixed Android resource IDs and namespace scope, raw/typed agreement, bounded DEX offset uniqueness, explicit DEX versions, ELF/path ABI agreement, `compatibility-rules-v1`, and all named regression gaps.
- The third independent read-only review of frozen SHA `0bbbeb6da8573ab770b0ca4ec1f6227e444244a1` returned FAIL with P0 `0`, P1 `4`, P2 `0`; it found separate hash/parse handles, boxed DEX offset memory amplification, truncated ELF positives and incomplete DEX map/data positives. The exact findings are archived in `security-review-3.md`.
- Implementation `e97d67f9fbfc5b4c23751a85822dc6c96af4c6c5` binds every parser read to an initial per-block snapshot on the same handle, bounds offset uniqueness with a file-sized BitSet, validates complete ELF headers and emits canonical synthetic ELF32/ELF64 headers, and closes DEX fixed-table/data/map-list structure with standard positive fixtures.

This is a remediated Windows candidate, not a completion claim. A new evidence commit must be frozen and independently reviewed with P0/P1/P2 all zero before publication. Ubuntu equivalence and normal PR CI remain later gates. M1-02, M1-03 and M2 remain blocked.
