# ADR 0010: Versioned local environment risk policy

## Status

Accepted

## Context

M2-05 must combine debugger, process mapping and emulator indicators without turning heuristic observations into integrity failures. The authenticated ConfigV2 already fixes `risk_policy_version=1`; no wire-format change is required.

## Decision

Policy version 1 uses five public signal IDs: `TRACER`, `JDWP`, `DEBUGGABLE`, `INSTRUMENTATION_MAPPING` and `EMULATOR_COMPOSITE`. Scores are fixed at 60, 50, 20, 40 per distinct mapping family capped at 80, and 10 per independent emulator characteristic capped at 30. The total is capped at 100. `LOW` is 0–39, `MEDIUM` 40–79 and `HIGH` 80–100. Actions are derived, never supplied: `LOW -> ALLOW`; `MEDIUM|HIGH -> DEGRADE`. No environment path returns `DENY` or changes signer, AEAD or authenticated-integrity handling.

Native collection reads only bounded current-process `/proc/self/status` and `/proc/self/maps`. Its internal mapping families are fixed to dynamic instrumentation (`frida`/`gadget`) and runtime hooks (`xposed`/`lsposed`/`substrate`/`zygisk`/`riru`); aliases within one family deduplicate before scoring. Java collection uses public `Debug`, `Build` and Framework-provided `ApplicationInfo`. ABI is not an input to scoring. Malformed, unavailable, permission-denied or over-budget signals become `UNAVAILABLE` with score zero. Reports expose only version, signal ID/state/score, total, level and action; raw proc text, mapping names, paths and device identifiers are discarded.

The collection deadline is 50 ms measured with `SystemClock.elapsedRealtimeNanos()`. A collector result that arrives after the deadline is discarded as unavailable; the scoring function itself is deterministic, order-independent and free of I/O.

## Consequences

- M2-06 may consume only the immutable `RiskReportV1` level/action pair and must reject inconsistent pairs.
- Root, injected or process-controlling attackers may hide or forge every heuristic signal. This policy raises analysis cost and provides auditable inputs; it is not an integrity proof.
- Policy changes require a new authenticated policy version and ADR update. Manifest metadata and runtime flags cannot override weights or thresholds.

## Rejected Alternatives

- ABI, root or emulator as a standalone blocking signal: unacceptable false-positive and compatibility impact.
- Manifest-configurable weights: unauthenticated policy override and inconsistent installs.
- Cross-application process/package scans: requires broader visibility and sensitive collection outside v0.1.
- Environment `DENY`: heuristic evidence cannot replace cryptographic integrity.

## Security Impact

Bounded, normalized current-process signals reduce log and parser exposure and provide M2-06 with auditable cost inputs. A process-controlling attacker can still hide mappings, debugger state or emulator characteristics; the engine therefore never weakens or replaces signer, AEAD or authenticated-integrity checks.

## Compatibility Impact

The policy uses API 29 public APIs and current-process proc files only. Missing or denied proc access becomes `UNAVAILABLE`/zero. ABI is not scored, and normal x86/x86_64 devices remain supported. No ConfigV2 or AHDC wire bytes change because the authenticated policy version was already fixed to 1.

## Verification

- Table-driven JVM tests cover every boundary, cap, deduplication, permutation, unavailable state and action mapping.
- Native parser tests cover truncation, malformed/overlong status and maps data, family deduplication and bounded reads.
- API 29 and highest-supported API connected tests exercise the real collector, verify ABI-neutral behavior, report redaction and the 50 ms budget.
