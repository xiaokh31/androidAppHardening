# ADR 0016: End-to-end startup attribution boundary

## Status

Proposed

## Context

M3-05 retained a stable API 36 protected-startup budget failure: the protected-minus-baseline P50 delta reached 331 ms for process start to `Application.onCreate` and 432 ms for process start to the first interactive window. The later M2-10 first-and-only diagnostic was valid and completed, but its `t0..t6` interval began immediately before signer measurement and ended after Runtime bootstrap/factory completion. No individual inner stage contributed the required 30 ms in both fixed sample partitions, so that diagnostic correctly selected no stage.

The two results answer different questions. M3-05 measures an end-to-end platform/application interval. M2-10 measures only a nested Runtime interval. Time before the Shell factory, between the final loader return and Application construction, in Application/Provider/Activity lifecycle dispatch, and between UI traversal and interactivity was not part of `t0..t6`. It is therefore invalid to attribute the remaining end-to-end delta to Runtime, to rerun M2-10 until a stage becomes eligible, or to weaken an existing release budget.

This ADR replaces the unmerged M2-10 attribution proposal in PR #67. It does not invalidate run `32099991400`, authorize another M2-10 run, or treat that result as a failed attempt. The retained result is conclusive only for its documented inner boundary.

## Decision

Startup attribution uses one outer `p0..p15` sequence on the real Release/R8 cold-start path and one protected-only inner sequence. Every timestamp uses Android `CLOCK_BOOTTIME` represented in nanoseconds. A later implementation may use profile-only bytecode probes plus Perfetto/Android trace events, but the observer, its manifest control and all keep rules must be absent from Release AARs, production fixture APKs, the CLI and distribution artifacts.

The common outer sequence is exactly:

1. `p0`: process start from the platform process-start event.
2. `p1`: benchmark Application constructor entry.
3. `p2`: benchmark Application constructor exit.
4. `p3`: `Application.attachBaseContext` entry.
5. `p4`: `Application.attachBaseContext` exit.
6. `p5`: fixture `ContentProvider.onCreate` entry.
7. `p6`: fixture `ContentProvider.onCreate` exit.
8. `p7`: benchmark `Application.onCreate` entry.
9. `p8`: benchmark `Application.onCreate` exit and the M3-05 application endpoint.
10. `p9`: benchmark Activity constructor entry.
11. `p10`: benchmark Activity constructor exit.
12. `p11`: benchmark Activity `onCreate` entry.
13. `p12`: benchmark Activity `onCreate` exit.
14. `p13`: benchmark Activity `onResume` entry.
15. `p14`: benchmark Activity `onResume` exit.
16. `p15`: first focused/interactive marker and the M3-05 interactive endpoint.

Each outer stage is exactly one adjacent difference `p(n+1)-p(n)`. All fifteen differences are retained, even when their value is zero. Their sum must equal `p15-p0` exactly after canonical nanosecond conversion; omission, overlap, duplication, reordered timestamps or an unexplained residual fails closed. `p8-p0` must reconcile to the process-to-Application interval and `p15-p0` to the process-to-interactive interval used by M3-05. First-frame/fully-drawn trace slices may be retained as auxiliary evidence inside `p14..p15`, but they are not inserted into the canonical chain because frame presentation and focus callbacks do not have a portable total order. The existing 300/500 ms P50/P95 budgets and the 10% repeatability limit remain unchanged.

For the protected APK, `p0..p1` is additionally decomposed by this exact nested sequence:

1. `h0`: production `ShellAppComponentFactory.instantiateClassLoader` entry.
2. `h1..h7`: the existing M2-10 `t0..t6` timestamps, in the same order and with the same six stage meanings.
3. `h8`: production `instantiateClassLoader` return after the final loader is published.

The ordered chain `p0,h0,h1,h2,h3,h4,h5,h6,h7,h8,p1` must be monotonic. Its ten adjacent differences must sum exactly to `p1-p0`. The contiguous Runtime bootstrap owner is `h0..h8`; `p0..h0` is `platform_pre_shell`, and `h8..p1` is `platform_post_loader`. Neither platform segment may be relabelled as Runtime. The baseline has no synthetic Shell or no-op factory inserted into its manifest; its common `p0..p1` interval remains the comparison boundary.

The future diagnostic uses only the retained failing `kotlin-multidex` fixture and exactly two order-reversed campaigns in one API 36 x86_64 job, one emulator boot and one exact head:

- Campaign `A`: baseline then protected.
- Campaign `B`: protected then baseline.
- Each mode has five warmups followed by exactly fifteen retained cold starts.
- Nearest-rank P50 for fifteen retained values is the eighth value after ascending sort.
- No sample may be dropped, replaced, duplicated or reordered. A timeout, missing marker, trace loss, non-monotonic sequence or failed reconciliation invalidates the only diagnostic and blocks the task.

Attribution is based on protected-minus-baseline deltas of matching common stages. The protected-only `h0..h8` Runtime span is an incremental candidate because the baseline contains no product Runtime. A stage or owner is eligible only when, in both campaigns, its P50 contribution is at least 30 ms, its sign is positive, its cross-campaign variation is at most 10%, and it accounts for at least half of the positive `p8-p0` P50 delta. If no owner satisfies all conditions, the result is `UNATTRIBUTED`; it does not authorize production optimization. `platform_pre_shell`, `platform_post_loader`, lifecycle, traversal and rendering owners require a separately scoped platform, artifact, fixture or benchmark task rather than a Runtime change.

The future diagnostic is first-and-only for its exact task. Its report binds workflow path, head SHA, run ID, job ID, run attempt, boot ID hash prefix, campaign/mode/sample ordinals, trace hash, raw-sample hash, APK hashes and cleanup result. An invalid or ineligible result cannot be replaced by a later run on identical product bytes. This rule is separate from and does not reopen the completed M2-10 diagnostic.

M3-09 is governance-only. It may update this ADR, task/dependency documents, HandOff/README and governance validation, but it may not implement probes, workflows, Runtime changes or benchmark orchestration and may not run KVM, emulator, ARM or M3-05.

## Consequences

- M2-10 remains valid evidence that no measured inner stage met its eligibility threshold; it is not retried.
- End-to-end startup cost is no longer assumed to be Runtime cost. Common lifecycle, platform residual and protected Runtime spans are named separately and reconcile to the same endpoints.
- A later implementation task may run one bounded API 36 diagnostic for the failing fixture. Its result chooses the owner of any subsequent optimization task.
- M3-05 remains blocked. M3-09 alone does not authorize resuming PR #63, running ARM, or changing production code.
- If the only dominant contribution is unowned/platform residual, the project records `UNATTRIBUTED` and plans a narrower diagnostic instead of guessing.

## Rejected Alternatives

- Rerun M2-10 with the same boundary: violates first-and-only evidence and cannot observe the missing interval.
- Aggregate all process-to-Application time as Runtime: attributes platform and fixture work to code that did not execute there.
- Insert a no-op baseline `AppComponentFactory`: changes the baseline path and creates false stage symmetry.
- Use wall clock or subtract timestamps from different clocks/processes: cannot produce an adjacent, reconcilable timeline.
- Lower the 30 ms eligibility threshold, raise release budgets or relax the 10% limit: selects a cause by changing the decision rule.
- Run all fixtures, ARM or the full M3-05 matrix during contract work: adds cost without deciding the attribution model.
- Add a Release timing API, environment switch, intent extra, manifest flag or public/package-private hook: creates a distributable or attacker-influenced diagnostic surface.

## Security Impact

Signer verification, authenticated container parsing, metadata binding, Guard ordering, memory controls, read-only and `DONTDUMP` mappings, cleanup and four-ABI behavior remain unchanged. Attribution probes are test/profile-only and must be proven absent from product artifacts. No finding in performance evidence may authorize skipping, caching across trust epochs, deferring or weakening a security control.

## Compatibility Impact

No wire format, minSdk, API, ABI, supported application type, fixture behavior or public interface changes. The future diagnostic is limited to the fixed API 36 revision 2 x86_64 reference image and the existing Release/R8 `kotlin-multidex` fixture. It makes no new compatibility claim and cannot replace API 29/36 Runtime acceptance or ARM evidence.

## Verification

- Governance verifies that ADR 0016, M3-09, M3-05, TEST_STRATEGY, ROADMAP and INDEX agree on the new dependency and scope.
- A dedicated validator locks the exact outer and inner sequences, adjacent-stage reconciliation, fixed campaign/sample/P50 rules, ownership labels, 30 ms/10%/50% eligibility, first-and-only identity and zero-production-diff boundary.
- Mutation tests reject missing/reordered/duplicate timestamps, gaps, overlaps, cross-clock subtraction, synthetic baseline Factory, report replacement, changed thresholds, sample replacement and product timing hooks.
- M3-09 requires independent read-only review with P0=0/P1=0/P2=0 and exact-head Ubuntu/Windows Build/Governance before merge; no device or benchmark evidence is accepted for this governance task.
