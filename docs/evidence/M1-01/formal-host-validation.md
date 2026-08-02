# M1-01 formal host validation

## Remediated implementation candidate

- Task: M1-01, Issue [#6](https://github.com/xiaokh31/androidAppHardening/issues/6).
- Branch: `feat/m1-01-untrusted-apk-inspector`.
- Verified implementation commit: `e267e3c7eab7d3b7d5d8c90947c79f0c77ee1208`.
- Base: `main@e02954f8d4ff9bd9c1a9b643d5bc8c88cd295030`.
- Timestamp: `2026-08-02T14:02:26+08:00`.
- Environment: Microsoft Windows 10 `10.0.19045` x64; Temurin `17.0.19+10`; Gradle `9.5.0`; Kotlin JVM plugin `2.4.10`.
- Tool source: repository-local ignored JDK and Gradle with the existing dependency cache in offline mode. No download, device or emulator was used.

## Commands and results

All Gradle commands set `JAVA_HOME` to the repository-local JDK and use `--offline --no-daemon --console=plain`.

| Command | Exit | Result |
|---|---:|---|
| `gradle :host:apk-inspector:test` | 0 | PASS; 54 named error fixtures, positive/boundary models and exactly 10,000 seeded samples |
| `gradle check` | 0 | PASS; 231 actionable tasks, including the formal inspector suite, Android lint/static tasks and ConfigV2 tamper suite |
| `git diff --check` | 0 | PASS |

The formal fuzz seed is `0x4d312d3031`. The root-check run reported peak used JVM memory `316,352,352` bytes. The repeated-string-data-offset DEX regression contains 4,096 string/type/class rows and must reject within five seconds. Success, failure, input-change and cancellation paths verify handle release and unchanged input; the inspector has no extraction or output path.

## Canonical accepted model

```json
{"inputSha256":"3588df49187eec17af7007468f1d20ed60632078750b182a7f8e8964175f48c9","packageName":"ah.fixtures.inspector","packageNameSha256":"6a74da948cef80fb8e8655c1a66992b118979135694811f53594b52f31aebc65","minSdk":29,"targetSdk":36,"applicationClass":"ah.fixtures.inspector.FixtureApplication","appComponentFactoryClass":"ah.fixtures.inspector.FixtureFactory","compatibilityRulesVersion":"compatibility-rules-v1","dexEntries":["classes.dex","classes2.dex"],"nativeAbis":["armeabi-v7a","arm64-v8a","x86","x86_64"],"markerIds":["CUSTOM_APPLICATION","CUSTOM_APP_COMPONENT_FACTORY","NATIVE_ABI_ARMEABI_V7A","NATIVE_ABI_ARM64_V8A","NATIVE_ABI_X86","NATIVE_ABI_X86_64"]}
```

- Canonical model report SHA-256: `fc224233c5a7a61b13075431684f0478c83f784444e712492315b4631c9efcc8`.
- Full 54-fixture error matrix SHA-256: `b6df7c5d4ba216f78a3b52d3bac043d64900fed5ab4ed3b3a10f554a975c0d1f`.
- Fuzz summary is deliberately non-canonical because it includes measured memory; seed and sample count remain fixed.

These canonical reports are the byte-equivalence inputs for the later Ubuntu/Windows PR gate.

## Representative failure evidence

| Requirement | Synthetic fixture | Input SHA-256 | Actual |
|---|---|---|---|
| ZIP central/local range | `central-local-length-conflict.apk` | `95bf65f78b922d4ad6b08d02c5614633cb05609f9716ee70c8dfc7b8db6ce7a6` | `INPUT_ZIP_STRUCTURE` |
| Compression budget | `compression-bomb.apk` | `d7d39ffd4f0892717c4d6a7309099bcbd1fb8ec1ba122abd4864bd1e02232aac` | `INPUT_LIMIT_EXCEEDED` |
| Duplicate entry | `duplicate-entry.apk` | `320a918760b75eace1c140fc6ef3dd849591f849bcb5b9e3c5490de7de7ef880` | `INPUT_DUPLICATE_ENTRY` |
| Unsafe path | `path-traversal.apk` | `7efcfcaa60ea0432d4f146d82d2a1a4e87dc8b6ea480301dee6d74094a443d6c` | `INPUT_PATH_UNSAFE` |
| AXML resource ID mismatch | `manifest-resource-id-mismatch.apk` | `f779537987bb159d0212a62b1c926721755f43c2baa1fff90c81c0ac384f9a06` | `INPUT_MANIFEST_INVALID` |
| DEX repeated string-data offset | `dex-repeated-string-data-offset.apk` | `a8eba2a704eec6f91a1a11fd4d3881484b143463faf04773a7a44a2d035cf0ca` | `INPUT_DEX_INVALID` |
| API boundary | `min-sdk-28.apk` | `351f522b7966280d6e3b5e09dad16355790ee7cda74a179f80f8ae19615251ed` | `COMPAT_MIN_SDK` / `MIN_SDK_BELOW_29` |
| Framework | `flutter.apk` | `f3abe460ce7b89d5f4a3250124031fe6e338f6da31d9199ca1b57b31321bd05e` | `COMPAT_FRAMEWORK` / `FLUTTER_RUNTIME` |
| ELF ABI mismatch | `native-elf-abi-mismatch.apk` | `c8f532fc583d1d5d6543bdd72ec417d7586bdf7ecc127ab075a1c903f1c315b2` | `COMPAT_FRAMEWORK` / `NATIVE_ELF_ABI_MISMATCH` |
| Existing shell | `existing-shell.apk` | `45efadc3a140e26f58cb3359275155f68e532b0f6cb7d33a38bc50c854760ba6` | `COMPAT_EXISTING_SHELL` / `QIHO0_JIAGU_SHELL` |
| Reserved namespace | `reserved-namespace.apk` | `712c7fb3770c1df72a4f0f07230ea30d913ff63d357ff4a3bf424bb85c2e3504` | `COMPAT_RESERVED_NAMESPACE` / `AH_RUNTIME_ASSET_NAMESPACE` |

The full matrix additionally covers `INPUT_IO`, actual CRC corruption, Zip64, encrypted entries, NFC collision, exact 1,024-byte path, package missing/duplicate/illegal/invalid UTF-8, resource-map overflow, namespace scope, namespaced core elements, raw/typed AXML conflict, DEX checksum/SHA-1/file-size/table/magic/version/descriptor errors, non-canonical and 64/65 DEX boundaries, `INPUT_CHANGED`, Split/AAB/APKS, Unity, React Native, Tinker, Sophix, plugin Runtime, unsupported ABI, invalid ELF, and all reserved namespaces.

## Review history and remediation

- The first review attempt was interrupted before a formal conclusion. Its allocation concern was treated as a failed gate and fixed by `d3dbfaa8ce4317d8b394f22478ddbb185fd480cb`.
- The completed second independent read-only review of frozen SHA `02e6334e916581f3d49c89ec512f6e9a9ec4a245` returned FAIL with P0 `0`, P1 `4`, P2 `3`; the exact findings are archived in `security-review-2.md`.
- Implementation `e267e3c7eab7d3b7d5d8c90947c79f0c77ee1208` closes every reported code/test finding: fixed Android resource IDs and namespace scope, raw/typed agreement, bounded DEX offset uniqueness, explicit DEX versions, ELF/path ABI agreement, `compatibility-rules-v1`, and all named regression gaps.

This is a remediated Windows candidate, not a completion claim. A new evidence commit must be frozen and independently reviewed with P0/P1/P2 all zero before publication. Ubuntu equivalence and normal PR CI remain later gates. M1-02, M1-03 and M2 remain blocked.
