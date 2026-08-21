# M3-10 independent read-only review 1

- Timestamp: `2026-08-21T11:19:33+08:00`
- Reviewer role: independent security reviewer
- Base: `9d3fc3a4ae17d14f84d223b9dbb5f92016814f1a`
- Implementation: `2eb462b1d3e410d20e684ec70be84aab3cd09562`
- Evidence successor: `2ae3e19e319e95e0cde474f4600408737fa67513`
- Reviewed coordination head: `6db7cc34d27b3cbf02b3e9c1d2b0457defd19b45`
- Result: `FAIL — P0=0/P1=5/P2=0`

The review was strictly read-only. It did not modify files, use the network, run Gradle, create a workflow, start KVM/emulator/device/benchmark work or resume M3-05. The canonical API 36 diagnostic eligibility remains unconsumed.

## Findings

1. **P1 — observer and profile identity are not immutably bound.** The deriver and verifier both accept the caller-provided observer DEX, while the verifier skips the observer implementation itself. No pre-run lock fixes the observer DEX, derivation manifest, signed profile hashes and profile signer commitment. A same-descriptor observer with changed behavior can therefore be trusted by both sides.
2. **P1 — DEX equivalence is not independently computed.** The verifier calls the same `M310DexProfileTool.derive` implementation as the deriver. Its extra scan checks observer method names/order but does not independently compare every non-probe instruction, handler/debug/access surface or exact insertion adjacency against the canonical DEX.
3. **P1 — signing/alignment/cleanup orchestration is not frozen.** The reviewed tree contains unsigned derivation and four-APK verification tasks, but not the fixed ephemeral signer generation, Build Tools `36.1.0` alignment/v3 signing, duplicate-output comparison and `finally` cleanup path used by local evidence. The evidence records command classes rather than a replayable redacted invocation.
4. **P1 — Release zero-pollution verification skips binaries.** `verify-m3-10-profile-freeze.mjs` skips files containing NUL and therefore does not inspect actual AAR/JAR/APK/DEX/ELF/ZIP outputs. Its six mutations are text-only and cannot prove observer/probe/keep surfaces are absent from Release, CLI, fixture and distribution artifacts.
5. **P1 — the complete pre-workflow fail-closed verifier is absent.** The task's public `verify-m3-10-startup-attribution.mjs` and runner do not exist. The current freeze cannot yet validate full timelines, `5+15` samples, calibration, nine-owner arithmetic, lifecycle/security events, immutable package/cleanup, GitHub terminal history or the required named mutations.

## Confirmed evidence

- Frozen ancestry and clean tree were correct.
- Both ignored canonical APK hashes/sizes matched the M3-11 lock.
- Existing A/B unsigned, aligned, signed and derivation-manifest outputs were byte-identical.
- Existing four-APK report SHA-256 was `0afc3caf8ca2fc23e6892a172387d234e6390cf6d9d21e81108258b942385aaa`.
- No temporary keystore, private key, password or seed remained.
- dexlib2 `2.5.2` had a dependency lock, verification metadata and license notice.
- Both canonical workflows remained absent.
- Node syntax, current six-mutation surface test, global governance, strict HandOff and diff checks exited `0`; these positive checks do not close the five findings.

## Required next action

Keep API 36, KVM, ARM, benchmark, workflows and M3-05 prohibited. Complete one bounded M3-10 review-remediation covering all five findings, freeze a new exact SHA, then run a new independent read-only review. Only an exact conclusion `P0=0/P1=0/P2=0` authorizes the workflow successor.
