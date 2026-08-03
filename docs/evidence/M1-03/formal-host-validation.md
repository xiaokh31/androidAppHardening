# M1-03 formal Host validation

## Scope and current implementation

- Task: M1-03, Issue [#8](https://github.com/xiaokh31/androidAppHardening/issues/8).
- Branch: `feat/m1-03-binary-axml-transformer`.
- Base: `077e4be14865c777dbbf3c1a5a3d9609b3620868`.
- Current status: the first frozen candidate failed independent review with P0 `0`, P1 `3`, P2 `1`; the second frozen candidate closed all four findings but failed with one new P1 for quadratic element-path retention. Both targets are invalid. Frozen commit `1425f911eb48796a8e4ade9aa3c5fcec09cb1f7b` removes retained full paths, uses bounded structural role flags and adds a long-name/deep-nesting regression. Its Windows Host/root/static rerun, third independent P0/P1/P2-zero review, API 29 arm64 physical-device matrix, published API 29/36 x86_64 KVM matrix and Ubuntu/Windows byte-equivalence gates pass. Draft PR [#35](https://github.com/xiaokh31/androidAppHardening/pull/35) remains unmerged.
- Product boundary: binary Manifest bytes only. No production ZIP/APK writer, signing operation, DEX change, ConfigV2 encoder or Runtime change is present.

The implementation copies the caller input, validates the supplied M1-01 summary, parses binary AXML iteratively under fixed limits, appends only missing strings/resource-map data, replaces or appends one application Factory attribute, reparses the result and enforces an ordered semantic whitelist. Public results and hashes use defensive byte-array copies; public failures do not include input paths, Manifest text or nested causes.

## Environment

| Item | Value |
|---|---|
| OS | Windows 10 `10.0.19045` amd64 |
| JDK | Eclipse Temurin `17.0.19+10` from ignored repository-local toolchain |
| Gradle | `9.5.0` |
| Kotlin Gradle plugin | `2.4.10` |
| Android Platform | `36` |
| Android Build Tools | package `36.1.0`; `aapt2` reports `2.20-14042983` |
| Fuzz | seed `1295069235` (`0x4d313033`), 5,000 samples |

No emulator was started. After the historical MIUI-rejected attempt, the user explicitly allowed USB installation and the same bounded API 29 arm64 acceptance runner completed both variants and final cleanup in 57.5 seconds.

## Commands and current results

| Command | Exit | Result |
|---|---:|---|
| project-local JDK 17 `./gradlew :host:axml:test --offline` with pinned `aapt2` and Android 36 `android.jar` properties | 0 | six positive fixtures, eighteen stable negatives, real `aapt2 link/dump` cross-check and 5,000 malformed samples PASS |
| project-local JDK 17 `./gradlew check verifyGovernance --offline` | 0 | 237 actionable tasks; M1-01 10,000-sample and M1-02 signer regressions PASS |
| `node tools/validation/verify-m0-05-apks.mjs` against both transformed Release/R8 APKs | 0 | signer/config, dual DEX, JNI, ABI, R8, native extraction modes and no-plaintext-payload static gates PASS |
| bounded API 29 arm64 runner against transformed extracted/direct Release/R8 fixtures, 20 cold starts per variant | 0 | instrumentation, lifecycle, cross-DEX, JNI, signer, metadata independence, cold starts, memory, zero plaintext DEX and final cleanup PASS |
| `git diff --check` | 0 | current implementation diff has no whitespace errors |

The local Kotlin daemon could not create its marker under the user-local C-drive directory in the restricted workspace. Gradle automatically used its supported in-process fallback and every command above exited `0`; no tool or dependency was downloaded to C by these validation commands.

## Positive and negative coverage

Positive fixtures cover UTF-8, UTF-16, default/custom Application, absent/existing Factory, a non-zero extended Factory attribute record, absent/existing resource map, metadata string/reference values, high-bit unsigned resource/typed values and an unknown XML node chunk. The transform runs twice for every fixture and must produce identical bytes. Input and returned arrays are mutated in the test harness to prove input immutability and defensive result copies. Every transform report row now records the old string-index digest, resource-map prefix digest, unknown-chunk sequence digest and high-bit typed-value digest/count.

The explicit negative matrix currently contains:

| Case | Stable result |
|---|---|
| truncated root bytes | `AXML_MALFORMED` |
| declared root beyond available bytes | `AXML_MALFORMED` |
| unsupported string-pool flags | `AXML_UNSUPPORTED_ENCODING` |
| string count above fixed budget | `AXML_LIMIT_EXCEEDED` |
| truncated/oversized encoded string length | `AXML_MALFORMED` |
| truncated resource map | `AXML_MALFORMED` |
| duplicate application | `AXML_MALFORMED` |
| nesting over 1,024 | `AXML_LIMIT_EXCEEDED` |
| 32,767-character element name combined with nesting over 1,024 | `AXML_LIMIT_EXCEEDED` |
| active namespace count over 1,024 | `AXML_LIMIT_EXCEEDED` |
| chunk count over 16,384 | `AXML_LIMIT_EXCEEDED` |
| attribute count over 16,384 | `AXML_LIMIT_EXCEEDED` |
| overlapping style chains exceed linear work budget | `AXML_LIMIT_EXCEEDED` |
| missing application | `AXML_APPLICATION_MISSING` |
| existing Shell Factory | `AXML_RESERVED_COLLISION` |
| reserved name/resource-ID collision | `AXML_RESERVED_COLLISION` |
| supplied M1-01 summary mismatch | `AXML_DIFF_VIOLATION` |
| Manifest over 16 MiB | `AXML_LIMIT_EXCEEDED` |

The 5,000-sample deterministic corpus accepted 220 structurally valid mutations and classified the remainder only through stable `AXML_*` errors. It produced no unexpected exception or crash.

## Canonical Host reports

| Artifact | SHA-256 |
|---|---|
| `transform-matrix.json` | `35bd420aa0fe05e1a5efee197bdea8d3699f5de743bf54974f13833e24ef5635` |
| `error-matrix.json` | `9a60c0c9fe710798d7f458822c1f2d6ffb9a22527a43c176c6c3869fb6dcf49c` |
| `fuzz-summary.json` | `d1dbf919a489a067506ab40b629916ea66a5b8a3e3ced42e710ae8dc57f8dced` |
| `aapt2-cross-check.json` | `916e2d79af152c6090fc7ba0c4b9b24f054f0eb094ff2968192b922d0d593672` |

The transform report records before/after/diff hashes for every fixture. The `aapt2` report includes a Manifest independently produced by `aapt2 link`, then transformed and read by `aapt2 dump xmltree`; it confirms the Shell Factory while retaining the original `.AaptApplication` and `fixture.metadata=kept`.

## Test-only Release/R8 device fixtures

The ignored device harness links the original binary Manifest with pinned `aapt2`, invokes the production transformer as a separate Gradle process, replaces only `AndroidManifest.xml` in a synthetic M0-05 compatibility APK, then zipaligns and externally signs the ignored install copy with an ephemeral test key. This is validation infrastructure only; no APK/ZIP writer or signing capability is added to a production module.

| Variant | Original Manifest SHA-256 | Transformed Manifest SHA-256 | Signed test APK SHA-256 |
|---|---|---|---|
| extracted | `4e5343457cd915067391aaf1026d656173687cd2569ffdb6cc11c48a3c7e5ace` | `8050475b79e73ac4026bba45d45b99b06d3ef3bcc0584dbe0027525f3390318e` | `9c31c54ad001613130b2150937f95d94a75f5bc2cf12bc2af87e8206ac381c18` |
| direct | `f02908f191c4267c983da6d15d904dd51d9ae3adcb1b49537ea545f673119080` | `8ef367881530d7520106ff9e26f95f0df14a62d2bca9351bdb6db998d5106f8b` | `ece3317e12b420e7afb66ee16f08c42518772818ee6f16b97a52a3f2bb524f64` |

Pinned `aapt2 dump xmltree` observes `ah.runtime.bootstrap.ShellAppComponentFactory`, the original `PayloadApplication`, and `ah.m103.fixture=preserved` in both APKs. The existing M0-05 static verifier independently reports `PASS` for both transformed APKs.

## API 29 physical-device acceptance

The first attempt at `2026-08-03T12:56:58+08:00` was rejected by MIUI with `INSTALL_FAILED_USER_RESTRICTED` and remains historical non-pass evidence. After the user explicitly allowed installation, the bounded rerun from `2026-08-03T14:07:55+08:00` through `14:08:52+08:00` verified a unique API 29 `arm64-v8a` device with 64-bit userspace, `user/release-keys`, `ro.secure=1`, `ro.debuggable=0`, and shell UID 2000 non-root.

Both transformed Release/R8 variants passed instrumentation, lifecycle order, multidex, JNI, early signer cross-check, metadata independence and zero plaintext DEX checks. Each completed 20 cold starts with the target payload Activity resumed every time. Extracted reported P50/P95 `269/294 ms` and peak total PSS `50,382 KB`; direct reported P50/P95 `332/370 ms` and peak total PSS `51,943 KB`. The shared M0-05 pre-CLI device harness intentionally reports task ID M0-05 because it owns the runtime assertions; M1-03 supplies only its transformed target APKs and does not add product ZIP/signing behavior.

The ignored controlled evidence is `build/m1-03/device-api29-arm64-review3/`: report SHA-256 `4f563a49c76ff27bef8033401b47591d8acd45e43c70f2045adc6ff3b57de042`, JUnit SHA-256 `af4bd59eaebe4448c9512129aaf3710d1169718891e59deffdf8d5f900da9f61`, and command transcript SHA-256 `ac2521cbaa67d8865788f1125bec07477eeff3138874487dbf21f5c4e9c47ec6`. Runner cleanup and an independent post-run `pm path`/`pidof` check confirmed all four target/test package names and processes absent. The raw serial and ignored signing material are not recorded in versioned evidence.

## Published PR validation

The authorized branch publication created the sole Issue #8 draft PR [#35](https://github.com/xiaokh31/androidAppHardening/pull/35). Initial PR HEAD `c6ed194c2fea9672d6cdd38cf181560e8d76e87f` completed all six required jobs:

| Workflow / job | Result | Duration |
|---|---|---:|
| [M0-05 Linux KVM run 30789605156](https://github.com/xiaokh31/androidAppHardening/actions/runs/30789605156), API 29 x86_64 job `91610180311` | PASS | 7m58s |
| same run, API 36 x86_64 job `91610180365` | PASS | 8m54s |
| [Build run 30789605218](https://github.com/xiaokh31/androidAppHardening/actions/runs/30789605218), Ubuntu 24.04 job `91610180655` | PASS | 3m11s |
| same run, Windows 2025 job `91610180663` | PASS | 4m02s |
| [Governance run 30789605187](https://github.com/xiaokh31/androidAppHardening/actions/runs/30789605187), Ubuntu 24.04 job `91610180563` | PASS | 14s |
| same run, Windows 2025 job `91610180603` | PASS | 41s |

Both Build jobs explicitly produced and matched the canonical `transform`, `error`, `fuzz` and `aapt2` SHA-256 values listed above. The ignored downloaded KVM evidence is `build/m1-03/github-run-30789605156/`.

API 29 and API 36 each passed transformed extracted/direct instrumentation, lifecycle, multidex, JNI, signer/config/metadata, the integrated 18-case external negative matrix, independent 18-case startup-negative reports, 20 cold starts, no-Factory handling, zero plaintext DEX and cleanup. API 29 extracted/direct P50/P95 were `771/975 ms` and `838/953 ms`, with peak total PSS `61,904/70,665 KB`; API 36 values were `1,287/1,636 ms` and `1,241/1,512 ms`, with peak total PSS `14,691/14,730 KB`.

The API 29 device report SHA-256 is `0ebd9d4cf89caaec3cfd056de34d93e2f810d6b793c0b6d0a7d6385265b05700`; API 36 is `d00b794661191fac624ffa0be44e9841966a55ba489e61b193d58c07bb36b7b3`. The PR was still draft, OPEN, CLEAN and MERGEABLE after these checks.

## Remaining gates

- The local implementation and independent parser/security-review gate are closed at `1425f911eb48796a8e4ade9aa3c5fcec09cb1f7b`; do not reuse either archived FAIL target.
- Push this evidence-only commit and require the replacement PR HEAD to pass the same six checks; the live PR check rollup is authoritative for that documentation successor.
- Keep PR #35 draft. Ready/merge requires separate explicit user authorization and a final expected-head check.
