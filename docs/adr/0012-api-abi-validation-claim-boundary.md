# ADR 0012: API/ABI validation-claim boundary

## Status

Accepted

## Context

The Runtime is built for four ABIs and the product accepts inputs with `minSdk >= 29`, but neither fact proves that every Android API and process-ABI combination has been exercised on a real device. M3-04 originally required the full Cartesian product from API 29 through the locked `compileSdk`. The repository currently has pinned API 29 and API 36 x86_64 KVM images plus an authorized API 29 physical device capable of ARM64 and ARM32 processes. Official, pinned API 30-35 images and API 30-36 ARM devices are not available. Build success, another ABI, a runner label, or a simulated report cannot fill those evidence gaps.

Keeping an impossible all-green gate would prevent the project from publishing an honest partial validation result. Silently dropping unavailable cells would instead overstate compatibility. The contract therefore needs an exhaustive inventory with an explicit distinction between implementation capability and release validation evidence.

## Decision

M3-04 always enumerates every integer API from the locked `minSdk` through the locked `compileSdk` and each of `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`. Every cell has exactly one status:

- `VERIFIED`: the required fixture and negative matrix passed in a real process whose API and ABI were reported by Android and matched the requested cell.
- `FAILED`: a suitable device was exercised but any mandatory assertion failed. A `FAILED` cell blocks M3-04 and release.
- `UNVERIFIED`: no suitable, authorized and provenance-locked device environment was available. It requires a stable reason and cannot carry a positive compatibility claim.

Missing cells, duplicate cells, unknown statuses, device facts inferred from runner labels, and `UNVERIFIED` cells presented as success all fail validation. An `UNVERIFIED` cell is not evidence of incompatibility; it means only that this release does not claim the combination as validated.

The minimum M3-04 campaign uses the environments already fixed by project evidence:

- API 29 `armeabi-v7a` and `arm64-v8a` processes on the authorized non-root physical device;
- API 29 and API 36 `x86_64` processes on the pinned Linux/KVM system images.

All other cells remain `UNVERIFIED` unless M3-04 receives a real environment with fixed provenance and executes the same acceptance contract. The matrix may promote a cell only through evidence; it may never infer promotion from the four-ABI build, `minSdk`, `compileSdk`, another API, another ABI, or an emulator's advertised secondary ABI.

Android 29 may perform one configuration relaunch after the canonical fixture startup. A `VERIFIED` cell may record that behavior only when the observed sequence is exactly the catalog sequence followed by the fixture's one predefined Activity-recreation suffix. The report must preserve both the catalog sequence and the normalized expected/observed sequence and set `configurationRelaunch=true`. The allowance is limited to API 29; a second relaunch, a reordered event, a repeated Provider/Application/worker event, or the same suffix on another API remains a failure.

The four-ABI Runtime statement remains a packaging and binary-interface capability. Public release compatibility is the intersection of that capability, the input APK's native ABI constraints, and the exact `VERIFIED` cells in the generated M3-04 matrix.

## Consequences

- M3-04 can complete with an exhaustive matrix that contains honest `UNVERIFIED` cells, provided every available mandatory baseline cell is `VERIFIED` and no cell is `FAILED`.
- API 30-35 and unavailable API 29/36 ABI combinations are not release-blocking solely because the devices are absent, but they receive no positive compatibility claim.
- Adding a device later is an evidence expansion. It requires fixed provenance, real Android-reported facts, the same fixture/negative contract, regenerated JSON/Markdown, and review of the expanded claim.
- M4 documentation must distinguish four-ABI build capability, input compatibility, and exact device validation. It must not summarize the matrix as “API 29-36 supported” unless every referenced combination is `VERIFIED`.

## Rejected Alternatives

- Treat all 32 cells as mandatory pass: impossible with the authorized inventory and encourages non-reproducible or unpinned environments.
- Test only API 29 and API 36 and imply intermediate support: endpoint evidence does not prove API 30-35.
- Use build success or a different process ABI as device evidence: neither exercises Android startup and Native loading for the target cell.
- Remove unavailable cells from output: absence is ambiguous and permits accidental overclaiming.
- Mark unavailable cells as failed: lack of a device is not a product failure and obscures actual regressions.

## Security Impact

The change does not weaken signer, authenticated-container, startup-order, or payload-before-load assertions. Every `VERIFIED` cell still runs the required positive and negative device checks. Explicit `UNVERIFIED` results reduce the risk of unsupported environments being advertised as security-validated.

## Compatibility Impact

No APK, Runtime, wire-format, CLI, SDK, or ABI implementation changes. The decision narrows release language to evidence-backed API/ABI combinations while preserving the product's API 29 minimum-input rule and four-ABI build output.

## Verification

- Governance tests require a complete, unique API-by-ABI grid and reject missing, duplicate, unknown, or contradictory cells.
- Schema and summary tests require device facts and fixture evidence for `VERIFIED`, stable failure evidence for `FAILED`, and a stable reason with no positive claim for `UNVERIFIED`.
- M3-04 executes the bounded mandatory baseline once and records Android-reported API/process ABI, artifact hashes, cleanup, and payload-before-load negatives.
- Fixture evidence rejects arbitrary duplicate lifecycle events; the API 29 configuration-relaunch case is accepted only through the exact per-fixture Activity suffix and is represented explicitly in the generated cell.
- The generated Markdown is compared semantically with the machine-readable matrix so no `UNVERIFIED` cell can be rendered as supported.
