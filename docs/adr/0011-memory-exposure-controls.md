# ADR 0011: Bounded in-process memory exposure controls

## Status

Accepted

## Context

M2-06 adds best-effort memory exposure controls without changing the authenticated container, signer policy, component order or the lifetime required by ART. M2-02 destroys CEK and derived keys before returning `LoadedPayload`, while M2-05 risk is consumed only after the Guard has returned that owned payload. A risk-selected profile therefore cannot retroactively protect key pages used during the first authenticated open.

## Decision

All Native key material and authenticated temporary plaintext use move-only `SecureBuffer` owners. Their pages always make a bounded best-effort lock attempt while the sensitive owner is alive, independent of the later risk profile, and are explicitly zeroized before unlock/release. This invariant is charged to the same process-wide 1 MiB lock budget. Failure to lock is observable in Native test diagnostics but never changes signer, AEAD or container handling and never causes plaintext fallback.

Every completed payload mapping is sealed read-only and receives `MADV_DONTDUMP` before its handle can be published. `BASELINE` reports those invariant controls. `ELEVATED` additionally locks at most the first and last 64 KiB of each retained DEX mapping, without overlapping a short mapping twice and without exceeding the shared 1 MiB process budget. `HIGH` includes `ELEVATED`, applies `PR_SET_DUMPABLE=0` to the current application process, and adds one cryptographically random 20–50 ms bounded delay. Unsupported or resource-limited `madvise`, `mlock` and `prctl` calls are reported as capabilities; they do not terminate loading.

`PayloadRuntime.applyMemoryProfile(LoadedPayload, MemoryProfile)` is the only Java-to-Native profile entry. It verifies that the `LoadedPayload` is open and uses its private primitive handle without widening the frozen M2-02 public ownership surface. M2-06 policy maps `ALLOW/LOW` to `BASELINE`, `DEGRADE/MEDIUM` to `ELEVATED`, and `DEGRADE/HIGH` to `HIGH`; any inconsistent action/level pair fails with a stable `AAH-RUNTIME-MEMORY-` code. ABI is not a policy input.

## Consequences

- Key pages receive the strongest feasible lifetime protection even though the risk report is not available during the first decrypt; retained DEX edge locking remains risk-selected.
- DEX mappings remain readable until `LoadedPayload.close()` because ART may still reference them. Closing zeroizes, unlocks and unmaps exactly once.
- `dontDump`, locked-byte count and current-process dumpability are capability observations, not absolute security claims.
- A process-controlling, root or kernel attacker can still inspect memory, bypass heuristics or capture ART copies.

## Rejected Alternatives

- Pass a risk profile into the authenticated open: this would create a second policy-to-Native entry and widen the frozen M2-02/Guard contract.
- Delay risk collection until after decrypt but claim risk-selected key locking: that claim is temporally impossible because the keys are already destroyed.
- Treat unavailable OS controls as an integrity failure: this would turn cost controls into an unsupported compatibility deny path.
- Erase retained DEX immediately: ART can still depend on the backing direct buffers.

## Security Impact

The controls shorten sensitive temporary lifetimes, exclude supported mappings from ordinary core dumps, limit locked memory and reduce default process dumpability at high risk. They raise collection cost but do not prevent a sufficiently privileged attacker from extracting plaintext.

## Compatibility Impact

Only API 29 public NDK/Linux primitives are used. Unsupported or denied controls degrade to accurately reported capability bits. No network, hidden API, system setting, extra permission, persistent process or disk plaintext is introduced.

## Verification

- Native unit tests cover `SecureBuffer` move/release/exception cleanup, zeroization, mapping sealing, `DONTDUMP`, lock overlap/cap accounting, unavailable capabilities and close cleanup.
- JVM tests cover the immutable report and exact risk-to-profile table, including inconsistent pairs and ABI-neutral behavior.
- Bounded API 29 and API 36 KVM acceptance covers extracted/direct Release/R8 startup, `/proc/self/smaps`, delay bounds, current-process dumpability and cleanup.
