# ADR 0013: Share the terminal bootstrap result across Shell Factory instances

## Status

Accepted

## Context

Android may construct another application `AppComponentFactory` wrapper while keeping the same process and application ClassLoader alive during a configuration relaunch. The M2-01 coordinator already owns one process-wide authenticated `READY` result, but the public Shell wrapper also keeps an instance-local cache. API 29 M3-04 evidence showed that the first startup completed correctly and a later Shell instance then rejected component creation because its local cache was empty.

Reopening `RuntimeStartupGuard` from a component callback is impossible without `ApplicationInfo` and would violate the one-time authentication and ownership contract. Making component callbacks accept an arbitrary loader would weaken the final-loader boundary.

## Decision

1. The process-wide `HardeningBootstrap.Coordinator` remains the sole owner of the terminal `BootstrapResult`.
2. The coordinator exposes a package-private synchronized read-only lookup that returns the terminal result only when state is exactly `READY` and the result is non-null and `READY`.
3. A Shell Factory instance whose local cache is empty may read that existing result during a component callback. It must first require a non-null Framework loader identical by reference to the frozen final loader, then cache the same immutable result locally.
4. This attachment path never invokes `install`, the Guard opener, original Factory construction or the original Factory ClassLoader hook.
5. `NEW`, `INSTALLING`, `FAILED`, null result and loader mismatch all fail with the existing stable component error. They do not retry, reopen, fall back or mutate coordinator state.
6. The authenticated session, provisional/final loaders and original Factory remain strongly referenced by the single terminal result for the process lifetime. Later Shell wrappers do not own or close them.
7. Cross-process sharing remains forbidden; each process has its own static coordinator and authenticated session.

## Consequences

Positive:

- API 29 configuration relaunches can create later components through a new Shell wrapper without repeating authentication.
- One Guard open, one original Factory construction and one original Factory ClassLoader hook remain enforceable per process.
- Loader identity and fail-closed behavior remain unchanged.

Trade-offs:

- The Runtime explicitly supports multiple Framework wrapper instances per process.
- The coordinator gains one internal read-only accessor that must remain non-public and state-checked.

## Rejected Alternatives

- Reopen the Guard from component callbacks: no authenticated startup arguments are available and ownership would fork.
- Store only a static Shell instance: Android owns Factory construction and no public API guarantees reuse of the wrapper object.
- Accept any ClassLoader after process bootstrap: this would permit component resolution outside the authenticated final-loader identity.
- Use hidden Framework fields to recover the first Factory: violates the API 29 public boundary.

## Security Impact

The change exposes no new public capability and does not accept new security inputs. Attachment is possible only to an already committed immutable result and only with exact final-loader reference equality. Failed or partial initialization remains inaccessible, and the Guard cannot be reopened through this path.

## Compatibility Impact

The public API 29 callback surface, minimum SDK, original Factory semantics and component names remain unchanged. The change adds compatibility for Framework behavior that reconstructs the Shell wrapper during a same-process configuration relaunch. Cross-process behavior remains one independent bootstrap per process.

## Verification

- JVM and Android tests create two Shell wrappers over one coordinator and prove exactly one session open, Factory construction and ClassLoader hook while both wrappers delegate with the same final loader.
- Missing READY, failed state and loader mismatch tests retain `AAH-RUNTIME-BOOT-COMPONENT` without a retry.
- Existing no-Factory, five component types, startup Provider, failure cleanup and secondary-process tests remain green.
- A bounded API 29/36 KVM run exercises the real production Shell in Release/R8; API 29 must complete the configuration relaunch that failed in retained M3-04 evidence.
- Static validation continues to reject hidden API, Runtime low-level imports and public-surface expansion.
