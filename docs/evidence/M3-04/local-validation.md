# M3-04 local validation

- Timestamp: `2026-08-15T13:19:36+08:00`
- Branch: `chore/m3-04-api-abi-matrix`
- Base: `e3a676ed2f4864d2b33077e1d00c300cf2a59817`
- Frozen device-behavior implementation: `015e2b375a2fd24fa99c8748671f56ed142b19f9`
- Host: Windows 10.0.19045 x64; Eclipse Temurin `17.0.19+10`; Gradle `9.5.0`; Node.js `24.12.0`
- Device boundary: one authorized non-root API 29 `user` ARM device was used. No local emulator was started.

## Final mandatory cells

| API | Process ABI | Status | Fixtures | Retries | Signer/tag before load | Cleanup | Cell SHA-256 |
| ---: | --- | --- | ---: | ---: | --- | --- | --- |
| 29 | `armeabi-v7a` | `VERIFIED` | 9/9 | 0 | PASS | PASS | `cde6441343ed38c0b11a1aed45179bc22e288c2a8d12ee7ec15be7051eb776ba` |
| 29 | `arm64-v8a` | `VERIFIED` | 9/9 | 0 | PASS | PASS | `9f5c57f2a85a6caa8ae91fec2f38e4c08141cc14e1d768863d8f902114831c48` |
| 29 | `x86_64` | `VERIFIED` | 9/9 | 0 | PASS | PASS | `d1b6247ad632962a2cbbaa0872da4c88c1bba904d806da17c20810fc52a9ed7c` |
| 36 | `x86_64` | `VERIFIED` | 9/9 | 0 | PASS | PASS | `cc5b5d223aa3dfbf339309e7eab6ffc091b46feaacef43b494d9d12c38ca25c0` |

Every verified cell reports Android API/process ABI facts, an anonymized device identity hash, exact fixture events, different-signer and authenticated-tag rejection before payload lookup, `sessionPublished=false`, and package cleanup. The remaining 28 API/ABI combinations are explicitly `UNVERIFIED` with `NO_AUTHORIZED_PROVENANCE_LOCKED_ENVIRONMENT`; they are not compatibility claims.

## Commands and outcomes

| Command | Exit | Result |
| --- | ---: | --- |
| repository-local `run-m3-04-arm-device.ps1` on authorized API 29 ARM device | 1 after both cell outputs | Both ARM64 and ARM32 cells reached `VERIFIED`; the final cleanup loop then called `.Trim()` on an empty `pm path` result. This was an orchestration false negative after all product assertions. |
| nine exact `adb shell pm path <fixture-package>` checks | 0 | `Checked=9`, `Present=0`; device package cleanup independently proved. |
| PowerShell parser on `run-m3-04-arm-device.ps1` | 0 | Null-safe cleanup fix parses successfully; no device rerun was required. |
| deterministic `cell` re-evaluation for both downloaded KVM reports | 0 | Raw exact-run fixture/runtime/signer evidence was rebound to the PR implementation head instead of the GitHub synthetic merge checkout SHA. |
| repository-local offline Gradle `-Pm304EvidenceDir=build/m3-04/combined-cells :integration-tests:runApiAbiMatrix` | 0 | Inventory/status mutation self-tests passed; 32 cells generated with 4 verified and 28 unverified; JSON/Markdown semantic equivalence passed. |
| `git diff --check` | 0 | No whitespace errors at the validation checkpoint. |

## Generated outputs

- `docs/evidence/M3-04/compatibility-matrix.json`: SHA-256 `e8fdf34228802d4bba4899f7523f11904dd6d9ae64d487479d1c9bb373a26439`.
- `docs/generated/COMPATIBILITY_RESULTS.md`: SHA-256 `06a2fdc1a1d047b53efc0488a25782bc7281432fc9e7bbb34e44077c40ee09a1`.
- The four immutable cell JSON files are retained under `docs/evidence/M3-04/cells/`.

## Evidence inheritance boundary

The frozen device-behavior implementation is `015e2b3`. The later evidence-only changes make the ARM cleanup check null-safe, select the pull-request head SHA from the fixed GitHub event payload instead of the synthetic merge SHA in the KVM workflow, archive the already produced cell evidence, and update documentation. They do not change production Runtime, APK transformation, fixture APK contents, event contracts, or device assertions. Therefore the ARM and KVM executions remain attributable to the frozen implementation; no device or KVM rerun is inferred from the evidence-only successor.
