# M0-05 formal compatibility evidence

## Scope and status

- Task: `M0-05`
- Validation mode: `pre-cli`
- Branch: `spike/m0-05-application-factory-provider-jni-poc`
- Issue: `#5`
- First-review remediation commit: `789d37e9fa321b54ee19bf4af1382e589f2942d4`
- Second-review remediation implementation commit: `189a04c5286187ae61575d3a9ec574d62501eacc`
- KVM validation commit: `e54d3d2a06b11375cb08f09ebaedb51d6623920f`
- Frozen corrected evidence commit: `350d08ee5f3c83bf60dcbd4564866ffb5f819844`
- Remote KVM validation head: `e54d3d2a06b11375cb08f09ebaedb51d6623920f`
- Status at this snapshot: API 29/36 x86_64 GitHub Linux/KVM and API 29 arm64 physical-device review-3 acceptance PASS, and the corrected evidence is frozen locally at `350d08ee5f3c83bf60dcbd4564866ffb5f819844`. The third independent read-only review returned FAIL with P0 `0`, P1 `0`, P2 `1` solely because this document and `HandOff.md` had stale freeze/next-action wording; it confirmed the technical and device-evidence findings closed. The remote branch intentionally remains at `e54d3d2a06b11375cb08f09ebaedb51d6623920f`, no PR exists, and the exact next action is a fourth independent read-only review after this documentation reconciliation. Earlier review failures remain historical evidence and are not erased.
- Local emulator use: none. The x86_64 workflow owns its emulator lifecycle, has a 35-minute job limit, a 180-second boot limit, a 900-second acceptance-runner limit, and EXIT/INT/TERM cleanup.
- Security boundary: the signer/config binding is a synthetic-fixture PoC check. It is not the production ConfigV2 authentication planned for M1/M2.

## Local implementation gate

Executed on Windows 10 x64 with project-local Temurin `17.0.19+10`, Gradle `9.5.0`, Node.js `24.12.0`, and the already pinned Android toolchain. No tool or emulator was downloaded to the C drive for this run.

```text
gradle --offline --no-daemon --no-configuration-cache --console=plain :runtime:bootstrap:check :fixtures:android:check :tools:validation:check verifyGovernance
node tools/validation/verify-m0-05-apks.mjs <extracted> <direct> <extracted-test> <direct-test> <extracted-mapping> <extracted-usage> <direct-mapping> <direct-usage> <baseline> <fixture-signer-sha256>
apksigner verify --verbose <seven generated M0-05 APKs>
zipalign -c -P 16 4 <seven generated M0-05 APKs>
```

All commands exited `0`. The Gradle gate reported `BUILD SUCCESSFUL`, the ConfigV2 test reported the golden case plus 20 tamper/no-factory cases, governance reported 26 task cards, 11 core documents and 7 ADRs, the APK verifier reported `PASS`, and all seven signed APKs passed signature and alignment verification. The R8 scan reported that signing execution classes were removed and the verifier entry point was retained. The static comparison is explicitly an M0-04 `classloaderPocRelease` baseline (`145,488` bytes) against each M0-05 compatibility root DEX (`147,156` bytes), a `1,668`-byte delta. Because the variants differ by more than the verifier, this value is not attributed solely to verifier inclusion. The report field is therefore `m004_baseline_root_dex_delta`, not `verifier_root_dex_delta`.

## API 29 arm64 physical device

- Timestamp: `2026-08-02T11:50:38+08:00`
- Result: `PASS`
- Environment: Android API 29; `arm64-v8a,armeabi-v7a,armeabi`; 64-bit process; Xiaomi user/release-keys build; `ro.secure=1`; `ro.debuggable=0`; adb shell uid 2000; non-root.
- Device identifier: omitted. The ignored report stores only a SHA-256 digest of the serial.
- Commands: review-3 `run-m0-05-device-acceptance.mjs` with four extracted/direct signed/unsigned negative directories, followed by separate `run-m0-05-startup-negative.mjs` invocations for the extracted and direct packages.
- Validation command exit codes: `0`, `0`, `0`; `svc power stayon false` exited `0`; cleanup verification found no installed M0-05 package or remote negative directory.
- Raw ignored evidence: `build/m0-05/review3-device-arm64-api29/`
- Raw report SHA-256: `a44c64bbb0f9d8c17c0e1fab4b11e5ec0a31b060fda81ff99a330954ab9a312b`
- Redacted command log SHA-256: `6deb13bedcba927c76f59d5b8c1e30da2ca63746dc194923caa54b89abe57681`
- Device JUnit XML SHA-256: `05fd60dd5d1c5fea0d6aaa19ff2e4b94071019e4f725fc9f2e462f414bd75383`
- Static verifier peak memory: `51,900 KB`

| Variant | Instrumentation | Lifecycle/factory | Cross-DEX | JNI | Signer/config/metadata | Startup negatives | Plaintext DEX | Cold starts | p50 | p95 | Peak PSS |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| extracted (`extractNativeLibs=true`) | PASS | PASS | PASS | PASS | PASS | 18/18 PASS | 0 | 20/20 | 276 ms | 287 ms | 44,608 KB |
| direct (`extractNativeLibs=false`) | PASS | PASS | PASS | PASS | PASS | 18/18 PASS | 0 | 20/20 | 336 ms | 357 ms | 46,848 KB |

The no-original-factory case also passed: no original-factory events occurred, all six original component counts were zero, and the provisional and final loader identities were equal. Cleanup completed and the runner did not own or start an emulator.

### Frozen arm64 artifacts

| Artifact | SHA-256 |
|---|---|
| extracted Release/R8 APK | `a372cdb46fc907c3b019d6714d6890a69cc17ec634164d44d00090de2b41e998` |
| direct Release/R8 APK | `1da91daa8aa30cf0503fdaebebde74b44765dae624600279a1ac026b3747931f` |
| extracted instrumentation APK | `3b112a84359f295e89da15efab9449ad2a190f27880d88a3f9001aa5d6e71ed6` |
| direct instrumentation APK | `3273776a8f745f51aefc7cef441750297cfc13a55e2dd7000c24d8f43f1622be` |
| no-original-factory APK | `d6c4a2b6f528f3e935e4ff1abe47c13a8fc46808b9a91374dd3273bdad3ed29c` |
| AHDC payload | `bbfd4c5ce0434793d47a4f2e6ff01ec7a40fdeb1e7738ea017133e7a7fadd879` |
| ConfigV2 | `59874d8a10d69742f6426367e1c9326f489aeee263aaad846c44f7efc5de95c1` |
| payload DEX 1 / DEX 2 | `c54ce0f5a67a8e32e2644936b1682dd8e02c33da91d602150949d354a485804e` / `06200c4901642a01d3d7e2ab5a3e23e9b863f015d528731eb29823b7c858f819` |
| R8 mapping | `70d9652c8707b6fc9c42e440015528b6238a4dd3a0f92c1f2eec55bce8eb96dc` |
| R8 usage | `b84a8d149f8e8d9dbc1bcd26bfb0bee783bb0ce55388c6d370c4584ef7987cc4` |
| `libfixture_jni.so` arm64-v8a | `a2334bdf16584dc7d5983bb17f1e65bb0d3ac98ea51eac8a25f9a67483155e25` |
| `libfixture_jni.so` x86_64 | `fac69e5f5b9776b97c14e40d83ee54bbd0eb600c098949a09754cfe94198e2d1` |
| static verifier report | `33b5c4de90204f6f793ddbc91cbf0f06542cd7bb26b274a8de88e2fc688bc3a0` |
| extracted startup-negative report / JUnit / commands | `0eb8b070fbea49d0c5c8c8da83f110ef58f12bde22b1339004e49860077c353a` / `d2443194315e7c63f2795557659d8da75694feb761b6c0a5c632d69b85b22768` / `eca3f7a8f487d0bfd3c9a9ca14283fa0d2023e33ff9ba684522e14c76b51ef7e` |
| direct startup-negative report / JUnit / commands | `931d2f1e91627e34afaa9d3fcd39ba52d9789bdd74d4544287eeb1b7dcdf9da8` / `06eb6192ee566668b894a3d643f7b1b4dda87b6de079199cd5302c1a0eb9f7ce` / `6b4d91123bd762206f4a3d905c25981aad7fd36706abc839d0e0a452f5a4cf3c` |

Both instrumentation runs reported the exact lifecycle order, six original-factory component counts of `1`, `component_delegate_negative=16`, `native_negative=3`, `authenticated_native_negative=1`, signer/config/metadata checks, cross-DEX and JNI success. The signed case-folded duplicate ABI alias passed early signer/config authentication and returned `AAH-P004` before `LOADER_CREATED` or `JNI_LOADED`; the independent startup runner also installed both variant artifacts and recorded `loader_created=false`. The ignored one-time fixture signer digest is recorded in the raw report/config verification output, but all ephemeral keystores and passwords were deleted after validation and none is committed.

## GitHub Linux/KVM matrix

The committed workflow pins these official archives through `tools/validation/m0-05-linux-kvm-packages.json`:

| Environment | Fixed revision | Status |
|---|---:|---|
| API 29 x86_64 | system image revision 8; Emulator 37.1.11 | PASS |
| API 36 x86_64 | system image revision 2; Emulator 37.1.11 | PASS |

Both review-3 jobs built extracted/direct Release/R8 fixtures, executed independent 18-case startup-negative matrices for both variants, ran 20 cold starts per variant, generated JUnit XML, collected memory and no-plaintext-DEX evidence, and forcibly cleaned their AVD/emulator state. Each instrumentation run additionally proved that the signed duplicate ABI alias passed signer/config authentication and failed with `AAH-P004` before business JNI.

- GitHub Actions run: [#30729952586](https://github.com/xiaokh31/androidAppHardening/actions/runs/30729952586)
- Validated commit: `e54d3d2a06b11375cb08f09ebaedb51d6623920f`
- API 29 job: `91448336583`, `success`
- API 36 job: `91448336558`, `success`
- Run conclusion: `success`
- Local emulator use: none

| Environment / variant | Instrumentation and functional matrix | Independent startup negatives | Plaintext DEX | Cold starts | p50 | p95 | Peak PSS |
|---|---:|---:|---:|---:|---:|---:|---:|
| API 29 x86_64 extracted | PASS | 18/18 PASS | 0 | 20/20 | 792 ms | 956 ms | 60,416 KB |
| API 29 x86_64 direct | PASS | 18/18 PASS | 0 | 20/20 | 755 ms | 900 ms | 66,908 KB |
| API 36 x86_64 extracted | PASS | 18/18 PASS | 0 | 20/20 | 1,177 ms | 1,519 ms | 14,628 KB |
| API 36 x86_64 direct | PASS | 18/18 PASS | 0 | 20/20 | 1,239 ms | 1,510 ms | 14,766 KB |

The API 29 environment reported Android 10/API 29, `x86_64,x86`, a 64-bit process, non-root adb shell, `ro.secure=1`, and a userdebug/test-keys system image. The API 36 environment reported Android 16/API 36, `x86_64`, a 64-bit process, non-root adb shell, `ro.secure=1`, and a userdebug/test-keys system image. Both no-original-factory cases passed, both device runners reported cleanup PASS, and the workflow's EXIT/INT/TERM trap completed.

### Linux/KVM evidence hashes

| Evidence | API 29 SHA-256 | API 36 SHA-256 |
|---|---|---|
| Device report | `57b0b6b53eafbc9f2ce1f2496201918d25cb7ac0989e40c908463cf8c592ce6f` | `9e7de9b2bc33fd27cc632d64f8b84a4301fa5a9e9e1bf1dec0c82d8e063721b8` |
| Redacted command log | `22b2393d47dcb128cf650460d9835e8c0e140a4c57f0d8f6ea9ce82030e825e5` | `5dc48592d3cf4e58962e80741f42a7793e325524807911026cee37424ed4be82` |
| Device JUnit XML | `57a59f6f1d52d1cd2137183280a5f9863ba24224c0a816a0e83a5093ecc393ff` | `68e4189677f58cb48a48f022feeb29bbd0841f7c9ef0c648165b3cf8dcbe59bf` |
| Extracted startup-negative report / JUnit | `d19f6a16080bc12566ed1f8688cfa227166cb7dd947290f984f0be9e3408ef72` / `d2443194315e7c63f2795557659d8da75694feb761b6c0a5c632d69b85b22768` | `9acedeef9bef26c5810365f1b9f693aa38c9b3d02a956c41fcda541884687d8d` / `d2443194315e7c63f2795557659d8da75694feb761b6c0a5c632d69b85b22768` |
| Direct startup-negative report / JUnit | `d18548010a26b6d4c60a84210014fbad343b0c4b0b389be7ab6150e390db2684` / `06eb6192ee566668b894a3d643f7b1b4dda87b6de079199cd5302c1a0eb9f7ce` | `0fab35582dee0e0e92dd336e63092445d0d7819f88fafd85567a2521e7d8643a` / `06eb6192ee566668b894a3d643f7b1b4dda87b6de079199cd5302c1a0eb9f7ce` |
| Static report | `f0ad761f590cff0fc7ee33aeb466450d23e97e6833b72c29c4edb784b379538f` | `f369a0c88567d3de7bc20cf4a76ad725ff15613dd01249dd4d2d7f3539b3868c` |
| Static verifier peak memory | `71,348 KB` | `73,516 KB` |

| Generated artifact | API 29 SHA-256 | API 36 SHA-256 |
|---|---|---|
| extracted Release/R8 APK | `9284d96357502d7025bb78af9b3415b12ec5349b20360a91ede3bce715f109b4` | `190cf670c9c985166addf6716e67b1ebb41369403425076761de1c014a625a3b` |
| direct Release/R8 APK | `dee4678dcf85d866b289fadf3052f34274333bdfd26547950be061cdae770dbe` | `a484a79dcd8d723652ccfa43eb7cbcc22c0856c1c725d2620725fb3f601e4602` |
| extracted instrumentation APK | `f91d1c9f58b03a66a370e69eb623d4ed08a4e68ae736c9157d2e1a676e4ddc0a` | `d2aa38aa795fb8742232f4bfc5680d17a7b688036229d3aa76e4e497f2d4b78c` |
| direct instrumentation APK | `7494accfbd9a075d971c531acd560598209549017afba13b6b6c1c5a1398f0c6` | `02d90189ceee12b3004a7aa9e1696c12525adf6b197de0732f711187181bac9f` |
| ConfigV2 | `c9bded1f5fb51e379fc804d33bbbfd0014a9947111564cfbfed6f2b2e93f058a` | `f09c7b881d5068380c14bc523da4b52a4d61811e9826ed9bab067077f3106928` |
| AHDC payload | `bbfd4c5ce0434793d47a4f2e6ff01ec7a40fdeb1e7738ea017133e7a7fadd879` | `bbfd4c5ce0434793d47a4f2e6ff01ec7a40fdeb1e7738ea017133e7a7fadd879` |
| payload DEX 1 / DEX 2 | `c54ce0f5a67a8e32e2644936b1682dd8e02c33da91d602150949d354a485804e` / `06200c4901642a01d3d7e2ab5a3e23e9b863f015d528731eb29823b7c858f819` | `c54ce0f5a67a8e32e2644936b1682dd8e02c33da91d602150949d354a485804e` / `06200c4901642a01d3d7e2ab5a3e23e9b863f015d528731eb29823b7c858f819` |
| R8 mapping | `70d9652c8707b6fc9c42e440015528b6238a4dd3a0f92c1f2eec55bce8eb96dc` | `70d9652c8707b6fc9c42e440015528b6238a4dd3a0f92c1f2eec55bce8eb96dc` |
| R8 usage | `f2953518d3553090c98971614a5ddedab2c9648ade9305b688fb9951383176f2` | `f2953518d3553090c98971614a5ddedab2c9648ade9305b688fb9951383176f2` |
| `libfixture_jni.so` arm64-v8a | `9e6d57ef9b23c55a897939852463a2a6c26c84da6277e75aca2954ee5ab64c06` | `9e6d57ef9b23c55a897939852463a2a6c26c84da6277e75aca2954ee5ab64c06` |
| `libfixture_jni.so` x86_64 | `9ab8c614757cc94c115e13b93a87afd8a02141e6237f9c3e4bc65321c4b020b6` | `9ab8c614757cc94c115e13b93a87afd8a02141e6237f9c3e4bc65321c4b020b6` |

For both KVM jobs, the M0-04 baseline/root-DEX values are `145,488 / 147,156` bytes and the explicitly non-verifier-only delta is `1,668` bytes. The generated APKs are ignored, run-scoped integration artifacts signed only with a one-time non-production fixture identity. They are not product outputs and are not committed.

## Completion gate

M0-05 is not complete at this snapshot. All three review-3 environments passed, including authenticated duplicate-ABI rejection, independent extracted/direct 18-case negative matrices, JUnit XML, two payload DEX hashes, ConfigV2/AHDC hashes, per-ABI SO hashes, R8 mapping/usage hashes, verifier peak memory, the correctly scoped M0-04 baseline delta, cold-start metrics and cleanup. That evidence is frozen locally at `350d08ee5f3c83bf60dcbd4564866ffb5f819844`, while the remote branch remains at KVM validation commit `e54d3d2a06b11375cb08f09ebaedb51d6623920f`. The only remaining review gate is a new independent read-only review of the reconciled documentation and already-frozen evidence with zero open P0/P1/P2 findings. No PR is created before that review passes, and M1/M2 remain blocked.
