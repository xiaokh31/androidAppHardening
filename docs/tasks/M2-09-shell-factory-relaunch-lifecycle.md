---
id: M2-09
title: "Shell Factory configuration-relaunch lifecycle"
milestone: M2
status: planned
owner_role: runtime-security-agent
depends_on:
  - M2-01
required_skills:
  - plan-apk-hardening-change
  - implement-runtime-protection
  - validate-protected-apk
security_sensitive: true
---

## Goal

Preserve the authenticated process-wide Runtime bootstrap result when Android creates a later Shell `AppComponentFactory` instance for an API 29 configuration relaunch, without reopening the Guard or weakening loader identity checks.

## Background

M3-04 API 29 x86_64 runs `31858315765` and `31859364008` completed the first custom-Factory startup sequence and then failed a configuration relaunch with `AAH-RUNTIME-BOOT-COMPONENT`. M2-01 stores the terminal bootstrap result in a process-wide coordinator, but each Shell Factory instance keeps a separate `installed` field. A later Framework-created Shell instance therefore cannot see the already authenticated `READY` result.

## Inputs

- M2-01 Shell Factory and process-wide bootstrap state machine.
- ADR 0003 and ADR 0007 startup, ownership and public-API contracts.
- The retained M3-04 API 29 failure evidence and exact component-event contract.

## Expected Outputs

- ADR 0013 defining safe attachment of later Shell Factory instances to an existing process-wide terminal result.
- A bounded Runtime bootstrap fix with JVM and Android regression coverage.
- Exact-head review, dual-platform and API 29/36 KVM evidence that unblocks M3-04.

## In Scope

- Later Shell Factory instances in the same process and ClassLoader identity domain.
- Reuse of an existing `READY` result without a second Guard open, Factory construction or ClassLoader hook.
- Stable rejection when no terminal result exists, the process is `FAILED`, or the Framework loader differs from the final loader.
- Custom-Factory configuration relaunch and startup-Provider regression coverage.

## Out of Scope

- Container, signer, ABI, Host processing or M3-04 matrix semantics.
- Reopening or replacing an authenticated session after `READY`.
- Cross-process sharing; each Android process retains its own coordinator.
- ARM installation, local emulators, benchmarks and M3-05.

## Implementation Decisions

- Keep one process-wide `HardeningBootstrap.Coordinator` and its immutable terminal `BootstrapResult` as the sole owner of the authenticated session, provisional/final loaders and original Factory.
- Add a synchronized read-only lookup that returns the terminal result only when the coordinator is `READY`; it never invokes the session opener and never returns a failed or partially initialized result.
- `ShellAppComponentFactory.requireReady` may attach its instance-local cache to that existing `READY` result only after the Framework loader is non-null and identical to the frozen final loader.
- The first `instantiateClassLoader` path remains the only path that opens the Guard and invokes the original Factory ClassLoader hook.
- Any missing result, `FAILED` state, loader mismatch or partial state remains `AAH-RUNTIME-BOOT-COMPONENT`; no fallback or retry is added.

## Public Interfaces

No public API changes. `ShellAppComponentFactory` keeps its six API 29 public callbacks; the new coordinator lookup is package-private and returns the existing internal `BootstrapResult` only.

## Security Constraints

- A later Shell instance cannot supply, replace or reconstruct session, Factory or loader state.
- Loader identity equality is checked before caching or delegating a component callback.
- `FAILED` and `INSTALLING` states never expose a result and never reopen the Guard.
- No hidden API, Framework reflection, disk DEX output or sensitive logging is introduced.

## Compatibility Requirements

- Preserve API 29 minimum and the public `AppComponentFactory` lifecycle.
- Preserve custom and absent original Factory behavior, startup Provider order and all five component delegation methods.
- Preserve one Guard open and one original Factory ClassLoader hook per process while allowing multiple Shell wrapper instances.

## Acceptance Criteria

- A second Shell Factory instance using the same `READY` coordinator instantiates a component with the frozen final loader and original Factory.
- Guard open, original Factory construction and ClassLoader hook counts remain exactly `1`; the READY session remains open.
- New Shell instances reject loader mismatch, `NEW`, `INSTALLING` and `FAILED` states without opening a session or falling back.
- API 29 custom-Factory configuration relaunch completes the exact expected Provider/Application/Activity event sequence; API 36 remains green.
- Targeted JVM/Android tests, governance, strict HandOff, independent read-only security review, Ubuntu/Windows Build/Governance and one bounded API 29/36 KVM run pass.

## Required Tests

- JVM state-machine tests for second-instance attach, loader mismatch, missing READY result and failed-state non-retry.
- Android connected contract with two Shell wrapper instances sharing one coordinator and exact install/hook/component/close counts.
- Existing M2-01 six-entry, no-Factory, failure ownership and multi-process regressions.
- M3-04 real API 29 configuration relaunch regression through the production Shell and Release/R8 fixture.

## Required Evidence

- Issue #59, branch, frozen SHA, commands, exit codes, OS/toolchain, timestamps and report hashes.
- Exact count evidence for Guard open, Factory construct/hook, component delegation and session close.
- Independent review with P0/P1/P2 all zero.
- Exact-head Ubuntu/Windows Build/Governance and API 29/36 KVM results with bounded cleanup.

## Likely Files

- `runtime/bootstrap/src/main/java/ah/runtime/bootstrap/HardeningBootstrap.java`
- `runtime/bootstrap/src/main/java/ah/runtime/bootstrap/ShellAppComponentFactory.java`
- `runtime/bootstrap/src/test/java/ah/runtime/bootstrap/BootstrapSelfTest.java`
- `runtime/bootstrap/src/androidTest/java/ah/runtime/bootstrap/BootstrapConnectedRunner.java`
- `tools/validation/verify-m2-01-bootstrap.mjs`
- `docs/evidence/M2-09/`

## Dependencies and Blockers

Any second Guard open, loader-identity relaxation, session ownership regression, open review finding, API 29 relaunch failure or exact-head CI mismatch blocks merge. M3-04 PR #58 remains paused until M2-09 merges and post-merge gates pass.

## Agent Handoff Requirements

Use Issue #59 and branch `fix/m2-09-component-relaunch-lifecycle` with one PR. The handoff must preserve both retained M3-04 failures, enumerate exact lifecycle counts, identify inherited versus rerun evidence, and keep M3-04/M3-05 changes out of this PR.
