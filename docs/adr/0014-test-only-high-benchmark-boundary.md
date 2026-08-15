# ADR 0014: Test-only HIGH benchmark boundary

## Status

Accepted

## Context

M3-05 must disclose the extra cost of the M2-06 `HIGH` memory profile while keeping its Android release gates representative of an ordinary application cold start. On the fixed non-root reference devices, a normal Release/R8 process is expected to remain `LOW`; deterministically forcing `HIGH` before `RuntimeStartupGuard` would otherwise require a product risk override, a manifest or filesystem control, a debuggable process, injected instrumentation, root, or a production test hook. Each option either changes the measured startup path, weakens the frozen M2-05 policy boundary, or adds a control an attacker could influence.

The production entry points are already sufficient to measure the bounded cost without adding a new interface: `RuntimeStartupGuard.openVerifiedPayload(...)` returns an owned authenticated session, and `PayloadRuntime.applyMemoryProfile(..., MemoryProfile.HIGH)` is the frozen monotonic profile entry from ADR 0011. The missing decision is how a benchmark fixture may reach the owned payload while keeping that reachability out of product artifacts and out of cold-start claims.

## Decision

M3-05 uses two explicitly different measurement modes.

1. `observed_cold_start` measures the unmodified Release/R8 application startup. The production risk engine, Guard, signer checks, authenticated container, ABI selection and memory controls run exactly as shipped. After the startup timer has stopped, the benchmark records a separate public risk observation as `observedRiskLevel`, `observedRiskAction` and `riskObservationTiming=post_start`; those fields describe the observed process environment and do not claim access to the Guard's private earlier report or an injected/forced result. The fixed API 29 ARM64 and API 36 x86_64 release gates require this observation to be `LOW`; any other level is an environment mismatch, not a comparable LOW sample.
2. `isolated_high_upgrade` measures only the incremental HIGH-profile operation. In a fresh force-stopped process for every sample, a fixture-only Android test bridge opens a fresh payload through `RuntimeStartupGuard.openVerifiedPayload(...)`, obtains that session's already-owned `LoadedPayload` through fixture-only reflection with fixture-only R8 keep rules, and immediately invokes the existing `PayloadRuntime.applyMemoryProfile(..., MemoryProfile.HIGH)` once before any fixture class or resource lookup. It then proves the same handle remains usable, performs one post-upgrade lookup, closes the session exactly once, and proves cleanup. No production source, public method, manifest flag, system property, persistent marker or risk-policy override is added.

The isolated timer surrounds only the existing monotonic HIGH upgrade. Every raw sample records `highProfileIncrementalMs`, the Native-reported jitter, capability observations and cleanup result. Native jitter must remain 20–50 ms and each wall-clock sample must remain bounded by 250 ms; P50/P95 are reported but are not presented as naturally observed HIGH cold-start latency. A real environment that naturally reports HIGH may additionally produce `observed_cold_start` evidence, but such evidence is optional and must not be synthesized.

The fixture-only bridge is permitted only in `benchmarks/android/src/androidTest` or a dedicated M3-05 Android-test source set. Its class names, keep rules and test controls must be absent from Runtime AARs, production fixture APKs, CLI/distribution artifacts and product reports. CI enumerates all Runtime/Host/production-fixture/distribution main and Release surfaces, rejects M3-07 base-to-HEAD production changes, and must fail if any such surface gains a manifest/property/environment/file/BuildConfig/intent/setter M3-05/HIGH override. The same formal report validator used by M3-05 must reject an isolated sample labeled as a cold start, omitted/ill-typed fields, wrong sample counts, inconsistent LOW/action pairs and missing observed-versus-isolated boundaries.

## Consequences

- M3-05 can quantify the complete ordinary LOW cold-start cost and the bounded incremental HIGH memory-control cost without claiming that the fixed devices naturally entered HIGH or that a post-start observation is the Guard's private report.
- The isolated HIGH number cannot be added to LOW and labeled a measured HIGH cold start. A derived estimate may be shown only as informational arithmetic with both components and the limitation stated explicitly; it is never a release gate.
- The existing signer, AEAD, authenticated metadata, ownership, read-only mapping, `DONTDUMP`, lock-budget, dumpability, jitter and cleanup invariants remain active.
- The test bridge relies on fixture-only reflection and keep rules. That is an intentional test artifact constraint, not a new supported Runtime API.

## Rejected Alternatives

- Add a manifest, system-property, intent, file or BuildConfig override to force `RiskLevel.HIGH`: attacker-influenced policy input and a production bypass surface.
- Add a public or package-private production testing setter: expands the frozen Runtime surface and can survive packaging or optimization unexpectedly.
- Attach a debugger, preload an instrumentation mapping, use root, or make the target debuggable: changes the release startup path and violates the M3-05 release-gate contract.
- Treat emulator architecture as HIGH: contradicts ADR 0010, where ABI is not a risk input and emulator characteristics alone are capped below HIGH.
- Omit HIGH evidence or silently call LOW results complete: hides a required cost and makes the benchmark report misleading.

## Security Impact

No security decision or production behavior changes. The bridge can only exercise an already-authenticated, test-owned payload in an Android-test artifact. It does not weaken signer, container or memory controls and cannot be selected by a protected production APK. HIGH remains a bypassable cost defense rather than an integrity guarantee.

## Compatibility Impact

No wire format, minimum SDK, ABI policy, production API or supported application claim changes. The isolated bridge runs on the same API 29 ARM64 and API 36 x86_64 reference profiles as the ordinary M3-05 benchmark, but its results remain a distinct measurement mode.

## Verification

- Governance validation enumerates every production main/Release surface, rejects base-to-HEAD production changes, rejects HIGH benchmark overrides and requires the two fixed Android measurement-mode labels.
- Mutation self-tests create representative temporary production layouts and serialized reports, then use the same production scanner/report CLI path to reject manifest/property/environment/file/BuildConfig/intent overrides, production setters, wrong/missing/null/types, 29/31 samples, inconsistent risk/action pairs and false `isolated_high_upgrade` cold-start labels.
- M3-05 Android tests prove authenticated session ownership, pre-upgrade zero lookup, one monotonic HIGH upgrade, 20–50 ms Native jitter, bounded wall time, post-upgrade lookup, exactly-once close and package cleanup.
- Artifact scans prove the bridge and its keep controls are absent from Runtime AARs and distributable production artifacts.
